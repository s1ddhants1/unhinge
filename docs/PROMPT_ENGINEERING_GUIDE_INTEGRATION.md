# DAIR.AI Prompt Engineering Guide — Unhinge Integration Analysis

This document maps the local DAIR.AI Prompt Engineering Guide repository
(`Prompt-Engineering-Guide/`) to Unhinge's AI Prompt Wingman implementation.
It distinguishes techniques that are already useful in Unhinge from techniques
that are unnecessary, incomplete, or currently overclaimed.

## Executive Summary

Unhinge already applies several sound prompt-engineering fundamentals:

- explicit instructions, context, input data, and output format;
- few-shot examples for style calibration;
- structured delimiters around candidate data;
- configurable temperature, top-p, and reasoning effort;
- manual directional style controls;
- structured JSON output with provider-specific parsing and fallbacks.

The main gap is not a lack of prompting techniques. It is the lack of a
repeatable evaluation and context-engineering loop. The current implementation
places many stylistic constraints in one large template and relies mostly on
model compliance. It needs stronger separation between data and instructions,
better output validation, smaller privacy-relevant context, and measured
iteration across models.

The guide should therefore be used as an engineering and evaluation framework,
not as a checklist of advanced techniques to apply wholesale.

## Guide Principles That Apply Directly

### Prompt elements

The guide identifies four useful prompt elements: instruction, context, input
data, and output indicator. See
[`elements.en.mdx`](../Prompt-Engineering-Guide/pages/introduction/elements.en.mdx).

Unhinge maps to this structure cleanly:

| Guide element | Unhinge implementation |
| --- | --- |
| Instruction | Generate a tailored Hinge opener or answer the user's wingman query |
| Context | Candidate profile, prompt history, selected vibe, and custom settings |
| Input data | Candidate prompt answers or the user's Ask AI query |
| Output indicator | `{"lines": [...]}` for opener generation, streamed text for Ask AI |

This separation is implemented through [`WingmanPrompts.kt`](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/WingmanPrompts.kt)
and [`PromptRepository.kt`](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/PromptRepository.kt).

### Few-shot prompting

The guide recommends representative demonstrations and warns that example
format, ordering, and distribution influence model behavior. See
[`fewshot.en.mdx`](../Prompt-Engineering-Guide/pages/techniques/fewshot.en.mdx).

Unhinge's opener template uses contrastive `BAD` and `GOOD` examples to teach
the desired tone and common failure modes. This is one of the strongest parts
of the current design.

The examples should eventually be expanded across:

- short, medium, and story-length answers;
- different Hinge prompt categories;
- direct, sincere, absurd, and low-energy tones;
- different cultures, dialects, and levels of English fluency;
- cases where teasing is inappropriate or likely to be misread.

### Structured inputs and outputs

The guide recommends explicit structure, delimiters, and output formats. See
[`optimizing-prompts.en.mdx`](../Prompt-Engineering-Guide/pages/guides/optimizing-prompts.en.mdx).

Unhinge uses `<candidate_context>` and requests a JSON object containing a
`lines` array. Provider adapters also have parsing fallbacks for models that
return bare arrays, markdown, or plain text.

This is useful, but parsing currently guarantees mostly structural validity,
not semantic quality. See [Output validation](#output-validation).

### Sampling controls

The guide discusses temperature, top-p, maximum output length, stop sequences,
and repetition penalties. See
[`settings.en.mdx`](../Prompt-Engineering-Guide/pages/introduction/settings.en.mdx).

Unhinge exposes temperature, top-p, and reasoning effort in settings. For
short creative openers, temperature is the primary diversity control. The
project should avoid tuning temperature and top-p simultaneously without an
evaluation reason, and should use provider-supported output-token limits where
available instead of relying only on a word-count instruction.

## Techniques That Need Careful Interpretation

### Directional Stimulus Prompting

The guide's DSP page describes a learned policy model that generates a
stimulus or hint for another frozen model. See
[`dsp.en.mdx`](../Prompt-Engineering-Guide/pages/techniques/dsp.en.mdx).

Unhinge does not implement that research technique literally. Its vibe chips
inject manually selected labels such as `Playful Tease` and `Micro-Debate` into
the prompt. This is better described as **manual directional style
conditioning**. It is still a useful product feature, but project
documentation should not claim that Unhinge has implemented learned DSP.

### Chain-of-thought

The guide presents chain-of-thought as a technique for tasks requiring
multi-step logical reasoning. See
[`cot.en.mdx`](../Prompt-Engineering-Guide/pages/techniques/cot.en.mdx).

Visible chain-of-thought is not appropriate for Hinge openers. The task is
creative and short, and exposing reasoning would make the result less natural.
For Ask AI analysis, provider-level reasoning controls may be useful, but the
application should request only the final answer. A prompt instruction such as
“keep thinking brief” is an output-style constraint, not a reliable control of
hidden model computation. The actual `reasoning_effort` API parameter is the
stronger control when the provider supports it.

### Self-consistency

The guide's self-consistency method samples multiple reasoning paths and uses
agreement to select an answer. See
[`consistency.en.mdx`](../Prompt-Engineering-Guide/pages/techniques/consistency.en.mdx).

Majority voting is a poor fit for creative openers: the most common line is
not necessarily the most natural or interesting one. A better adaptation would
be to generate several diverse candidates and rank them against a small rubric
for relevance, naturalness, cliché avoidance, and prompt alignment.

Unhinge currently generates one line per prompt and obtains alternatives only
through regeneration. It does not rank or critique candidates.

### Prompt chaining

The guide recommends decomposing complex tasks into smaller prompt operations.
See [`prompt_chaining.en.mdx`](../Prompt-Engineering-Guide/pages/techniques/prompt_chaining.en.mdx).

The opener path is simple enough to remain a single call. Ask AI is more
complex: it can interpret a profile, identify relevant details, suggest a
strategy, and draft a message in one call. If evaluation shows inconsistent
results, it could be split into:

1. relevant-signal extraction;
2. angle or strategy selection;
3. message generation;
4. quality validation.

This should be introduced only if the quality gain justifies additional
latency, network calls, and profile-data exposure.

### RAG and ReAct

The current wingman feature does not need RAG or ReAct. The candidate profile
is small, local, and directly available from Hinge's SQLite database. That is
structured context, not a retrieval-heavy knowledge task. ReAct is also not
appropriate for generating a short conversational opener.

RAG could become relevant later if Unhinge adds a large personal memory of
previous conversations, notes, or user-specific writing preferences. It is not
necessary for the current candidate dossier.

## Context Engineering Implications

The guide's newer context-engineering material emphasizes layered context,
explicit expectations, error handling, observability, validation, and iterative
refinement. See [`context-engineering.en.mdx`](../Prompt-Engineering-Guide/pages/agents/context-engineering.en.mdx).

Unhinge currently has these layers conceptually:

1. **System layer:** wingman identity and global behavior;
2. **Task layer:** opener or Ask AI instructions;
3. **Data layer:** candidate profile and prompt answers;
4. **History layer:** previously generated replies and in-memory cache;
5. **Transport layer:** provider-specific schemas, streaming, retries, and
   reasoning settings.

The architecture is sound, but the boundaries are not fully enforced. In
particular:

- candidate text, previous replies, and directional stimulus are interpolated
  into the same user prompt block;
- candidate text is not escaped before being wrapped in XML-like tags;
- delimiters provide organization but are not a security boundary;
- the prompt template contains many overlapping negative constraints;
- there is no formal prompt-quality telemetry or regression set.

The guide also warns against over-constraint. Unhinge's opener template has a
large catalogue of banned words, sentence structures, slang, questions, and
openings in [`opener_template.md`](../app/src/main/assets/prompts/opener_template.md).
This may reduce familiar AI clichés, but it can also make replies stiff or
prevent legitimate language. A smaller set of high-value exclusions plus
strong positive examples should be tested against the current template.

## Security and Privacy Findings

The guide's adversarial prompting material recommends separating instructions
from untrusted input, quoting or parameterizing data, and assuming prompt
injection defenses are brittle. See
[`adversarial.en.mdx`](../Prompt-Engineering-Guide/pages/risks/adversarial.en.mdx).

Unhinge's `<candidate_context>` warning is a useful first layer, but it does
not prevent a profile answer from containing instruction-like text or closing
the delimiter. Candidate fields should be serialized or escaped, and the
directional stimulus should be kept outside the candidate-data enclosure.

The guide's bias material is also relevant. Few-shot examples can bias output
through their distribution and ordering. See
[`biases.en.mdx`](../Prompt-Engineering-Guide/pages/risks/biases.en.mdx).
Unhinge's examples currently favor one English-language, dry-internet-humor
voice. This should not be treated as a universal dating style.

Privacy minimization is not addressed sufficiently by the current prompting
design. Opener generation sends more profile context than the task usually
needs, including work, school, location, hometown, dating intention, habits,
and pet information. The model may be instructed not to use unrelated fields,
but transmitting them still creates unnecessary disclosure. The opener path
should prefer the selected prompt plus only the minimum supporting context.

## Correctness and Runtime Gaps

### Prompt count is derived from physical lines

`HostAppAiSheetContent.kt` converts each prompt answer into `PromptEntry.text`
and preserves embedded newlines. `PromptRepository.formatOpenerUserPrompt`
then calculates `{lineCount}` using `promptsText.lines().size`.

That means a multiline answer can make a three-card profile appear to contain
four or more prompts. The parser then truncates or pads to the incorrect count
in [`OpenRouterService.kt`](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/OpenRouterService.kt).
The count should come from the number of prompt entries, not the number of
rendered text lines.

### Output validation is incomplete

The parser handles JSON and common formatting failures, but it does not verify:

- relevance to the specific prompt;
- banned phrase usage;
- word or character limits;
- duplicate or near-duplicate wording;
- number of questions;
- topical isolation;
- unsafe or inappropriate teasing.

The guide's evaluation and context-validation principles imply that these
requirements should be measured separately from transport parsing.

### Custom and remote templates are powerful overrides

The custom opener template replaces the default template rather than extending
it. Manual remote synchronization also stores downloaded text as a custom
template. This is flexible, but it can remove all default behavior and safety
constraints without schema validation, versioning, rollback, or quality tests.

## Recommended Implementation Priorities

### P0 — Correctness and data boundaries

1. Derive output count from prompt-entry count.
2. Serialize or escape candidate data before inserting it into the prompt.
3. Keep directional stimulus and other control data outside the candidate-data
   enclosure.
4. Reduce opener context to the selected prompt and minimal supporting fields.

### P1 — Quality and evaluation

1. Create a representative offline prompt corpus.
2. Evaluate relevance, naturalness, cliché rate, diversity, and topical
   isolation across supported providers and models.
3. Add candidate generation plus rubric-based ranking for regeneration or
   multi-option output.
4. Track model, prompt-template version, settings, parse result, regeneration,
   and copy behavior so prompt changes can be compared.

### P2 — Prompt maintainability

1. Replace some negative rules with positive behavioral instructions.
2. Expand few-shot examples across languages, cultures, prompt types, and
   answer lengths.
3. Validate custom and remote templates before activation.
4. Version the prompt assets and keep the repository copy synchronized with
   `app/src/main/assets/prompts/`.

## Final Position

The most valuable DAIR.AI lessons for Unhinge are:

- make the task and output contract explicit;
- use representative few-shot demonstrations;
- separate instructions from data;
- decompose only when the task is genuinely complex;
- control sampling deliberately;
- validate behavior empirically;
- iterate from observed failures rather than continually adding rules.

The project should not add chain-of-thought, ReAct, RAG, or literal DSP merely
because those techniques appear in the guide. For Unhinge, a smaller,
well-evaluated prompt with clean context boundaries and a quality-ranking layer
is more appropriate than a larger prompt containing every available technique.
