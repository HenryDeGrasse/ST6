# R8: LLM Cost Anomaly

**Last reviewed:** 2026-03-13  
**Triggered by:** `WC_AI_CostAnomaly` (P2), `WC_AI_DailySpendExceeded` (P2)

## Impact

Unexpectedly high LLM spend. No user-facing impact unless the cost cap
circuit breaker trips, in which case AI features degrade gracefully
(users fall back to manual RCDO picker and manual reconciliation).

## Triage steps

1. **Check daily spend.**
   - Grafana → WC AI Operations → daily cost panel
   - Compare to baseline (typical daily spend for the org count)
   - If spend is 3× normal, this alert fires

2. **Identify the cost driver.**
   - Grafana → WC AI Operations → requests by endpoint
   - Which AI feature is responsible?
     - `ai.suggest` — RCDO suggestion (should be ~200 tokens/call)
     - `ai.reconciliation-draft` — reconciliation draft (~500 tokens/call)
     - `ai.manager-insights` — manager insights (~800 tokens/call)

3. **Check for runaway loops.**
   - Filter logs by `"component":"AiSuggestionService"` and count per `userId`
   - Is one user/org generating an abnormal number of calls?
   - Check for frontend bugs causing rapid re-requests (debounce failure)

4. **Check rate limiter status.**
   - Redis key: `ai:ratelimit:{orgId}:{userId}`
   - Is the per-user rate limiter functioning? Check `RateLimiter` logs

5. **Check token counts per request.**
   - Grafana → WC AI Operations → tokens per request histogram
   - If token counts are abnormally high, prompts may have grown
   - Check `PromptBuilder` — did a recent deploy change the system prompt?

6. **Check for model routing issues.**
   - Verify `ai.model` config value matches the intended model
   - A model change (e.g., from a smaller to larger model) can increase cost

## Mitigation

- **Immediate:** Disable AI features via feature flag
  ```
  wc.ai.suggest=false
  wc.ai.reconciliation-draft=false
  wc.ai.manager-insights=false
  ```

- **If caused by a specific user/org:** Add to rate-limit deny list
  ```bash
  redis-cli SET "ai:deny:{orgId}:{userId}" "1" EX 3600
  ```

- **If caused by prompt growth:** Roll back the prompt change

## Cost cap configuration

The `AiConfiguration` enforces:
- Per-user: max 20 requests/hour
- Per-org: max 500 requests/hour  
- Daily spend cap: configurable via `ai.daily-spend-cap-usd`

When the daily cap is hit, all AI features return graceful fallbacks
for the remainder of the UTC day.

## Prevention

- Review prompt token counts in PR reviews
- Set conservative daily spend caps per environment
- Monitor the cost anomaly dashboard weekly
