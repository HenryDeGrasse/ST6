# R7: LLM Timeout Rate Elevated

**Last reviewed:** 2026-03-12  
**Triggered by:** `WC_AI_TimeoutRate` (P3)

## Impact

AI features degrade gracefully (PRD §9.5):
- Users see manual RCDO picker instead of suggestions
- No workflow disruption — AI is a convenience, not a gate

## Triage steps

1. **Check LLM provider status page.**
   - Anthropic: https://status.anthropic.com
   - If provider is degraded → no action needed, system degrades gracefully

2. **Check `ai.llm.request.seconds` metrics.**
   - Is latency increasing gradually or are requests timing out immediately?
   - Immediate timeout → likely network issue
   - Gradual increase → provider slowness

3. **If network issue (requests timeout immediately):**
   - Check NAT Gateway health
   - Check VPC endpoint (if configured)
   - Verify DNS resolution for the LLM API endpoint

4. **If persistent (> 4 hours):**
   - Consider temporarily disabling AI features via feature flag
   - `wc.ai.suggest=false` reduces noise in logs and metrics

## Resolution

- Provider outage: wait for recovery (no action needed)
- Network issue: fix networking, verify with test request
- Persistent: evaluate switching provider via `ai.provider` config change
