# Frozen operation lists (D47)

Recorded 24 Nov 2026 from `generate --list-only` on the owner's machine (branch `t6-gen`). Rule: section = first path
segment; largest-remainder shares; SHA-256-of-key order within a section (`OperationSelector`). The SHA-256 is of the
sorted keys joined by newlines; the assistant re-computed it from the lists below and it matches. These selections are
not changed on the basis of any observed result. Held-out projects use all operations, so only the count and SHA-256
are recorded here.

| Project | Spec | Operations | List SHA-256 | Used for |
|---|---|---|---|---|
| Dolibarr | R14 at `9d4007e` (`dolibarr_r14_9d4007e_swagger.json`) | 20 of 392 | `0f4cb5c43b50bbea3c16cf60b0bf872fd59767bb60e06547a4e6fa88194d4166` | model test (G2); list: `dolibarr_cap20.txt` |
| Dolibarr | same | 60 of 392 | `6cb955345720c0838618ea5c00885bf36a775c1d65c3b03027b0497522a8a25f` | Day 5 development runs; list: `dolibarr_cap60.txt` |
| RESTCountries | `restcountries#235_spec.json` | 10 of 10 | `16d82241ce3662e2b1f80a93cdb21abe9dc17704de387c21cff8caa54e6a7731` | model test, development runs |
| Podman (held-out) | `podman#13986_spec.yaml` (D5.1 preference 2) | 165 of 165 | `15d58cec3c2a436c3dde452ceb23103e33621ec0408458faf4378b322e03891c` | held-out generation |
| nocodb (held-out) | official spec at `cea2b4ad7348` | 126 of 126 | `095f71ce6db0e538cf0a69e13ae7e8d64cffb632eb796b9613227706c8c7e9fa` | held-out generation |
