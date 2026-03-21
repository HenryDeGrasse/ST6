package com.weekly.quickupdate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.ai.LlmClient;
import com.weekly.plan.domain.CommitCategory;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.usermodel.UserUpdatePatternEntity;
import com.weekly.usermodel.UserUpdatePatternService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link CheckInOptionService}.
 *
 * <p>Follows the pattern established in {@code DefaultAiSuggestionServiceTest}:
 * Mockito for dependency injection and {@link ArgumentCaptor} for prompt-content
 * assertions.
 */
@SuppressWarnings("unchecked")
class CheckInOptionServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private WeeklyCommitRepository commitRepository;
    private UserUpdatePatternService userUpdatePatternService;
    private LlmClient llmClient;
    private CheckInOptionService service;

    @BeforeEach
    void setUp() {
        commitRepository = mock(WeeklyCommitRepository.class);
        userUpdatePatternService = mock(UserUpdatePatternService.class);
        llmClient = mock(LlmClient.class);
        service = new CheckInOptionService(
                commitRepository, userUpdatePatternService, llmClient, new ObjectMapper());

        // Default: no user patterns
        when(userUpdatePatternService.getTopPatterns(any(), any(), any(), anyInt()))
                .thenReturn(List.of());
    }

    /**
     * Helper that creates a {@link WeeklyCommitEntity} with category DELIVERY
     * and a populated snapshot outcome name, owned by {@link #ORG_ID}.
     */
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

    // ─── GenerateOptions ──────────────────────────────────────────────────────

    @Nested
    class GenerateOptions {

        @Test
        void returnsOptionsOnSuccessfulLlmCall() {
            UUID commitId = UUID.randomUUID();
            WeeklyCommitEntity commit = makeCommit(commitId);

            when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));
            when(llmClient.complete(any(), any())).thenReturn(
                    "{\"statusOptions\":[\"ON_TRACK\",\"DONE_EARLY\"],"
                            + "\"progressOptions\":"
                            + "[{\"text\":\"Deployed to staging\",\"source\":\"ai_generated\"}]}");

            CheckInOptionsResponse response = service.generateOptions(
                    ORG_ID, USER_ID, commitId, "ON_TRACK", null, 1);

            assertEquals("ok", response.status());
            assertEquals(List.of("ON_TRACK", "DONE_EARLY"), response.statusOptions());
            assertEquals(1, response.progressOptions().size());
            assertEquals("Deployed to staging", response.progressOptions().get(0).text());
            assertEquals("ai_generated", response.progressOptions().get(0).source());
        }

        @Test
        void includesUserPatternsInPrompt() {
            UUID commitId = UUID.randomUUID();
            WeeklyCommitEntity commit = makeCommit(commitId);

            UserUpdatePatternEntity p1 = new UserUpdatePatternEntity(
                    UUID.randomUUID(), ORG_ID, USER_ID, "DELIVERY", "Deployed to staging");
            UserUpdatePatternEntity p2 = new UserUpdatePatternEntity(
                    UUID.randomUUID(), ORG_ID, USER_ID, "DELIVERY", "Code reviewed and merged");

            when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));
            when(userUpdatePatternService.getTopPatterns(any(), any(), any(), anyInt()))
                    .thenReturn(List.of(p1, p2));

            ArgumentCaptor<List<LlmClient.Message>> messagesCaptor =
                    ArgumentCaptor.forClass(List.class);
            when(llmClient.complete(messagesCaptor.capture(), any()))
                    .thenReturn("{\"statusOptions\":[],\"progressOptions\":[]}");

            service.generateOptions(ORG_ID, USER_ID, commitId, "ON_TRACK", null, 0);

            List<LlmClient.Message> capturedMessages = messagesCaptor.getValue();
            String userMessageContent = capturedMessages.stream()
                    .filter(m -> m.role() == LlmClient.Role.USER)
                    .findFirst()
                    .map(LlmClient.Message::content)
                    .orElseThrow();

            assertTrue(userMessageContent.contains("Deployed to staging"),
                    "USER message should contain first pattern text");
            assertTrue(userMessageContent.contains("Code reviewed and merged"),
                    "USER message should contain second pattern text");
        }

        @Test
        void returnsEmptyOptionsWhenLlmThrows() {
            UUID commitId = UUID.randomUUID();
            WeeklyCommitEntity commit = makeCommit(commitId);

            when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));
            when(llmClient.complete(any(), any()))
                    .thenThrow(new LlmClient.LlmUnavailableException("service down"));

            CheckInOptionsResponse response = service.generateOptions(
                    ORG_ID, USER_ID, commitId, null, null, 0);

            assertEquals(CheckInOptionsResponse.empty(), response);
        }

        @Test
        void returnsEmptyOptionsOnMalformedJson() {
            UUID commitId = UUID.randomUUID();
            WeeklyCommitEntity commit = makeCommit(commitId);

            when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));
            when(llmClient.complete(any(), any())).thenReturn("not json");

            CheckInOptionsResponse response = service.generateOptions(
                    ORG_ID, USER_ID, commitId, null, null, 0);

            assertEquals(CheckInOptionsResponse.empty(), response);
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
