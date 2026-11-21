# Smart Email Writer — Quantitative Evaluation

**Date:** 2026-11-21 12:22 IST  
**Target:** locally running Smart Email Writer backend (`http://localhost:8080`) using a real Gemini API key  
**Generation model:** `gemini-3.6-flash`  
**Judge model:** `gemini-3.5-flash-lite`  
**Sample:** 24 requests = 6 email scenarios × 4 tones

This document is intentionally separate from the main README. It records a one-off measurement run and the numbers below are not a claim of production-grade performance.

## 1. Methodology

- Requests were sent sequentially to `POST /api/email/generate` with a fixed body of `emailContent` + `tone`.
- Scenarios covered meeting rescheduling, deadline extension, proposal feedback, vendor pricing, leave request and bug escalation.
- Tones: `professional`, `friendly`, `assertive`, `concise`.
- Latency was measured client-side around the HTTP call (includes backend + Gemini network time).
- Every successful reply was graded by a second Gemini model acting as a judge. The judge returned:
  - `tone` — one of the four requested tones
  - `relevant` — whether the reply addresses the original email
  - `subject_line` — whether a subject line leaked into the body
  - `quality` — integer 0–5
- Deterministic checks were also applied: scenario keyword presence, literal `Subject:` text, and word count.
- The backend was warm; no cold-start measurement was included.

## 2. Headline results

| Metric | Value |
| --- | --- |
| Total requests | 24 |
| Successful replies (HTTP 200, non-empty) | 14 (58.3%) |
| Errors | 10 (41.7%) |
| Successful replies with the requested tone | 10 / 24 (41.7% end-to-end) |
| Mean judge quality (successful replies) | 4.71 / 5 (94.2 / 100) |
| Replies rated ≥ 4 / 5 | 100% of judged replies |

## 3. Latency

### Successful requests only (n = 14)

| Statistic | Value |
| --- | --- |
| Min | 4.47 s |
| Mean | 17.14 s |
| p50 (median) | 10.50 s |
| p90 | 39.95 s |
| Max | 40.13 s |

### All requests, including failures (n = 24)

| Statistic | Value |
| --- | --- |
| Min | 0.25 s |
| Mean | 12.61 s |
| p50 | 7.68 s |
| p90 | 39.95 s |
| p95 | 40.13 s |
| Max | 60.05 s |
| Std. dev. | 15.67 s |

Average successful latency by tone:

| Tone | Mean latency |
| --- | --- |
| Professional | 13.88 s |
| Friendly | 15.51 s |
| Assertive | 27.48 s |
| Concise | 9.32 s |

Latency is highly variable (mean 17 s, p90 ~40 s). The 60.05 s outlier was the single request that ended in a 502 after the backend timeout.

## 4. Errors

| Error | Count | Share of all requests |
| --- | --- | --- |
| HTTP 429 (Gemini rate limit / free-tier quota) | 9 | 37.5% |
| HTTP 502 (backend timeout / upstream failure) | 1 | 4.2% |
| **Total** | **10** | **41.7%** |

The dominant failure mode is quota exhaustion, not application logic. Failures began after roughly 15 successful calls in this run, which is consistent with a per-minute or per-day free-tier limit. Failed 429 responses returned in ~250–400 ms, so they do not inflate latency meaningfully.

## 5. Output quality (judged on the 14 successful replies)

| Metric | Result |
| --- | --- |
| Tone accuracy (judge agrees with requested tone) | 71.4% (10 / 14) |
| Relevance to original email | 100% (14 / 14) |
| Scenario keyword coverage | 100% (14 / 14) |
| Subject-line leakage | 0% (0 / 14) |
| Mean judge quality | 4.71 / 5 |
| Replies rated 4 or 5 | 100% (14 / 14) |

Tone accuracy by requested tone:

| Requested tone | Judge agreement | Note |
| --- | --- | --- |
| Professional | 100% (4/4) | Strongest |
| Friendly | 100% (3/3) | Strongest |
| Assertive | 75% (3/4) | One reply graded as professional |
| Concise | 0% (0/3) | Judge labelled short replies as friendly/professional despite 21–31 word outputs |

The `concise` result is a metric limitation as much as a model limitation: the replies were genuinely short (21, 26 and 31 words), but the judge mapped brevity to other tone labels. Judge-based tone accuracy should therefore be read alongside the word-count data, not in isolation.

## 6. Length

| Statistic | Words |
| --- | --- |
| Min | 21 |
| Mean | 59.3 |
| Max | 95 |
| Within 40–180 words | 78.6% (11 / 14) |

Three replies fell below 40 words (all were `concise` or near-concise). No reply was excessively long.

## 7. Interpretation

- **Reliability is the weakest dimension.** A 41.7% error rate, almost entirely 429s, makes the free-tier key unsuitable for bursty or repeated generation. Retry/backoff, quota-aware throttling and a clearer user-facing 429 message are the highest-impact improvements.
- **Latency needs attention.** Even on successful calls, p50 is ~10 s and p90 ~40 s. Streaming responses, caching repeated prompts, and a faster model or smaller prompt would improve perceived speed.
- **Quality on successful calls is good.** Relevance, context retention and format compliance were perfect, with a 4.71/5 mean quality score.
- **Tone control is partially effective.** Professional and friendly are reliable; assertive is mostly reliable; concise produces short text but is not recognised as a distinct tone by the judge. Stronger tone instructions, few-shot examples or tone-specific post-checks would help.
- **End-to-end yield is the honest headline:** only 10 of 24 requests (41.7%) delivered a reply with the requested tone. Reliability and tone fidelity compound.

## 8. Caveats

- Small sample (24 calls) from a single run and a single free-tier key.
- Rate limiting was triggered during the run, so error and latency figures are specific to that quota state.
- LLM-as-judge grading is subjective and can mislabel short or neutral text (see `concise`).
- Keyword coverage is a shallow relevance proxy; it does not prove semantic correctness.
- No cold start, concurrency, or long-form email stress cases were measured.

## 9. Reproducing the measurement

1. Start the backend with a real `GEMINI_API_KEY` in `backend\.env` and run it on port 8080.
2. Send 24 sequential `POST /api/email/generate` calls: 6 fixed emails × 4 tones (`professional`, `friendly`, `assertive`, `concise`).
3. Record HTTP status and client-side elapsed milliseconds for each call.
4. For each HTTP 200 response, send the original email plus reply to a judge model and request JSON with `tone`, `relevant`, `subject_line`, and `quality` (0–5).
5. Compute percentiles on successful calls separately from all calls; report error counts by HTTP status.
