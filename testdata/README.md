# Test data for the manual acceptance suite

These small SVGs back the human-executed scripts in [`../docs/TESTING.md`](../docs/TESTING.md).
They are deliberately tiny and hand-readable so a tester can confirm by eye what
each one should do. Keep them stable — script expectations (command counts,
page-border drops, weld results) are written against these exact files.

| File | What it contains | Used by |
|---|---|---|
| `single-rect.svg` | One `<rect>`, no surrounding border | TS-G1 (lone shape is content, must not be dropped) |
| `simple.svg` | A few lines/path/circle, one colour, no layers | most import/plot scripts (TS-G2, TS-F2, TS-O1, TS-S1, …) |
| `layers2.svg` | Two Inkscape layers, red + blue strokes | TS-H1, TS-N1 |
| `many-segments.svg` | Two chains of end-to-end touching `<line>` segments | TS-L2 (stroke welding) |
| `colours.svg` | Five distinct stroke colours | TS-N1 (colour → station mapping) |
| `framed.svg` | Content **plus** a full-page border rect | TS-G1 (border dropped, inner content kept) |

If you change a file, re-check the scripts that reference it — a different vertex
count or extent can change expected command counts and the page-border result.
