/**
 * Hook for in-app notifications (unread count, list, mark-read).
 */
import { useState, useCallback } from "react";
import type {
  NotificationItem,
  ApiErrorResponse,
} from "@weekly-commitments/contracts";
import { useApiClient } from "../api/ApiContext.js";

export interface UseNotificationsResult {
  notifications: NotificationItem[];
  unreadCount: number;
  loading: boolean;
  error: string | null;
  fetchUnread: () => Promise<void>;
  markRead: (notificationId: string) => Promise<void>;
  markAllRead: () => Promise<void>;
  clearError: () => void;
}

export function useNotifications(): UseNotificationsResult {
  const client = useApiClient();
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const clearError = useCallback(() => setError(null), []);

  const extractError = useCallback(
    (resp: { error?: unknown; response: Response }): string => {
      const err = resp.error as ApiErrorResponse | undefined;
      if (err?.error?.message) return err.error.message;
      return `Request failed (${String(resp.response.status)})`;
    },
    [],
  );

  const fetchUnread = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const resp = await client.GET("/notifications/unread");
      if (resp.data) {
        setNotifications(resp.data as NotificationItem[]);
      } else {
        setError(extractError(resp));
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Network error");
    } finally {
      setLoading(false);
    }
  }, [client, extractError]);

  const markRead = useCallback(
    async (notificationId: string) => {
      try {
        const resp = await client.POST("/notifications/{notificationId}/read", {
          params: { path: { notificationId } },
        });
        if (resp.response.ok) {
          setNotifications((prev) =>
            prev.filter((n) => n.id !== notificationId),
          );
          return;
        }
        setError(extractError(resp));
      } catch (e) {
        setError(e instanceof Error ? e.message : "Network error");
      }
    },
    [client, extractError],
  );

  const markAllRead = useCallback(async () => {
    try {
      const resp = await client.POST("/notifications/read-all");
      if (resp.response.ok) {
        setNotifications([]);
        return;
      }
      setError(extractError(resp));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Network error");
    }
  }, [client, extractError]);

  return {
    notifications,
    unreadCount: notifications.length,
    loading,
    error,
    fetchUnread,
    markRead,
    markAllRead,
    clearError,
  };
}
