# Documentation audit — 2026-09-26

Reviewed the repository's Markdown guides against the current RC2 implementation,
release evidence, profile behavior and running UI. The audit found stale active
status descriptions and an older welcome screenshot; these have been updated.

| Documents | Status and maintenance rule |
|---|---|
| README, FIRST_PLOT, USER_GUIDE, SAMPLE_GALLERY | Current entry points and refreshed UI images; installer candidate remains unpublished |
| ROADMAP, PROJECT_DIGEST, ADOPTION_ROADMAP | Reconciled with closed usability milestone, implemented composition, native installer evidence and remaining adoption work |
| TESTING, NOVICE_STUDY | Include installed-runtime/upgrade checks and explicit isolated-profile instructions |
| ARCHITECTURE, REFACTORING | Updated persistence/composition descriptions; removed misleading current size/test counts |
| RELEASE_CHECKLIST, release-results/1.0.0-rc.2.md | Current release procedure and evidence; human desktop/hardware gates remain open |
| RELEASE_ASSESSMENT | Historical pre-implementation baseline, now explicitly labeled |
| ROADMAP_HISTORY, dated test-results, earlier release notes/results | Historical records: their old counts and pending work describe those revisions, not today's status |
| TRACER_CAPTURE_IMPORT | Future design contract, not an implemented import workflow |
| KNOWN_GOOD_SETUPS, novice-study templates | Evidence collection remains incomplete; do not turn pending checks into claimed results |

The [RC2 acceptance record](../release-results/1.0.0-rc.2.md) is the authoritative
summary of current validation. The [screenshot gallery](images/README.md) records
capture provenance and reproduction instructions. A demo video, external novice
study and complete hardware acceptance remain outstanding.

This audit checks documentation consistency and selected executable contracts;
it does not claim every manual procedure has been performed on physical hardware.
