import { useCallback, useState } from "react";
import type { ApiErrorResponse, OutcomeForecastListResponse, OutcomeForecastResponse } from "@weekly-commitments/contracts";
import { useApiClient } from "../api/ApiContext.js";
import { useFeatureFlags } from "../context/FeatureFlagContext.js";

export interface UseForecastsResult {
  forecasts: OutcomeForecastResponse[];
  selectedForecast: OutcomeForecastResponse | null;
  loadingList: boolean;
  loadingForecast: boolean;
  errorList: string | null;
  errorForecast: string | null;
  fetchForecasts: () => Promise<void>;
  fetchForecast: (outcomeId: string) => Promise<void>;
  clearErrors: () => void;
}

export function useForecasts(): UseForecastsResult {
  const client = useApiClient();
  const flags = useFeatureFlags();

  const [forecasts, setForecasts] = useState<OutcomeForecastResponse[]>([]);
  const [selectedForecast, setSelectedForecast] = useState<OutcomeForecastResponse | null>(null);
  const [loadingList, setLoadingList] = useState(false);
  const [loadingForecast, setLoadingForecast] = useState(false);
  const [errorList, setErrorList] = useState<string | null>(null);
  const [errorForecast, setErrorForecast] = useState<string | null>(null);

  const extractError = useCallback((resp: { error?: unknown; response: Response }): string => {
    const err = resp.error as ApiErrorResponse | undefined;
    return err?.error?.message ?? `Request failed (${String(resp.response.status)})`;
  }, []);

  const fetchForecasts = useCallback(async () => {
    if (!flags.targetDateForecasting) {
      return;
    }

    setLoadingList(true);
    setErrorList(null);
    try {
      const resp = await client.GET("/outcomes/forecasts");
      if (resp.data) {
        const data = resp.data as OutcomeForecastListResponse;
        setForecasts(data.forecasts ?? []);
      } else {
        setErrorList(extractError(resp));
      }
    } catch (e) {
      setErrorList(e instanceof Error ? e.message : "Network error");
    } finally {
      setLoadingList(false);
    }
  }, [client, extractError, flags.targetDateForecasting]);

  const fetchForecast = useCallback(
    async (outcomeId: string) => {
      if (!flags.targetDateForecasting) {
        return;
      }

      setLoadingForecast(true);
      setErrorForecast(null);
      setSelectedForecast(null);
      try {
        const resp = await client.GET("/outcomes/{outcomeId}/forecast", {
          params: { path: { outcomeId } },
        });
        if (resp.data) {
          setSelectedForecast(resp.data as OutcomeForecastResponse);
        } else {
          setSelectedForecast(null);
          setErrorForecast(extractError(resp));
        }
      } catch (e) {
        setSelectedForecast(null);
        setErrorForecast(e instanceof Error ? e.message : "Network error");
      } finally {
        setLoadingForecast(false);
      }
    },
    [client, extractError, flags.targetDateForecasting],
  );

  const clearErrors = useCallback(() => {
    setErrorList(null);
    setErrorForecast(null);
  }, []);

  return {
    forecasts,
    selectedForecast,
    loadingList,
    loadingForecast,
    errorList,
    errorForecast,
    fetchForecasts,
    fetchForecast,
    clearErrors,
  };
}
