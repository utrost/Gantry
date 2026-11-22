# Firmware, placement and reviewed restart evidence — 2026-11-22

Actual Swing captures from `RoadmapAcceptance`, using the production GRBL backend
with the in-memory serial simulator, an isolated 100 × 100 mm profile and no
physical serial port. The seed and recovery phases run in separate JVMs.

Capture JAR SHA-256: `f0606b950a48efc406f1bc4b2cc2d8b8f4c0aa741a09390ce24186f1d5cbbc9b`.
The exact-head CI reruns the scripted flow on Java 17 and 21; its links are recorded
in the integration PR.

- [Anchor placement](anchor.png): center reference retained through resize/rotation.
- [Compact placement](anchor-compact.png): actual 1024 × 768 window.
- [Firmware review](firmware.png): read-only limits, unchanged profile, explicit
  machine-coordinate convention and reference confirmation.
- [Calibration review](calibration-review.png): reference review remains reachable
  inside the modal calibration wizard, including Cancel and a reviewed raw-axis jog.
- [Uncertain reverse proposal](uncertain-reverse.png): mid-stroke Stop, submitted
  versus completed progress, selected start and muted skipped paths; Cancel saves
  the proposal without preparing or starting motion.
- [Reviewed forward selection](reviewed-forward.png): the second process restores
  the saved selection, then explicitly changes direction and confirms registration,
  pens/passes and uncertainty.
- [Partial completion](partial-complete.png): only retained strokes execute;
  skipped work is not reported as controller-confirmed.

These captures supplement the existing dated gallery; they do not replace its
provenance. Virtual-display layout and simulated coordinates do not establish
native desktop scaling, physical reference, paper registration, ink or endstops.
See the [verification record](../../release-results/2026-11-22-firmware-anchors-restart.md).
