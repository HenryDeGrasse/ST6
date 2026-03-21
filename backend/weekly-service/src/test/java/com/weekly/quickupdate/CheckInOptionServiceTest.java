package com.weekly.quickupdate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.ai.LlmClient;
import com.weekly.plan.domain.CommitCategory;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.usermodel.TeamPatternService;
import com.weekly.usermodel.UserModelService;
import com.weekly.usermodel.UserProfileResponse;
import com.weekly.usermodel.UserUpdatePatternEntity;
import com.weekly.usermodel.UserUpdatePatternService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link CheckInOptionService}.
 */
@SuppressWarnings("unchecked")
class CheckInOptionServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private WeeklyCommitRepository commitRepository;
    private UserUpdatePatternService userUpdatePatternService;
    private TeamPatternService teamPatternService;
    private UserModelService userModelService;
    private LlmClient llmClient;
    private CheckInOptionService service;

    @BeforeEach
    void setUp() {
        commitRepository = mock(WeeklyCommitRepository.class);
        userUpdatePatternService = mock(UserUpdatePatternService.class);
        teamPatternService = mock(TeamPatternService.class);
        userModelService = mock(UserModelService.class);
        llmClient = mock(LlmClient.class);
        service = new CheckInOptionService(
                commitRepository,
                userUpdatePatternService,
                teamPatternService,
                userModelService,
                llmClient,
                new ObjectMapper());

        when(userUpdatePatternService.getTopPatterns(any(), any(), any(), anyInt()))
                .thenReturn(List.of());
        when(teamPatternService.getTopPatterns(any(), any(), anyInt())).thenReturn(List.of());
        when(userModelService.getSnapshot(any(), any())).thenReturn(Optional.empty());
    }

    private WeeklyCommitEntity makeCommit(UUID commitId) {
        WeeklyCommitEntity commit = new WeeklyCommitEntity(
                commitId, ORG_ID, UUID.randomUUID(), "Deploy API gateway");
        commit.setCategory(CommitCategory.DELIVERY);
        commit.populateSnapshot(
                null, null,
                null, null,
                null, "Improve platform reliability");
        return commit;
    }

    private UserProfileResponse makeProfile() {
        return new UserProfileResponse(
                USER_ID.toString(),
                6,
                new UserProfileResponse.PerformanceProfile(
                        0.74,
                        0.82,
                        4.5,
                        0.8,
                        List.of("DELIVERY", "OPERATIONS"),
                        Map.of("DELIVERY", 0.9, "OPERATIONS", 0.7),
                        Map.of("KING", 0.8)
                ),
                new UserProfileResponse.Preferences(
                        "1K-2Q-1R",
                        List.of("Weekly ops review"),
                        2.3,
                        List.of("MONDAY", "WEDNESDAY")
                ),
                new UserProfileResponse.Trends("IMPROVING", "STABLE", "STABLE")
        );
    }

    @Nested
    class GenerateOptions {

        @Test
        void returnsMergedSeededAndAiOptionsOnSuccessfulLlmCall() {
            UUID commitId = UUID.randomUUID();
            WeeklyCommitEntity commit = makeCommit(commitId);

            UserUpdatePatternEntity p1 = new UserUpdatePatternEntity(
                    UUID.randomUUID(), ORG_ID, USER_ID, "DELIVERY", "Deployed to staging");
            UserUpdatePatternEntity p2 = new UserUpdatePatternEntity(
                    UUID.randomUUID(), ORG_ID, USER_ID, "DELIVERY", "Code reviewed and merged");

            when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));
            when(userUpdatePatternService.getTopPatterns(any(), any(), any(), anyInt()))
                    .thenReturn(List.of(p1, p2));
            when(teamPatternService.getTopPatterns(ORG_ID, "DELIVERY", 5))
                    .thenReturn(List.of("Daily rollout status", "Deployed to staging"));
            when(llmClient.complete(any(), any())).thenReturn(
                    "{\"statusOptions\":[\"ON_TRACK\",\"DONE_EARLY\"],"
                            + "\"progressOptions\":"
                            + "[{\"text\":\"Deployed to staging\",\"source\":\"hallucinated\"},"
                            + "{\"text\":\"Validated production metrics\"}]}"
            );

            CheckInOptionsResponse response = service.generateOptions(
                    ORG_ID, USER_ID, commitId, "ON_TRACK", null, 1);

            assertEquals("ok", response.status());
            assertEquals(List.of("ON_TRACK", "DONE_EARLY"), response.statusOptions());
            assertIterableEquals(
                    List.of(
                            new CheckInOptionItem("Deployed to staging", "user_history"),
                            new CheckInOptionItem("Code reviewed and merged", "user_history"),
                            new CheckInOptionItem("Daily rollout status", "team_common"),
                            new CheckInOptionItem("Validated production metrics", "ai_generated")
                    ),
                    response.progressOptions()
            );
        }

        @Test
        void includesPersonalHistoryTeamPatternsAndUserModelSummaryInPrompt() {
            UUID commitId = UUID.randomUUID();
            WeeklyCommitEntity commit = makeCommit(commitId);
            UserUpdatePatternEntity p1 = new UserUpdatePatternEntity(
                    UUID.randomUUID(), ORG_ID, USER_ID, "DELIVERY", "Deployed to staging");
            UserUpdatePatternEntity p2 = new UserUpdatePatternEntity(
                    UUID.randomUUID(), ORG_ID, USER_ID, "DELIVERY", "Validated production metrics");

            when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));
            when(userUpdatePatternService.getTopPatterns(any(), any(), any(), anyInt()))
                    .thenReturn(List.of(p1, p2));
            when(teamPatternService.getTopPatterns(ORG_ID, "DELIVERY", 5))
                    .thenReturn(List.of("Daily rollout status"));
            when(userModelService.getSnapshot(ORG_ID, USER_ID)).thenReturn(Optional.of(makeProfile()));

            ArgumentCaptor<List<LlmClient.Message>> messagesCaptor = ArgumentCaptor.forClass(List.class);
            when(llmClient.complete(messagesCaptor.capture(), any()))
                    .thenReturn("{\"statusOptions\":[],\"progressOptions\":[]}");

            service.generateOptions(ORG_ID, USER_ID, commitId, "ON_TRACK", null, 0);

            List<LlmClient.Message> capturedMessages = messagesCaptor.getValue();
            String systemMessageContent = capturedMessages.stream()
                    .filter(m -> m.role() == LlmClient.Role.SYSTEM)
                    .findFirst()
                    .map(LlmClient.Message::content)
                    .orElseThrow();
            String userMessageContent = capturedMessages.stream()
                    .filter(m -> m.role() == LlmClient.Role.USER)
                    .findFirst()
                    .map(LlmClient.Message::content)
                    .orElseThrow();

            assertTrue(systemMessageContent.contains("completion reliability 82%"));
            assertTrue(systemMessageContent.contains("current category DELIVERY done rate 90%"));
            assertTrue(userMessageContent.contains(
                    "User's common phrases: [Deployed to staging, Validated production metrics]"));
            assertTrue(userMessageContent.contains("Team's common phrases: [Daily rollout status]"));
        }

        @Test
        void usesTeamPatternsAsFallbackWhenPersonalHistoryIsSparse() {
            UUID commitId = UUID.randomUUID();
            WeeklyCommitEntity commit = makeCommit(commitId);
            UserUpdatePatternEntity p1 = new UserUpdatePatternEntity(
                    UUID.randomUUID(), ORG_ID, USER_ID, "DELIVERY", "Deployed to staging");

            when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));
            when(userUpdatePatternService.getTopPatterns(any(), any(), any(), anyInt()))
                    .thenReturn(List.of(p1));
            when(teamPatternService.getTopPatterns(ORG_ID, "DELIVERY", 5))
                    .thenReturn(List.of("Daily rollout status", "Blocked on dependency"));
            when(llmClient.complete(any(), any())).thenThrow(new LlmClient.LlmUnavailableException("down"));

            CheckInOptionsResponse response = service.generateOptions(
                    ORG_ID, USER_ID, commitId, null, null, 0);

            assertIterableEquals(
                    List.of(
                            new CheckInOptionItem("Deployed to staging", "user_history"),
                            new CheckInOptionItem("Daily rollout status", "team_common"),
                            new CheckInOptionItem("Blocked on dependency", "team_common")
                    ),
                    response.progressOptions()
            );
        }

        @Test
        void surfacesTeamCommonPatternsForNewUserWhenNoPersonalHistoryExists() {
            UUID commitId = UUID.randomUUID();
            WeeklyCommitEntity commit = makeCommit(commitId);

            when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));
            when(userUpdatePatternService.getTopPatterns(any(), any(), any(), anyInt()))
                    .thenReturn(List.of());
            when(teamPatternService.getTopPatterns(ORG_ID, "DELIVERY", 5))
                    .thenReturn(List.of("Daily rollout status", "Blocked on dependency"));
            when(llmClient.complete(any(), any())).thenThrow(new LlmClient.LlmUnavailableException("down"));

            CheckInOptionsResponse response = service.generateOptions(
                    ORG_ID, USER_ID, commitId, null, null, 0);

            assertIterableEquals(
                    List.of(
                            new CheckInOptionItem("Daily rollout status", "team_common"),
                            new CheckInOptionItem("Blocked on dependency", "team_common")
                    ),
                    response.progressOptions()
            );
        }

        @Test
        void skipsTeamFallbackLookupWhenPersonalHistoryAlreadyFillsSeededLimit() {
            UUID commitId = UUID.randomUUID();
            WeeklyCommitEntity commit = makeCommit(commitId);

            when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));
            when(userUpdatePatternService.getTopPatterns(any(), any(), any(), anyInt()))
                    .thenReturn(List.of(
                            new UserUpdatePatternEntity(UUID.randomUUID(), ORG_ID, USER_ID, "DELIVERY", "One"),
                            new UserUpdatePatternEntity(UUID.randomUUID(), ORG_ID, USER_ID, "DELIVERY", "Two"),
                            new UserUpdatePatternEntity(UUID.randomUUID(), ORG_ID, USER_ID, "DELIVERY", "Three"),
                            new UserUpdatePatternEntity(UUID.randomUUID(), ORG_ID, USER_ID, "DELIVERY", "Four"),
                            new UserUpdatePatternEntity(UUID.randomUUID(), ORG_ID, USER_ID, "DELIVERY", "Five")
                    ));
            when(llmClient.complete(any(), any())).thenThrow(new LlmClient.LlmUnavailableException("down"));

            CheckInOptionsResponse response = service.generateOptions(
                    ORG_ID, USER_ID, commitId, null, null, 0);

            assertEquals(5, response.progressOptions().size());
            verify(teamPatternService, never()).getTopPatterns(any(), any(), anyInt());
        }

        @Test
        void normalizesSeededDuplicatesBeforeApplyingTeamFallbackLimit() {
            UUID commitId = UUID.randomUUID();
            WeeklyCommitEntity commit = makeCommit(commitId);
            UserUpdatePatternEntity p1 = new UserUpdatePatternEntity(
                    UUID.randomUUID(), ORG_ID, USER_ID, "DELIVERY", "Deployed to staging");

            when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));
            when(userUpdatePatternService.getTopPatterns(any(), any(), any(), anyInt()))
                    .thenReturn(List.of(p1));
            when(teamPatternService.getTopPatterns(ORG_ID, "DELIVERY", 5)).thenReturn(List.of(
                    "  deployed   to staging  ",
                    "Daily rollout status",
                    "Blocked on dependency",
                    "Validated metrics",
                    "Shared release note"
            ));
            when(llmClient.complete(any(), any())).thenThrow(new LlmClient.LlmUnavailableException("down"));

            CheckInOptionsResponse response = service.generateOptions(
                    ORG_ID, USER_ID, commitId, null, null, 0);

            assertIterableEquals(
                    List.of(
                            new CheckInOptionItem("Deployed to staging", "user_history"),
                            new CheckInOptionItem("Daily rollout status", "team_common"),
                            new CheckInOptionItem("Blocked on dependency", "team_common"),
                            new CheckInOptionItem("Validated metrics", "team_common"),
                            new CheckInOptionItem("Shared release note", "team_common")
                    ),
                    response.progressOptions()
            );
        }

        @Test
        void limitsMergedOptionsToFiveInPriorityOrder() {
            UUID commitId = UUID.randomUUID();
            WeeklyCommitEntity commit = makeCommit(commitId);

            when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));
            when(teamPatternService.getTopPatterns(ORG_ID, "DELIVERY", 5))
                    .thenReturn(List.of("Team one", "Team two", "Team three", "Team four", "Team five"));
            when(llmClient.complete(any(), any())).thenReturn(
                    "{\"statusOptions\":[],\"progressOptions\":[{\"text\":\"AI one\"}]}"
            );

            CheckInOptionsResponse response = service.generateOptions(
                    ORG_ID, USER_ID, commitId, null, null, 0);

            assertEquals(5, response.progressOptions().size());
            assertEquals("Team one", response.progressOptions().get(0).text());
            assertEquals("team_common", response.progressOptions().get(0).source());
            assertEquals("Team five", response.progressOptions().get(4).text());
        }

        @Test
        void returnsEmptyOptionsWhenCommitNotFound() {
            UUID commitId = UUID.randomUUID();

            when(commitRepository.findById(commitId)).thenReturn(Optional.empty());

            CheckInOptionsResponse response = service.generateOptions(
                    ORG_ID, USER_ID, commitId, null, null, 0);

            assertEquals(CheckInOptionsResponse.empty(), response);
        }
    }
}
