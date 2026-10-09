# Commands, similar tracks and gradual mood transitions

## Release progression
V1: structured metadata and deterministic ranking.
V2: local preference signals and cached acoustic features.
V3: optional licensed tiny classifier and mood journeys.

## Metadata and uncertainty
Separate language, genre, lyrical theme, emotional mood and acoustic energy. Store multiple labels, provenance and confidence. A fast track can be sad; a quiet track can be happy.
Language cannot be established from script alone, especially for Romanized Hindi/Punjabi.
User corrections override inferred fields and survive reanalysis.

## Command parser
Input → normalize aliases → parse intent → validate constraints → query candidates → rank → preview → create session queue.

Examples:
- Hindi sad love songs for 40 minutes.
- Punjabi mellow songs, no rap.
- Like this song, becoming happier over eight tracks.
- Romantic Hindi songs before 2015.

PlaylistQuery contains languages, required/preferred labels, exclusions, duration or count, seed references, diversity constraints, strictness and journey parameters.
A request can include both a duration and a count only if their joint meaning is defined. Contradictions show a clarification instead of silently dropping constraints.

Do not use an LLM to execute arbitrary queries. Optional language classification produces the same validated object.

## Temporary playlist semantics
Generated queue has session identity, query and generation time. Persist for crash recovery. Save to library only on explicit action. Manual additions are pinned and cannot be displaced by re-ranking.

Strict mode only uses explicit/user-verified labels. Inferred mode discloses estimated matches. Zero matches offers relaxation; it does not silently change Hindi to another language.

## Similarity baseline
Filter hard constraints first. Score available features:
metadata agreement + listening preference + acoustic similarity + journey proximity − repeat penalties − artist concentration.

Weights are versioned configuration. Normalize over known features so absent mood does not become “neutral.” Avoid same-song duplicates and near-identical recordings. Explain selection with concrete matched fields.

For ordinary libraries, linear vector scoring may be sufficient; benchmark 10,000 tracks before adding a vector index.

## Acoustic analyzer candidate
Take several bounded excerpts, decode to analysis PCM, compute features, then optionally infer a log-mel model.
Candidate outputs: tempo estimate, energy, timbre embedding and multi-label mood/genre estimates.
No model weights are selected. Gate on weight license, architecture portability, preprocessing requirements and multilingual evaluation.

Proposed model budget: 5–20 MB; optional installation; CPU-first; bounded concurrency. Quantization can affect quality. “Tiny” is not proof of accuracy.

Schedule import/idle analysis with cancellation and charging preferences. Reuse native decoding carefully without interrupting playback. Feature extraction must not run synchronously at track transition.

## Mood journey
Define starting profile, target profile, track count and hard constraints. Interpolate a desired profile across the route, then use short lookahead to choose candidates with bounded neighboring distance.
Score emotional mood and energy separately. The simplest interpolation is a baseline, not a validated psychological model.

If bridge songs are missing, report the gap and offer more steps or looser constraints. Never rewrite user-pinned tracks to force a journey.

## Evaluation
Human-reviewed Hindi, Punjabi and English subsets with varied genre, tempo and emotional content; language balance and label provenance recorded.
Evaluate similarity preference, route continuity and mood accuracy separately.
Ablations: tags only; tags/history; tags/acoustics; optional model.
Report skip rates carefully: calls, seeking and interruptions are not automatic dislike signals.
