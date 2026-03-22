/**
 * useTeamManagement — extends useTeams with access request operations
 * and team creation workflow state.
 */
import { useState, useCallback } from "react";
import type {
  Team,
  TeamMember,
  TeamAccessRequest,
  TeamDetailResponse,
  CreateTeamRequest,
  UpdateTeamRequest,
  AddTeamMemberRequest,
  TeamAccessRequestAction,
  ApiErrorResponse,
} from "@weekly-commitments/contracts";
import { AccessRequestStatus, TeamRole } from "@weekly-commitments/contracts";
import { useApiClient } from "../api/ApiContext.js";

export interface UseTeamManagementResult {
  // Team list
  teams: Team[];
  loading: boolean;
  error: string | null;
  fetchTeams: () => Promise<void>;

  // Team detail (members)
  teamDetail: TeamDetailResponse | null;
  fetchTeamDetail: (teamId: string) => Promise<TeamDetailResponse | null>;

  // Team CRUD
  createTeam: (req: CreateTeamRequest) => Promise<Team | null>;
  updateTeam: (teamId: string, req: UpdateTeamRequest) => Promise<Team | null>;

  // Member management
  addMember: (teamId: string, req: AddTeamMemberRequest) => Promise<TeamMember | null>;
  removeMember: (teamId: string, userId: string) => Promise<boolean>;

  // Access requests
  accessRequests: TeamAccessRequest[];
  loadingRequests: boolean;
  fetchAccessRequests: (teamId: string) => Promise<void>;
  requestAccess: (teamId: string) => Promise<TeamAccessRequest | null>;
  decideAccessRequest: (
    teamId: string,
    requestId: string,
    status: AccessRequestStatus.APPROVED | AccessRequestStatus.DENIED,
  ) => Promise<boolean>;

  clearError: () => void;
}

export function useTeamManagement(): UseTeamManagementResult {
  const client = useApiClient();

  const [teams, setTeams] = useState<Team[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [teamDetail, setTeamDetail] = useState<TeamDetailResponse | null>(null);

  const [accessRequests, setAccessRequests] = useState<TeamAccessRequest[]>([]);
  const [loadingRequests, setLoadingRequests] = useState(false);

  const clearError = useCallback(() => setError(null), []);

  const extractError = useCallback((resp: { error?: unknown; response: Response }): string => {
    const err = resp.error as ApiErrorResponse | undefined;
    if (err?.error?.message) return err.error.message;
    return `Request failed (${String(resp.response.status)})`;
  }, []);

  // ── Team list ───────────────────────────────────────────────────────────

  const fetchTeams = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const resp = await client.GET("/teams");
      if (resp.data) {
        const data = resp.data as { teams: Team[] };
        setTeams(data.teams);
      } else {
        setError(extractError(resp));
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Network error");
    } finally {
      setLoading(false);
    }
  }, [client, extractError]);

  // ── Team detail ─────────────────────────────────────────────────────────

  const fetchTeamDetail = useCallback(
    async (teamId: string): Promise<TeamDetailResponse | null> => {
      setLoading(true);
      setError(null);
      try {
        const resp = await client.GET("/teams/{teamId}", {
          params: { path: { teamId } },
        });
        if (resp.data) {
          const detail = resp.data as TeamDetailResponse;
          setTeamDetail(detail);
          return detail;
        }
        setError(extractError(resp));
        return null;
      } catch (e) {
        setError(e instanceof Error ? e.message : "Network error");
        return null;
      } finally {
        setLoading(false);
      }
    },
    [client, extractError],
  );

  // ── Team CRUD ───────────────────────────────────────────────────────────

  const createTeam = useCallback(
    async (req: CreateTeamRequest): Promise<Team | null> => {
      setLoading(true);
      setError(null);
      try {
        const resp = await client.POST("/teams", { body: req });
        if (resp.data) {
          const created = resp.data as Team;
          setTeams((prev) => [...prev, created]);
          return created;
        }
        setError(extractError(resp));
        return null;
      } catch (e) {
        setError(e instanceof Error ? e.message : "Network error");
        return null;
      } finally {
        setLoading(false);
      }
    },
    [client, extractError],
  );

  const updateTeam = useCallback(
    async (teamId: string, req: UpdateTeamRequest): Promise<Team | null> => {
      setLoading(true);
      setError(null);
      try {
        const resp = await client.PATCH("/teams/{teamId}", {
          params: { path: { teamId } },
          body: req,
        });
        if (resp.data) {
          const updated = resp.data as Team;
          setTeams((prev) => prev.map((t) => (t.id === teamId ? updated : t)));
          if (teamDetail?.team.id === teamId) {
            setTeamDetail((prev) => (prev ? { ...prev, team: updated } : prev));
          }
          return updated;
        }
        setError(extractError(resp));
        return null;
      } catch (e) {
        setError(e instanceof Error ? e.message : "Network error");
        return null;
      } finally {
        setLoading(false);
      }
    },
    [client, extractError, teamDetail],
  );

  // ── Member management ───────────────────────────────────────────────────

  const addMember = useCallback(
    async (teamId: string, req: AddTeamMemberRequest): Promise<TeamMember | null> => {
      setLoading(true);
      setError(null);
      try {
        const resp = await client.POST("/teams/{teamId}/members", {
          params: { path: { teamId } },
          body: req,
        });
        if (resp.data) {
          const member = resp.data as TeamMember;
          setTeamDetail((prev) =>
            prev ? { ...prev, members: [...prev.members, member] } : prev,
          );
          return member;
        }
        setError(extractError(resp));
        return null;
      } catch (e) {
        setError(e instanceof Error ? e.message : "Network error");
        return null;
      } finally {
        setLoading(false);
      }
    },
    [client, extractError],
  );

  const removeMember = useCallback(
    async (teamId: string, userId: string): Promise<boolean> => {
      setLoading(true);
      setError(null);
      try {
        const resp = await client.DELETE("/teams/{teamId}/members/{userId}", {
          params: { path: { teamId, userId } },
        });
        if (resp.response.ok) {
          setTeamDetail((prev) =>
            prev
              ? { ...prev, members: prev.members.filter((m) => m.userId !== userId) }
              : prev,
          );
          return true;
        }
        setError(extractError(resp));
        return false;
      } catch (e) {
        setError(e instanceof Error ? e.message : "Network error");
        return false;
      } finally {
        setLoading(false);
      }
    },
    [client, extractError],
  );

  // ── Access requests ─────────────────────────────────────────────────────

  const fetchAccessRequests = useCallback(
    async (teamId: string) => {
      setLoadingRequests(true);
      setError(null);
      try {
        const resp = await client.GET("/teams/{teamId}/access-requests", {
          params: { path: { teamId } },
        });
        if (resp.data) {
          const data = resp.data as { requests: TeamAccessRequest[] };
          setAccessRequests(data.requests);
        } else {
          setError(extractError(resp));
        }
      } catch (e) {
        setError(e instanceof Error ? e.message : "Network error");
      } finally {
        setLoadingRequests(false);
      }
    },
    [client, extractError],
  );

  const requestAccess = useCallback(
    async (teamId: string): Promise<TeamAccessRequest | null> => {
      setLoading(true);
      setError(null);
      try {
        const resp = await client.POST("/teams/{teamId}/access-requests", {
          params: { path: { teamId } },
        });
        if (resp.data) {
          return resp.data as TeamAccessRequest;
        }
        setError(extractError(resp));
        return null;
      } catch (e) {
        setError(e instanceof Error ? e.message : "Network error");
        return null;
      } finally {
        setLoading(false);
      }
    },
    [client, extractError],
  );

  const decideAccessRequest = useCallback(
    async (
      teamId: string,
      requestId: string,
      status: AccessRequestStatus.APPROVED | AccessRequestStatus.DENIED,
    ): Promise<boolean> => {
      setLoading(true);
      setError(null);
      try {
        const body: TeamAccessRequestAction = { status };
        const approvedRequest = accessRequests.find((request) => request.id === requestId) ?? null;
        const resp = await client.PATCH("/teams/{teamId}/access-requests/{requestId}", {
          params: { path: { teamId, requestId } },
          body,
        });
        if (resp.response.ok) {
          setAccessRequests((prev) =>
            prev.map((r) => (r.id === requestId ? { ...r, status } : r)),
          );
          if (status === AccessRequestStatus.APPROVED && approvedRequest) {
            setTeamDetail((prev) => {
              if (!prev || prev.members.some((member) => member.userId === approvedRequest.requesterUserId)) {
                return prev;
              }
              return {
                ...prev,
                members: [
                  ...prev.members,
                  {
                    teamId,
                    userId: approvedRequest.requesterUserId,
                    orgId: approvedRequest.orgId,
                    role: TeamRole.MEMBER,
                    joinedAt: new Date().toISOString(),
                  },
                ],
              };
            });
          }
          return true;
        }
        setError(extractError(resp));
        return false;
      } catch (e) {
        setError(e instanceof Error ? e.message : "Network error");
        return false;
      } finally {
        setLoading(false);
      }
    },
    [accessRequests, client, extractError],
  );

  return {
    teams,
    loading,
    error,
    fetchTeams,
    teamDetail,
    fetchTeamDetail,
    createTeam,
    updateTeam,
    addMember,
    removeMember,
    accessRequests,
    loadingRequests,
    fetchAccessRequests,
    requestAccess,
    decideAccessRequest,
    clearError,
  };
}
