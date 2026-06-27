package org.trostheide.gantry.vectorize.strategies;

import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.ThresholdImageOps;

import boofcv.io.image.ConvertBufferedImage;
import boofcv.struct.image.GrayU8;
import georegression.struct.point.Point2D_I32;
import org.trostheide.gantry.vectorize.PolylineGeometry;
import org.trostheide.gantry.vectorize.VectorGeometry;
import org.trostheide.gantry.vectorize.VectorizationStrategy;
import org.trostheide.gantry.vectorize.PathGeometry;
import org.trostheide.gantry.vectorize.algorithms.CurveFitter;
import org.trostheide.gantry.vectorize.algorithms.SkeletonTracer;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Strategy for Centerline (Skeleton) Vectorization.
 * 
 * This strategy processes the entire image:
 * 1. Binarizes the image (Thresholding).
 * 2. Thins the binary image to a 1-pixel skeleton.
 * 3. Traces the skeleton into polylines using SkeletonTracer.
 */
public class SkeletonStrategy implements VectorizationStrategy {

    @Override
    public VectorGeometry processContour(List<Point2D_I32> rawPoints, double tolerance, double detailFactor,
            double minLength, double maxLength) {
        throw new UnsupportedOperationException("SkeletonStrategy requires the full image, not contours.");
    }

    @Override
    public WorkflowType getWorkflowType() {
        return WorkflowType.SKELETON;
    }

    @Override
    public String getName() {
        return "centerline";
    }

    /**
     * Orchestrates the centerline vectorization workflow.
     * 
     * @param image     Input image
     * @param threshold Binary threshold (0-255). Pixels darker than this become the
     *                  skeleton.
     * @param tolerance Tolerence for Ramer-Douglas-Peucker simplification (0.0 = no
     *                  simplification)
     * @return List of vector geometries (Polylines)
     */
    public static List<VectorGeometry> vectoriseImage(BufferedImage image, int threshold, double tolerance) {
        // 1. Convert to GrayU8
        GrayU8 input = ConvertBufferedImage.convertFrom(image, (GrayU8) null);
        GrayU8 binary = new GrayU8(input.width, input.height);

        // 2. Threshold
        // We assume dark strokes on light background.
        // So pixels < threshold should be 1 (Foreground), others 0.
        // ThresholdImageOps.threshold(input, output, threshold, down=true) sets <=
        // threshold to 1.
        ThresholdImageOps.threshold(input, binary, threshold, true);

        // 3. Thinning (Skeletonization)
        GrayU8 skeleton = new GrayU8(input.width, input.height);
        BinaryImageOps.thin(binary, -1, skeleton);

        // 4. Trace Skeleton
        SkeletonTracer tracer = new SkeletonTracer();
        List<List<Point2D_I32>> rawPaths = tracer.trace(skeleton);

        List<VectorGeometry> result = new ArrayList<>();

        for (List<Point2D_I32> path : rawPaths) {
            if (path.isEmpty())
                continue;
            List<Point2D_I32> simplified = path;
            if (tolerance > 0) {
                simplified = simplify(path, tolerance);
            }

            // If tolerance > 0, we apply curve fitting to smooth the simplification
            if (tolerance > 0 && simplified.size() >= 3) {
                String pathData = CurveFitter.fitBezier(simplified);
                result.add(new PathGeometry(pathData));
            } else {
                result.add(new PolylineGeometry(simplified));
            }
        }

        return result;
    }

    // --- Ramer-Douglas-Peucker Simplification ---

    private static List<Point2D_I32> simplify(List<Point2D_I32> points, double tol) {
        if (points.size() < 3)
            return points;
        List<Point2D_I32> result = new ArrayList<>();
        result.add(points.get(0));
        simplifyRecursive(points, 0, points.size() - 1, tol, result);
        result.add(points.get(points.size() - 1));
        return result;
    }

    private static void simplifyRecursive(List<Point2D_I32> points, int start, int end, double tol,
            List<Point2D_I32> result) {
        if (start + 1 >= end)
            return;

        double maxDist = -1;
        int index = -1;
        Point2D_I32 p1 = points.get(start);
        Point2D_I32 p2 = points.get(end);

        for (int i = start + 1; i < end; i++) {
            double dist = perpendicularDistance(points.get(i), p1, p2);
            if (dist > maxDist) {
                maxDist = dist;
                index = i;
            }
        }

        if (maxDist > tol) {
            simplifyRecursive(points, start, index, tol, result);
            result.add(points.get(index)); // Add the split point
            simplifyRecursive(points, index, end, tol, result);
        }
    }

    private static double perpendicularDistance(Point2D_I32 p, Point2D_I32 lineStart, Point2D_I32 lineEnd) {
        double dx = lineEnd.x - lineStart.x;
        double dy = lineEnd.y - lineStart.y;
        if (dx == 0 && dy == 0) {
            dx = p.x - lineStart.x;
            dy = p.y - lineStart.y;
            return Math.sqrt(dx * dx + dy * dy);
        }
        double t = ((p.x - lineStart.x) * dx + (p.y - lineStart.y) * dy) / (dx * dx + dy * dy);
        double projX = lineStart.x + t * dx;
        double projY = lineStart.y + t * dy;
        double distX = p.x - projX;
        double distY = p.y - projY;
        return Math.sqrt(distX * distX + distY * distY);
    }
}
