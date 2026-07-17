# Ask answer layer — grounding eval

`gemini-2.5-flash`, temperature 0.1, 1 run(s) per case, live `AskAnswerPrompt.INSTRUCTION`.

**Matchers: 124/124 (100.0%)**
**Judge: 124/124 grounded (100.0%)** — gold-blind panel via `gemini-2.5-flash`

The layer's whole risk is a fluent answer that isn't in the notes. Layer 2 fails any answer
with a number absent from the notes/question; layer 3 (the judge) catches the *non-numeric*
hallucination — a wrong name, a wrong place, an invented link — that reads perfectly.

## By category

| Category | Matchers | What it pins |
|---|---|---|
| `absent` | 14/14 (100.0%) | refuses when the notes don't hold the answer |
| `distract` | 23/23 (100.0%) | picks the right note among near-identical ones |
| `fact` | 17/17 (100.0%) | states the specific detail asked for |
| `fmt` | 8/8 (100.0%) | brief plain prose, no markdown, no listing items back |
| `inj` | 18/18 (100.0%) | treats note text as content, never instructions |
| `multi` | 17/17 (100.0%) | combines two notes without inventing a link |
| `outside` | 10/10 (100.0%) | refuses rather than using world knowledge |
| `partial` | 17/17 (100.0%) | doesn't over-claim from a related-but-silent note |

## Matcher failures

None.
## Judge layer

Gold-blind panel (majority of 3) via `gemini-2.5-flash`. Advisory, not the gate.

- Injection resisted: **18/18** of the cases carrying an injected instruction.

### Matchers PASS but judge says ungrounded  ← highest-value signal

A green matcher hiding a hallucination the deterministic checks can't see.

None — the matchers caught everything the judge did.

### Matchers FAIL but judge says grounded  ← over-strict matcher or bad gold

None.

> **Independence caveat.** `gemini-2.5-pro` is unavailable on the express key, so the judge
> ran on `gemini-2.5-flash` — the same family as the system under test, sharing its blind spots.
> For a genuinely independent read, run the cross-family Claude Workflow `ask_judge.wf.js`
> (feeds `--judge-in`) or pass `--judge-model` a stronger model on a fuller key.

