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

## Third-party tracers not on Maven Central

The two whole-image tracer libraries are not on Maven Central. Both are
vendored so the module builds offline with no extra repositories — there is
**nothing to install**:

### DrPTrace (`bezier` strategy) — vendored JARs

`net.plantabyte:drptrace` / `drptrace-utils` (2.0.0) are vendored under
[`lib/`](lib/) and wired as `system`-scoped dependencies in `pom.xml`.

### ImageTracer (`bezier2` strategy) — vendored source

The `bezier2` colour-fill strategy uses
[`jankovicsandras/imagetracerjava`](https://github.com/jankovicsandras/imagetracerjava),
a single public-domain (The Unlicense) source file. Rather than depend on the
JitPack repackage (`com.github.brixomatic:imagetracerjava`, which is not
reliably reachable from restricted/CI networks), the upstream
`ImageTracer.java` is carried in-tree at
[`src/main/java/jankovicsandras/imagetracer/ImageTracer.java`](src/main/java/jankovicsandras/imagetracer/ImageTracer.java),
unmodified. The fork-only `getPalette()`/`SVGUtils` helpers map onto the
upstream `imageToSVG(image, options, null)` entry point (which builds the
palette internally), so the strategy keeps full parity with no JitPack
dependency.

## Building

From the Gantry repo root:

```
mvn -pl vectorize -am package
```
