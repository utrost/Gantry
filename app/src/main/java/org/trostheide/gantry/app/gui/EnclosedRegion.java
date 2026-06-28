package org.trostheide.gantry.app.gui;

import java.awt.geom.Path2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Detects the area enclosing a clicked point when its boundary is formed by several <em>separate</em>
 * strokes (so no single closed path encloses it) — the case {@link VisualizationPanel#findClosedRegionAt}
 * can't catch. It rasterizes all strokes as walls, flood-fills from the seed, and traces the filled
 * blob's boundary into a polygon (model mm space) that the normal hatch pipeline can fill.
 *
 * <p>Pure and Swing-free for testability. First cut: a single connected region, outer boundary only
 * (interior holes are not carved out); a seed whose fill leaks to the raster border is treated as
 * "not enclosed" and yields {@code null}.
 */
final class EnclosedRegion {

    private EnclosedRegion() {
    }

    /** Raster long-side resolution (cells). Trades boundary precision against flood-fill cost. */
    private static final int TARGET = 600;
    /** Empty margin (cells) around the content so an un-enclosed fill reaches the border to escape. */
    private static final int BORDER = 2;
    /** Half-thickness (cells) painted along each stroke so near-touching strokes seal the boundary. */
    private static final int WALL = 1;

    /**
     * The enclosed area around {@code (seedX, seedY)} as a closed path in model mm space, or
     * {@code null} if the strokes don't enclose the seed (the fill escapes), or the input is
     * degenerate.
     *
     * @param strokes each stroke as an {@code [n][2]} array of model-space points
     */
    static Path2D fromSeed(List<double[][]> strokes, double seedX, double seedY) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        boolean any = false;
        for (double[][] s : strokes) {
            for (double[] p : s) {
                any = true;
                minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
                minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
            }
        }
        // Include the seed so a click slightly outside the strokes' bbox still rasterizes in-grid.
        minX = Math.min(minX, seedX); maxX = Math.max(maxX, seedX);
        minY = Math.min(minY, seedY); maxY = Math.max(maxY, seedY);
        double w = maxX - minX, h = maxY - minY;
        if (!any || w <= 0 || h <= 0) {
            return null;
        }

        double scale = TARGET / Math.max(w, h);
        int gw = (int) Math.ceil(w * scale) + 2 * BORDER + 1;
        int gh = (int) Math.ceil(h * scale) + 2 * BORDER + 1;

        boolean[][] wall = new boolean[gh][gw];
        for (double[][] s : strokes) {
            for (int i = 0; i + 1 < s.length; i++) {
                rasterLine(wall, gw, gh,
                        cx(s[i][0], minX, scale), cy(s[i][1], minY, scale),
                        cx(s[i + 1][0], minX, scale), cy(s[i + 1][1], minY, scale));
            }
        }

        int sc = cx(seedX, minX, scale);
        int sr = cy(seedY, minY, scale);
        int[] seed = nearestOpenCell(wall, gw, gh, sc, sr);
        if (seed == null) {
            return null;
        }

        boolean[][] fill = new boolean[gh][gw];
        if (!floodFill(wall, fill, gw, gh, seed[0], seed[1])) {
            return null; // escaped to the border ⇒ not enclosed
        }

        List<int[]> contour = traceBoundary(fill, gw, gh);
        if (contour.size() < 3) {
            return null;
        }

        Path2D path = new Path2D.Double();
        boolean first = true;
        int[] prevDir = {Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int k = 0; k < contour.size(); k++) {
            int[] cell = contour.get(k);
            // Decimate: keep only vertices where the step direction changes (corners).
            int[] next = contour.get((k + 1) % contour.size());
            int dx = Integer.signum(next[0] - cell[0]);
            int dy = Integer.signum(next[1] - cell[1]);
            if (!first && dx == prevDir[0] && dy == prevDir[1]) {
                prevDir[0] = dx; prevDir[1] = dy;
                continue;
            }
            prevDir[0] = dx; prevDir[1] = dy;
            double mx = minX + (cell[0] - BORDER + 0.5) / scale;
            double my = minY + (cell[1] - BORDER + 0.5) / scale;
            if (first) {
                path.moveTo(mx, my);
                first = false;
            } else {
                path.lineTo(mx, my);
            }
        }
        path.closePath();
        return path;
    }

    private static int cx(double x, double minX, double scale) {
        return BORDER + (int) Math.round((x - minX) * scale);
    }

    private static int cy(double y, double minY, double scale) {
        return BORDER + (int) Math.round((y - minY) * scale);
    }

    /** Bresenham line with a square brush of half-width {@link #WALL}. */
    private static void rasterLine(boolean[][] wall, int gw, int gh, int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            brush(wall, gw, gh, x0, y0);
            if (x0 == x1 && y0 == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    private static void brush(boolean[][] wall, int gw, int gh, int x, int y) {
        for (int yy = y - WALL; yy <= y + WALL; yy++) {
            for (int xx = x - WALL; xx <= x + WALL; xx++) {
                if (xx >= 0 && xx < gw && yy >= 0 && yy < gh) {
                    wall[yy][xx] = true;
                }
            }
        }
    }

    /** A non-wall cell at or near {@code (sc, sr)}; null if the seed is buried in walls. */
    private static int[] nearestOpenCell(boolean[][] wall, int gw, int gh, int sc, int sr) {
        if (sc < 0 || sc >= gw || sr < 0 || sr >= gh) {
            return null;
        }
        if (!wall[sr][sc]) {
            return new int[]{sc, sr};
        }
        for (int rad = 1; rad <= 4; rad++) {
            for (int yy = sr - rad; yy <= sr + rad; yy++) {
                for (int xx = sc - rad; xx <= sc + rad; xx++) {
                    if (xx >= 0 && xx < gw && yy >= 0 && yy < gh && !wall[yy][xx]) {
                        return new int[]{xx, yy};
                    }
                }
            }
        }
        return null;
    }

    /** Flood-fills open cells from the seed; returns false if the fill reaches the grid border. */
    private static boolean floodFill(boolean[][] wall, boolean[][] fill, int gw, int gh, int sx, int sy) {
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{sx, sy});
        boolean enclosed = true;
        while (!stack.isEmpty()) {
            int[] c = stack.pop();
            int x = c[0], y = c[1];
            if (x < 0 || x >= gw || y < 0 || y >= gh || wall[y][x] || fill[y][x]) {
                continue;
            }
            fill[y][x] = true;
            if (x == 0 || y == 0 || x == gw - 1 || y == gh - 1) {
                enclosed = false; // touched the border — keep filling to finish, but it's not enclosed
            }
            stack.push(new int[]{x + 1, y});
            stack.push(new int[]{x - 1, y});
            stack.push(new int[]{x, y + 1});
            stack.push(new int[]{x, y - 1});
        }
        return enclosed;
    }

    // Moore-neighbourhood offsets, clockwise: E, SE, S, SW, W, NW, N, NE.
    private static final int[] DX = {1, 1, 0, -1, -1, -1, 0, 1};
    private static final int[] DY = {0, 1, 1, 1, 0, -1, -1, -1};

    /** Moore-neighbour boundary trace of the filled region's outer contour (ordered cells). */
    private static List<int[]> traceBoundary(boolean[][] fill, int gw, int gh) {
        int sx = -1, sy = -1;
        outer:
        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                if (fill[y][x]) {
                    sx = x; sy = y;
                    break outer;
                }
            }
        }
        List<int[]> contour = new ArrayList<>();
        if (sx < 0) {
            return contour;
        }
        contour.add(new int[]{sx, sy});
        // We arrived at the start from the West (the cell to its left is background by the scan order).
        int bx = sx - 1, by = sy;
        int cxC = sx, cyC = sy;
        int guard = 0, max = 8 * gw * gh;
        while (guard++ < max) {
            int from = dirIndex(bx - cxC, by - cyC);
            boolean found = false;
            for (int i = 1; i <= 8; i++) {
                int nd = (from + i) % 8;
                int nx = cxC + DX[nd], ny = cyC + DY[nd];
                if (nx >= 0 && nx < gw && ny >= 0 && ny < gh && fill[ny][nx]) {
                    int pd = (from + i - 1) % 8;
                    bx = cxC + DX[pd]; by = cyC + DY[pd];
                    cxC = nx; cyC = ny;
                    found = true;
                    break;
                }
            }
            if (!found) {
                break; // isolated cell
            }
            if (cxC == sx && cyC == sy) {
                break; // closed the loop
            }
            contour.add(new int[]{cxC, cyC});
        }
        return contour;
    }

    /** Index into {@link #DX}/{@link #DY} for a unit step {@code (dx, dy)} in {-1,0,1}. */
    private static int dirIndex(int dx, int dy) {
        dx = Integer.signum(dx);
        dy = Integer.signum(dy);
        for (int i = 0; i < 8; i++) {
            if (DX[i] == dx && DY[i] == dy) {
                return i;
            }
        }
        return 0;
    }
}
