/**
 * Hook for team backlog issue CRUD, filtering, and sorting.
 */
import { useState, useCallback } from "react";
import type {
  Issue,
  IssueListResponse,
  IssueDetailResponse,
  CreateIssueRequest,
  UpdateIssueRequest,
  AssignIssueRequest,
  AddCommentRequest,
  LogTimeEntryRequest,
  IssueActivity,
  ApiErrorResponse,
  EffortType,
  IssueStatus,
} from "@weekly-commitments/contracts";
import { useApiClient } from "../api/ApiContext.js";

export type IssueSortField = "createdAt" | "updatedAt" | "chessPriority" | "ai_rank";

export interface IssueFilters {
  status?: IssueStatus;
  effortType?: EffortType;
  assigneeUserId?: string;
  outcomeId?: string;
  search?: string;
}

export interface UseIssuesResult {
  issues: Issue[];
  totalElements: number;
  totalPages: number;
  loading: boolean;
  error: string | null;
  fetchIssues: (teamId: string, page?: number, size?: number, filters?: IssueFilters, sort?: IssueSortField) => Promise<void>;
  fetchIssueDetail: (issueId: string) => Promise<IssueDetailResponse | null>;
  createIssue: (teamId: string, req: CreateIssueRequest) => Promise<Issue | null>;
  updateIssue: (issueId: string, req: UpdateIssueRequest) => Promise<Issue | null>;
  deleteIssue: (issueId: string) => Promise<boolean>;
  assignIssue: (issueId: string, req: AssignIssueRequest) => Promise<Issue | null>;
  addComment: (issueId: string, req: AddCommentRequest) => Promise<IssueActivity | null>;
  logTime: (issueId: string, req: LogTimeEntryRequest) => Promise<IssueActivity | null>;
  clearError: () => void;
}

export function useIssues(): UseIssuesResult {
  const client = useApiClient();
  const [issues, setIssues] = useState<Issue[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const clearError = useCallback(() => setError(null), []);

  const extractError = useCallback((resp: { error?: unknown; response: Response }): string => {
    const err = resp.error as ApiErrorResponse | undefined;
    if (err?.error?.message) return err.error.message;
    return `Request failed (${String(resp.response.status)})`;
  }, []);

  const fetchIssues = useCallback(
    async (
      teamId: string,
      page = 0,
      size = 20,
      filters: IssueFilters = {},
      sort: IssueSortField = "createdAt",
    ) => {
      setLoading(true);
      setError(null);
      try {
        const query: Record<string, unknown> = { page, size, sort };
        if (filters.status) query.status = filters.status;
        if (filters.effortType) query.effortType = filters.effortType;
        if (filters.assigneeUserId) query.assigneeUserId = filters.assigneeUserId;
        if (filters.outcomeId) query.outcomeId = filters.outcomeId;
        if (filters.search) query.search = filters.search;

        const resp = await client.GET("/teams/{teamId}/issues", {
          params: {
            path: { teamId },
            query: query as Parameters<typeof client.GET>[1]["params"]["query"],
          },
        });
        if (resp.data) {
          const data = resp.data as IssueListResponse;
          setIssues(data.content);
          setTotalElements(data.totalElements);
          setTotalPages(data.totalPages);
        } else {
          setError(extractError(resp));
        }
      } catch (e) {
        setError(e instanceof Error ? e.message : "Network error");
      } finally {
        setLoading(false);
      }
    },
    [client, extractError],
  );

  const fetchIssueDetail = useCallback(
    async (issueId: string): Promise<IssueDetailResponse | null> => {
      setLoading(true);
      setError(null);
      try {
        const resp = await client.GET("/issues/{issueId}", {
          params: { path: { issueId } },
        });
        if (resp.data) {
          return resp.data as IssueDetailResponse;
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

  const createIssue = useCallback(
    async (teamId: string, req: CreateIssueRequest): Promise<Issue | null> => {
      setLoading(true);
      setError(null);
      try {
        // The generated types omit `null` for enum fields; strip them to `undefined`
        const body = {
          title: req.title,
          ...(req.description !== undefined ? { description: req.description } : {}),
          ...(req.effortType != null ? { effortType: req.effortType } : {}),
          ...(req.estimatedHours !== undefined ? { estimatedHours: req.estimatedHours } : {}),
          ...(req.chessPriority != null ? { chessPriority: req.chessPriority } : {}),
          ...(req.outcomeId !== undefined ? { outcomeId: req.outcomeId } : {}),
          ...(req.nonStrategicReason !== undefined ? { nonStrategicReason: req.nonStrategicReason } : {}),
          ...(req.assigneeUserId !== undefined ? { assigneeUserId: req.assigneeUserId } : {}),
          ...(req.blockedByIssueId !== undefined ? { blockedByIssueId: req.blockedByIssueId } : {}),
        };
        const resp = await client.POST("/teams/{teamId}/issues", {
          params: { path: { teamId } },
          body,
        });
        if (resp.data) {
          const created = resp.data as Issue;
          setIssues((prev) => [created, ...prev]);
          setTotalElements((prev) => prev + 1);
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

  const updateIssue = useCallback(
    async (issueId: string, req: UpdateIssueRequest): Promise<Issue | null> => {
      setLoading(true);
      setError(null);
      try {
        // Strip null enum fields to undefined for generated type compatibility
        const body: Record<string, unknown> = {};
        if (req.title !== undefined) body.title = req.title;
        if (req.description !== undefined) body.description = req.description;
        if (req.effortType != null) body.effortType = req.effortType;
        if (req.estimatedHours !== undefined) body.estimatedHours = req.estimatedHours;
        if (req.chessPriority != null) body.chessPriority = req.chessPriority;
        if (req.outcomeId !== undefined) body.outcomeId = req.outcomeId;
        if (req.nonStrategicReason !== undefined) body.nonStrategicReason = req.nonStrategicReason;
        if (req.assigneeUserId !== undefined) body.assigneeUserId = req.assigneeUserId;
        if (req.blockedByIssueId !== undefined) body.blockedByIssueId = req.blockedByIssueId;
        if (req.status !== undefined) body.status = req.status;
        if (req.version !== undefined) body.version = req.version;
        const resp = await client.PATCH("/issues/{issueId}", {
          params: { path: { issueId } },
          body: body as Parameters<typeof client.PATCH>[1]["body"],
        });
        if (resp.data) {
          const updated = resp.data as Issue;
          setIssues((prev) => prev.map((i) => (i.id === issueId ? updated : i)));
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
    [client, extractError],
  );

  const deleteIssue = useCallback(
    async (issueId: string): Promise<boolean> => {
      setLoading(true);
      setError(null);
      try {
        const resp = await client.DELETE("/issues/{issueId}", {
          params: { path: { issueId } },
        });
        if (resp.response.ok) {
          setIssues((prev) => prev.filter((i) => i.id !== issueId));
          setTotalElements((prev) => Math.max(0, prev - 1));
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

  const assignIssue = useCallback(
    async (issueId: string, req: AssignIssueRequest): Promise<Issue | null> => {
      setLoading(true);
      setError(null);
      try {
        const resp = await client.POST("/issues/{issueId}/assign", {
          params: { path: { issueId } },
          body: req,
        });
        if (resp.data) {
          const updated = resp.data as Issue;
          setIssues((prev) => prev.map((i) => (i.id === issueId ? updated : i)));
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
    [client, extractError],
  );

  const addComment = useCallback(
    async (issueId: string, req: AddCommentRequest): Promise<IssueActivity | null> => {
      setLoading(true);
      setError(null);
      try {
        const resp = await client.POST("/issues/{issueId}/comment", {
          params: { path: { issueId } },
          body: req,
        });
        if (resp.data) {
          return resp.data as IssueActivity;
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

  const logTime = useCallback(
    async (issueId: string, req: LogTimeEntryRequest): Promise<IssueActivity | null> => {
      setLoading(true);
      setError(null);
      try {
        const resp = await client.POST("/issues/{issueId}/time-entry", {
          params: { path: { issueId } },
          body: req,
        });
        if (resp.data) {
          return resp.data as IssueActivity;
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

  return {
    issues,
    totalElements,
    totalPages,
    loading,
    error,
    fetchIssues,
    fetchIssueDetail,
    createIssue,
    updateIssue,
    deleteIssue,
    assignIssue,
    addComment,
    logTime,
    clearError,
  };
}
