import createClient, {
  type Client,
  type ClientOptions,
} from "openapi-fetch";

import type {
  components,
  operations,
  paths,
} from "./generated/openapi.js";

/** OpenAPI-generated path map for the Weekly Commitments v1 API. */
export type WeeklyCommitmentsApiPaths = paths;

/** OpenAPI-generated schema components for the Weekly Commitments v1 API. */
export type WeeklyCommitmentsApiComponents = components;

/** OpenAPI-generated operation map for the Weekly Commitments v1 API. */
export type WeeklyCommitmentsApiOperations = operations;

/** Strongly typed fetch client built from the committed OpenAPI spec. */
export type WeeklyCommitmentsClient = Client<paths>;

export type WeeklyCommitmentsClientOptions = ClientOptions;

/**
 * Creates a typed API client backed by the generated OpenAPI path map.
 *
 * Frontend callers can supply the PA host's bearer-token fetch wrapper,
 * base URL, and default headers without duplicating endpoint types.
 */
export function createWeeklyCommitmentsClient(
  options: WeeklyCommitmentsClientOptions = {},
): WeeklyCommitmentsClient {
  return createClient<paths>(options);
}
