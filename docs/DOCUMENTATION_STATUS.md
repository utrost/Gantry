# Documentation status — 2026-11-21

RC13 is a public testing prerelease containing the coordinate/preview/estimate fixes and
matching guides/gallery. Its exact-tag builds and all nine assets are verified. RC12 remains
an immutable unpublished draft at `8dcb772`; its assets and older public releases
are unchanged. See [RC13 candidate evidence](release-results/1.0.0-rc.13.md),
[RC12 historical identity](release-results/1.0.0-rc.12.md) and the
[current software/audit record](release-results/2026-11-21-prepared-route.md).

Later [watercolor/station/homing fixes](release-results/2026-11-21-watercolor-homing.md)
are development source changes, absent from the immutable RC13 downloads. Current
guides describe these corrected paths; preserve the release/source distinction.
The [completion/simulator audit](release-results/2026-11-21-completion-serial-simulator.md)
adds #111/#112 evidence, supported serial subset and physical boundaries. Settings,
offline help, testing, backend contract and active roadmap describe these source additions.

| Area | Current state |
|---|---|
| Repository-wide references | All Markdown local file/image/heading links checked offline; CI now repeats the check |
| User and offline guides | Versioned upright profiles, explicit migration, complete controller-start preview, known initial-position rejection, prepared-route estimates and timing limits |
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

The [operator checklist](OPERATOR_ACCEPTANCE.md) uses the RC13 worksheet
for #93 direction/frame/plot checks, current manual Stop/recovery #39, actual
native scaling #43, operator benchmark #54, a real artwork task #72 and novice
participants #16. #33 stays open for its acceptance dependencies; #71 remains
deferred pending physical ink/drying assumptions. A full human walkthrough of
every guide instruction remains uncollected. Documentation refresh and CI passes
do not replace those observations or authorize a stable-readiness claim.
