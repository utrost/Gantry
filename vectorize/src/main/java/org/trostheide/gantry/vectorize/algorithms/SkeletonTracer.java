package org.trostheide.gantry.vectorize.algorithms;

import boofcv.struct.image.GrayU8;
import georegression.struct.point.Point2D_I32;

import java.util.ArrayList;
import java.util.List;

/**
 * Traces a 1-pixel wide skeleton (binary image) into a set of polylines.
 * Assumes the input image has already been skeletonized/thinned.
 */
public class SkeletonTracer {

    private static final int BG = 0;
    private static final int FG = 1;

    /**
     * Trace the skeleton.
     * 
     * @param input Binary image (0=background, 1=foreground/skeleton)
     * @return List of polylines (lists of points)
     */
    public List<List<Point2D_I32>> trace(GrayU8 input) {
        List<List<Point2D_I32>> polylines = new ArrayList<>();
        int w = input.getWidth();
        int h = input.getHeight();
        boolean[][] visited = new boolean[w][h];

        // 1. Identify Nodes (Endpoints and Junctions)
        // We don't necessarily need to pre-store them, but it helps for structured
        // graph walking.
        // However, a greedy approach starting from endpoints works well for "strokes".

        // Scan for endpoints first (1 neighbor)
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (input.get(x, y) == FG && !visited[x][y]) {
                    if (countNeighbors(input, x, y) == 1) {
                        // Found an endpoint. Start a stroke.
                        List<Point2D_I32> stroke = traceLine(input, visited, x, y);
                        if (stroke.size() > 1) {
                            polylines.add(stroke);
                        }
                    }
                }
            }
        }

        // Scan for remaining unvisited visible pixels (Loops or internal segments)
        // This catches closed loops (no endpoints) or segments between junctions if we
        // stopped at junctions.
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (input.get(x, y) == FG && !visited[x][y]) {
                    // Start a trace here. It's likely a loop or a segment between junctions.
                    List<Point2D_I32> stroke = traceLine(input, visited, x, y);
                    if (stroke.size() > 1) {
                        polylines.add(stroke);
                    }
                }
            }
        }

        return polylines;
    }

    private List<Point2D_I32> traceLine(GrayU8 input, boolean[][] visited, int startX, int startY) {
        List<Point2D_I32> points = new ArrayList<>();
        int cx = startX;
        int cy = startY;

        // Add start point
        points.add(new Point2D_I32(cx, cy));
        visited[cx][cy] = true;

        while (true) {
            // Look for an unvisited neighbor
            Point2D_I32 next = findUnvisitedNeighbor(input, visited, cx, cy);

            if (next == null) {
                // No unvisited neighbors. Check for visited neighbors that are NOT the previous
                // point.
                // This closes loops or connects to junctions.
                // For a simple centerline tracer, stopping here is often acceptable,
                // but we might want to "connect" to the junction.
                // Let's see if we are adjacent to a visited node that isn't our immediate
                // predecessor.
                Point2D_I32 junction = findVisitedNeighbor(input, cx, cy, points);
                if (junction != null) {
                    // We hit a junction or looped back. Connect and stop.
                    points.add(junction);
                }
                break;
            }

            // Move to next
            cx = next.x;
            cy = next.y;
            points.add(new Point2D_I32(cx, cy));
            visited[cx][cy] = true;

            // Heuristic: If we hit a pixel with > 2 neighbors (a junction), we should
            // deciding whether to stop.
            // But since we marked it visited, other paths will stop when they hit it.
            // To ensure good topology, often we traverse *until* a junction, then stop,
            // allowing other paths to also reach that junction.
            if (countNeighbors(input, cx, cy) > 2) {
                // It's a junction. Stop this segment so other segments can connect here.
                // Since it's marked visited, future scans won't start here,
                // but that's okay because we want lines *between* junctions.
                // WAIT: If we stop, we need to ensure we don't "consume" the junction such that
                // others can't see it?
                // Actually, 'visited' prevents restarting new lines, but 'findVisitedNeighbor'
                // allows connecting.
                // So marking it visited is fine.
                break;
            }
        }
        return points;
    }

    private Point2D_I32 findUnvisitedNeighbor(GrayU8 input, boolean[][] visited, int cx, int cy) {
        // Check 8 neighbors
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0)
                    continue;
                int nx = cx + dx;
                int ny = cy + dy;
                if (isInBounds(input, nx, ny)) {
                    if (input.get(nx, ny) == FG && !visited[nx][ny]) {
                        return new Point2D_I32(nx, ny);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Finds a neighbor that IS visited but is not the immediate previous point in
     * the list.
     * Used to close loops or connect to existing junctions.
     */
    private Point2D_I32 findVisitedNeighbor(GrayU8 input, int cx, int cy, List<Point2D_I32> currentPath) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0)
                    continue;
                int nx = cx + dx;
                int ny = cy + dy;

                if (isInBounds(input, nx, ny) && input.get(nx, ny) == FG) {
                    // Must be visited (implied by execution flow, but good to check context if
                    // logic changes)
                    // Check if it's the second-to-last point (backtracking).
                    // The last point added is (cx,cy). The one before that is
                    // currentPath.get(size-2).
                    if (currentPath.size() >= 2) {
                        Point2D_I32 prev = currentPath.get(currentPath.size() - 2);
                        if (prev.x == nx && prev.y == ny) {
                            continue; // It's just where we came from
                        }
                    }
                    return new Point2D_I32(nx, ny);
                }
            }
        }
        return null;
    }

    private int countNeighbors(GrayU8 input, int cx, int cy) {
        int count = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0)
                    continue;
                int nx = cx + dx;
                int ny = cy + dy;
                if (isInBounds(input, nx, ny) && input.get(nx, ny) == FG) {
                    count++;
                }
            }
        }
        return count;
    }

    private boolean isInBounds(GrayU8 input, int x, int y) {
        return input.isInBounds(x, y);
    }
}
