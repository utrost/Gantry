# vectorize

Raster image (JPG/PNG) → SVG. The ported [Vectorize](https://github.com/utrost/vectorize)
(BoofCV-Batik Vectorizer) engine, re-homed under `org.trostheide.gantry.vectorize`
as Gantry's optional **front stage**: it produces an SVG that the existing
`pipeline-core` `SvgImportStage` consumes, opening the full
**image → SVG → process → plot** path.

This module depends on no other Gantry module — removing `vectorize/` still
leaves a fully functional plotter (same priority encoding as `watercolor/`).

## Strategies

`dp`, `line`, `raw`, `convexhull`, `centerline`, `pbn` (Paint-by-Numbers),
`bezier` (DrPTrace outlines), and `bezier2` (ImageTracer colour fills).

## Third-party dependencies that are not on Maven Central

Two tracer libraries are not resolvable from Maven Central and need special
handling — note them before building:

### DrPTrace (`bezier` strategy) — vendored, no action needed

`net.plantabyte:drptrace` / `drptrace-utils` (2.0.0) are vendored under
[`lib/`](lib/) and wired as `system`-scoped dependencies in `pom.xml`. They
build out of the box.

### imagetracerjava (`bezier2` strategy) — JitPack

`com.github.brixomatic:imagetracerjava:1.1.5` is published only via
[JitPack](https://jitpack.io) (declared as a repository in the **root** Gantry
`pom.xml`). The aggregate build therefore needs network access to `jitpack.io`.

> **Restricted/CI environments:** if `jitpack.io` is not reachable (e.g. an
> egress policy returns HTTP 403), the module — and the aggregate
> `mvn package` — cannot resolve this dependency. The fix is either to allow
> `jitpack.io`, vendor the jar under `lib/` the way DrPTrace is vendored, or
> drop the `bezier2`/ImageTracer strategy. See ROADMAP Phase 18 for the
> recorded decision.

## Building

From the Gantry repo root:

```
mvn -pl vectorize -am package
```
