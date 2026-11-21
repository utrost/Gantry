# Documentation status — 2026-11-21

RC11 remains the public download. RC12 is an immutable unpublished draft at
`8dcb772`; later source fixes are absent from both sets of artifacts. Current
guides, screenshots and offline-help source describe development behavior.
See [RC12 artifact identity](release-results/1.0.0-rc.12.md) and the
[current software/audit record](release-results/2026-11-21-prepared-route.md).

| Area | Current state |
|---|---|
| Repository-wide references | All Markdown local file/image/heading links checked offline; CI now repeats the check |
| User and offline guides | Versioned upright profiles, explicit migration, complete controller-start preview, prepared-route estimates and timing limits |
| First plot and support | Source-vs-release distinction, migration troubleshooting, estimate discrepancies |
| Developer reference | Actual command classes, shared plot-based estimation, controller-space starts, compatibility and regression coverage |
| Roadmap and adoption | Delivered software separated from native/hardware/participant acceptance and deferred research |
| Gallery | All 47 actual Swing PNGs regenerated 2026-11-21 from this development JAR; [provenance](images/README.md) |
| Walkthrough | New 60-second/six-state mock video from the same JAR; no physical movement |
| Historical evidence | Dated failures/counts retained; original RC12 image links pinned to immutable tag |
| Packaging | Existing payload checks still validate installed offline links, screenshots and samples; older release assets unchanged |

The initial scan covered 72 Markdown files and 286 local references; the final
scan includes the new audit/evidence pages. `python3 scripts/check_doc_links.py`
reports the exact current count. External URL availability is not checked by that
offline script. Twelve Python regressions include checker and packaging coverage.

[Upright-profile verification](release-results/2026-11-21-upright-profiles.md)
records preserved legacy output and explicit opt-in. [Prepared-route follow-up](release-results/2026-11-21-prepared-route.md)
records the later complete preview and time-estimate fixes. The [backend contract](BACKEND_CONTRACT.md)
qualifies mock/fake-serial evidence; neither establishes firmware or physical fidelity.

The [operator checklist](OPERATOR_ACCEPTANCE.md) still requires a new candidate
for #93 direction/frame/plot checks, current manual Stop/recovery #39, actual
native scaling #43, operator benchmark #54, a real artwork task #72 and novice
participants #16. #33 stays open for its acceptance dependencies; #71 remains
deferred pending physical ink/drying assumptions. A full human walkthrough of
every guide instruction remains uncollected. Documentation refresh and CI passes
do not replace those observations or authorize a stable-readiness claim.
