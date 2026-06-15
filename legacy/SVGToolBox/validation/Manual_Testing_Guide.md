# SVG Toolbox Validation Plan

This document outlines how to manually verify that the three core enhancements (UI Responsiveness, Robust Batik Path Parsing, and the Config Builder pattern) are functioning correctly.

---

## Enhancement 1 & 3: UI Responsiveness & Config Builder

### Goal
Prove that the main GUI thread (EDT) is not blocked during intensive SVG processing tasks, and that the new asynchronous `SwingWorker` and `Config.Builder` backend logic successfully updates the preview image and saves files.

### Steps
1. Launch the application:
   ```bash
   ./start_gui.sh
   ```
2. In the "SVG Toolbox" window, click **"Load SVG..."** (or use the equivalent File Load mechanism) to open `example.svg` or an extremely complex/large SVG file of your own.
3. Check the **"Optimize Path Travel"** checkbox (this triggers a heavy \(O(N^2)\) path calculation loop).
4. Click the **"Update Preview"** button.
5. **Observation 1:** The button text must immediately change from "Update Preview" to "Processing..." and become visually disabled.
6. **Observation 2:** While the text says "Processing...", quickly attempt to check/uncheck the "Enable Hatching" box. The UI must remain responsive. You should not see a macOS spinning beachball.
8. **Observation 3:** Wait for processing to complete. Once finished, the button should re-enable and say "Update Preview" again. The main graphic panel must show the processed SVG.
9. Under **Layer Settings**, verify the table has column headers (Layer, Vis, Pattern, Angle, Gap, Width) and alternating row backgrounds.
10. Uncheck the visibility checkbox for one of the layers and click "Update Preview". Verify the layer disappears.
11. Use the stroke width **spinner** (+/- buttons) for a specific layer to set it to 5.0px and change its pattern to "cross". Click "Update Preview". Verify the layer is thicker and cross-hatched, while other layers remain unaffected.
12. Verify that each layer can have a **different** hatch pattern, angle, and gap. Set one layer to "zigzag" at 90 degrees and another to "linear" at 45 degrees. Click "Update Preview" and confirm they render differently.
13. Verify there is **no global settings panel** — only per-layer controls and the "Options" bar (Enable Hatching, Optimize Path Travel).
11. Click **"Save As..."**, pick a destination file (e.g. `test_output.svg`), and verify the exact same asynchronous behavior happens (button disables, UI remains responsive) until you get the "Saved to..." success dialog. Verify the saved file matches what is shown in the preview pane.

---

## Enhancement 2: Robust SVG Path Parsing

### Goal
Prove that the `PathOptimizeProcessor` correctly handles complex, real-world SVG paths (which can contain relative coordinates (`m 10,10`), implied commands, or compressed number formats (`M10-10`)) using the new Apache Batik `PathParser`. The previous naive implementation would crash or mangle the coordinates.

### Steps
1. Open up a terminal in the root directory.
2. We will test the path optimizer directly through the CLI tool, which utilizes the exact same `PathOptimizeProcessor` engine. 
3. Run the following command:
   ```bash
   java -jar target/svgtoolbox-1.0-SNAPSHOT-shaded.jar -i example.svg -o test_optimized.svg -w 1.0 -h --optimize
   ```
4. **Observation 1:** The CLI should output "Optimizing path order..." and complete successfully, ending with "Success: test_optimized.svg". If the naive string-split parser was still active, complex SVG files would likely throw `NumberFormatException`s or array index out of bounds exceptions. The operation must complete silently and successfully.
5. **Observation 2:** Open `test_optimized.svg` in Inkscape/Adobe Illustrator or a web browser. The geometry of the image must look identical to standard hatching. It should not look mangled, exploded, or corrupted, proving that Batik correctly parsed the exact start and end coordinate points of every shape.
