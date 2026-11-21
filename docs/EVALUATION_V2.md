# Smart Email Writer v2 — Before/After Evaluation

**Date:** 2026-11-21 14:36 IST  
**Repo:** same repository as v1; all components bumped to `2.0.0`  
**Baseline:** `docs/EVALUATION.md` — 24 requests, `gemini-3.6-flash`, judge `gemini-3.5-flash-lite`  
**v2 run:** 24 requests, `gemini-3.5-flash-lite`, judge `gemini-3.5-flash-lite`  
**Sample:** 6 fixed emails × 4 tones (`professional`, `friendly`, `assertive`, `concise`)

This document is separate from the README. It is a one-off A/B measurement, not a production SLA.

## 1. What changed in v2

| Area | v1 | v2 |
| --- | --- | --- |
| Generation model | `gemini-3.6-flash` | `gemini-3.5-flash-lite` |
| Retries | none | 1 retry, exponential backoff + jitter for 429 / 5xx / timeout |
| Repeated prompts | always called Gemini | SHA-256 keyed in-memory cache, `X-Cache: HIT/MISS`, 1h TTL, 500 entries |
| Tone prompt | generic instruction | explicit per-tone instructions, including word-count guidance |
| 429 message | raw API error | friendly “rate limit reached” message |
| Response timeout | 60 s | 30 s (bounded worst case with one retry) |
| Component versions | `1.0.0` | `2.0.0` for backend, frontend and extension |

A rejected intermediate configuration kept `gemini-3.6-flash` with thinking enabled. It produced 6–22 word replies and 60–120 s latencies, so v2 switched to the lite model and restored `maxOutputTokens: 1024`. The numbers below are from the final configuration.

## 2. Headline comparison

| Metric | v1 | v2 | Change |
| --- | --- | --- | --- |
| Successful replies | 14 / 24 (58.3%) | **24 / 24 (100%)** | **+41.7 pp** |
| Errors | 10 / 24 (41.7%) | **0 / 24 (0%)** | **−41.7 pp** |
| Mean latency, all requests | 12.61 s | **2.20 s** | **−82.5%** |
| p50 latency, all requests | 7.68 s | **1.12 s** | **−85.4%** |
| p90 latency, all requests | 39.95 s | **3.71 s** | **−90.7%** |
| Max latency | 60.05 s | **12.85 s** | **−78.6%** |
| Mean judge quality | 4.71 / 5 | 4.55 / 5 | −0.16 |
| Replies rated ≥ 4 / 5 | 100% | 90.9% | −9.1 pp |
| Relevance | 100% | **100%** | unchanged |
| Subject-line leakage | 0% | **0%** | unchanged |
| Tone accuracy among judged replies | 71.4% | 63.6% | −7.8 pp |
| Confirmed correct-tone yield (of all 24) | 10 / 24 (41.7%) | **14 / 24 (58.3%)** | **+16.6 pp** |

The biggest win is reliability and speed: v2 removed every error in this run and cut p90 latency by over 90%. Quality stayed high, but tone fidelity did not improve — that remains the next optimization target.

## 3. Latency detail

### v2 successful requests (n = 24)

| Statistic | v1 successful | v2 successful |
| --- | --- | --- |
| Min | 4.47 s | **0.71 s** |
| Mean | 17.14 s | **2.20 s** |
| p50 | 10.50 s | **1.12 s** |
| p90 | 39.95 s | **3.71 s** |
| p95 | 40.13 s | **4.09 s** |
| Max | 40.13 s | **12.85 s** |
| Std. dev. | 11.93 s | **2.46 s** |

Average successful latency by tone:

| Tone | v1 | v2 |
| --- | --- | --- |
| Professional | 13.88 s | 4.09 s |
| Friendly | 15.51 s | 1.20 s |
| Assertive | 27.48 s | 1.86 s |
| Concise | 9.32 s | 1.67 s |

### Cache verification

After the 24 measured calls, one previously successful `(email, tone)` pair was repeated:

| Request | Status | Latency | Header |
| --- | --- | --- | --- |
| Repeat of `meeting-reschedule` / `professional` | 200 | **19 ms** | `X-Cache: HIT` |

The same prompt took 3,043 ms on its first call — about a **160× latency reduction** on a cache hit.

## 4. Errors

| Error | v1 | v2 |
| --- | --- | --- |
| HTTP 429 | 9 | **0** |
| HTTP 502 / timeout | 1 | **0** |
| Client timeout | 0 | **0** |
| Total | 10 | **0** |

Important caveat: this is partly a model/quota effect. `gemini-3.6-flash` hit the free-tier daily limit of 20 requests; `gemini-3.5-flash-lite` had quota headroom during this run. The retry and backoff logic was exercised in unit tests and in the rejected intermediate run, but no end-user retry was needed in the final 24-call sample.

## 5. Quality and tone

| Metric | v1 | v2 |
| --- | --- | --- |
| Judge coverage | 14 / 24 | 22 / 24 |
| Tone accuracy among judged | 71.4% | 63.6% |
| Relevance | 100% | 100% |
| Context keyword coverage | 100% | 91.7% |
| Subject-line leakage | 0% | 0% |
| Mean judge quality | 4.71 | 4.55 |
| Quality ≥ 4 | 100% | 90.9% |

Tone accuracy by requested tone:

| Tone | v1 | v2 |
| --- | --- | --- |
| Professional | 100% | **100%** |
| Friendly | 100% | **100%** |
| Assertive | 75% | 50% |
| Concise | 0% | 16.7% |

Professional and friendly remain reliable. Assertive replies were sometimes graded as professional, and short concise replies were usually graded as professional — the same LLM-judge limitation seen in v1. Because judge coverage differs (14 vs 22), the aggregate tone-accuracy comparison should be treated as directional only.

## 6. Length

| Statistic | v1 | v2 |
| --- | --- | --- |
| Min words | 21 | 6 |
| Mean words | 59.3 | 53.7 |
| Max words | 95 | 104 |
| Within 40–180 words | 78.6% | 70.8% |

One `bug-escalation` / `assertive` reply was only 6 words. This is the clearest remaining quality outlier and should be addressed with a minimum-length guard or stronger assertive examples.

## 7. Interpretation

- **Solved:** the v1 reliability bottleneck. 24/24 calls succeeded with no 429s or timeouts.
- **Solved:** the v1 latency problem. Median latency dropped from 10.50 s to 1.12 s, and p90 from 39.95 s to 3.71 s.
- **Solved:** repeated-prompt waste. A cache hit returned in 19 ms with `X-Cache: HIT`.
- **Mostly preserved:** output quality — 4.55/5 mean judge score, 100% relevance, zero subject-line leakage.
- **Not solved:** tone fidelity. The judge still struggles with `concise` and sometimes maps `assertive` to `professional`. One 6-word reply is a quality outlier.
- **Next target:** tone control and minimum-length enforcement, ideally with few-shot examples or a deterministic post-generation tone/length check.

## 8. Caveats

- Single-run, small sample (24 calls per version).
- Different generation models between v1 and v2, so this is a system-level comparison, not a pure code-change comparison.
- Free-tier quota state differs between runs; v2's zero errors are partly quota-dependent.
- LLM-as-judge is subjective, especially for short replies; 2 of 24 v2 replies could not be judged reliably.
- No concurrency, cold start, or very long email stress cases were measured.
- The cache is process-local and not shared across backend replicas.

## 9. Reproducing

1. Start the v2 backend with `GEMINI_API_KEY` in `backend\.env` on port 8080.
2. Send 24 sequential `POST /api/email/generate` calls: 6 fixed emails × 4 tones.
3. Record HTTP status, client-side latency and the `X-Cache` header.
4. Judge each HTTP 200 reply with `gemini-3.5-flash-lite`, requesting JSON with `tone`, `relevant`, `subject_line`, `quality` (0–5).
5. Repeat one successful prompt to confirm `X-Cache: HIT`.
6. Compare against the v1 numbers in `docs/EVALUATION.md`.
