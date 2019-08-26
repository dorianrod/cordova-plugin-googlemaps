package plugin.google.maps;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Helper to get X and Y coordinates from a foreign class T.
 *
 * @author hgoebl
 * @since 06.07.13
 */
interface PointExtractor<T> {
    double getX(T point);
    double getY(T point);

    double getSquareDistance(T x1, T x2);
}


/**
 * Access to X and Y coordinates (2D-Point).
 *
 * @author hgoebl
 * @since 06.07.13
 */
interface Point {
    double getX();
    double getY();
}


class Point2D implements Point {
    double x;
    double y;
    public Point2D(double x, double y) {
        this.x = x;
        this.y = y;
    }
    public double getX() {
        return x;
    }
    public double getY() {
        return y;
    }
}

/**
 * Abstract base class for simplification of a polyline.
 *
 * @author hgoebl
 * @since 06.07.13
 */
abstract class AbstractSimplify<T> {

    private T[] sampleArray;

    protected AbstractSimplify(T[] sampleArray) {
        this.sampleArray = sampleArray;
    }

    /**
     * Simplifies a list of points to a shorter list of points.
     * @param points original list of points
     * @param tolerance tolerance in the same measurement as the point coordinates
     * @param highestQuality <tt>true</tt> for using Douglas-Peucker only,
     *                       <tt>false</tt> for using Radial-Distance algorithm before
     *                       applying Douglas-Peucker (should be a bit faster)
     * @return simplified list of points
     */
    public T[] simplify(T[] points,
                        double tolerance,
                        boolean highestQuality) {

        if (points == null || points.length <= 2) {
            return points;
        }

        double sqTolerance = tolerance * tolerance;

        if (!highestQuality) {
            points = simplifyRadialDistance(points, sqTolerance);
            return points;
        }

        points = simplifyDouglasPeucker(points, sqTolerance);

        return points;
    }

    T[] simplifyRadialDistance(T[] points, double sqTolerance) {
        T point = null;
        T prevPoint = points[0];

        List<T> newPoints = new ArrayList<T>();
        newPoints.add(prevPoint);

        for (int i = 1; i < points.length; ++i) {
            point = points[i];

            if (getSquareDistance(point, prevPoint) > sqTolerance) {
                newPoints.add(point);
                prevPoint = point;
            }
        }

        if (prevPoint != point) {
            newPoints.add(point);
        }

        return newPoints.toArray(sampleArray);
    }

    private static class Range {
        private Range(int first, int last) {
            this.first = first;
            this.last = last;
        }

        int first;
        int last;
    }

    T[] simplifyDouglasPeucker(T[] points, double sqTolerance) {

        BitSet bitSet = new BitSet(points.length);
        bitSet.set(0);
        bitSet.set(points.length - 1);

        List<Range> stack = new ArrayList<Range>();
        stack.add(new Range(0, points.length - 1));

        while (!stack.isEmpty()) {
            Range range = stack.remove(stack.size() - 1);

            int index = -1;
            double maxSqDist = 0f;

            // find index of point with maximum square distance from first and last point
            for (int i = range.first + 1; i < range.last; ++i) {
                double sqDist = getSquareSegmentDistance(points[i], points[range.first], points[range.last]);

                if (sqDist > maxSqDist) {
                    index = i;
                    maxSqDist = sqDist;
                }
            }

            if (maxSqDist > sqTolerance) {
                bitSet.set(index);

                stack.add(new Range(range.first, index));
                stack.add(new Range(index, range.last));
            }
        }

        List<T> newPoints = new ArrayList<T>(bitSet.cardinality());
        for (int index = bitSet.nextSetBit(0); index >= 0; index = bitSet.nextSetBit(index + 1)) {
            newPoints.add(points[index]);
        }

        return newPoints.toArray(sampleArray);
    }


    public abstract double getSquareDistance(T p1, T p2);

    public abstract double getSquareSegmentDistance(T p0, T p1, T p2);
}

/**
 * Simplification of a 2D-polyline.
 *
 * @author hgoebl
 * @since 06.07.13
 */
public class Simplify<T> extends AbstractSimplify<T> {

    private final PointExtractor<T> pointExtractor;

    /**
     * Simple constructor for 2D-Simplifier.
     * <br>
     * With this simple constructor your array elements must implement {@link Point}.<br>
     * If you have coordinate classes which cannot be changed to implement <tt>Point</tt>, use
     * {@link #Simplify(Object[], PointExtractor)} constructor!
     *
     * @param sampleArray pass just an empty array (<tt>new MyPoint[0]</tt>) - necessary for type consistency.
     */
    public Simplify(T[] sampleArray) {
        super(sampleArray);
        this.pointExtractor = new PointExtractor<T>() {
            @Override
            public double getX(T point) {
                return ((Point) point).getX();
            }

            @Override
            public double getY(T point) {
                return ((Point) point).getY();
            }

            @Override
            public double getSquareDistance(T p1, T p2) {
                double dx = pointExtractor.getX(p1) - pointExtractor.getX(p2);
                double dy = pointExtractor.getY(p1) - pointExtractor.getY(p2);

                return dx * dx + dy * dy;
            }
        };
    }

    /**
     * Alternative constructor for 2D-Simplifier.
     * <br>
     * With this constructor your array elements do not have to implement a special interface like {@link Point}.<br>
     * Implement a {@link PointExtractor} to give <tt>Simplify</tt> access to your coordinates.
     *
     * @param sampleArray pass just an empty array (<tt>new MyPoint[0]</tt>) - necessary for type consistency.
     * @param pointExtractor your implementation to extract X and Y coordinates from you array elements.
     */
    public Simplify(T[] sampleArray, PointExtractor<T> pointExtractor) {
        super(sampleArray);
        this.pointExtractor = pointExtractor;
    }

    @Override
    public double getSquareDistance(T p1, T p2) {
        return pointExtractor.getSquareDistance(p1, p2);
        /*
        double dx = pointExtractor.getX(p1) - pointExtractor.getX(p2);
        double dy = pointExtractor.getY(p1) - pointExtractor.getY(p2);

        return dx * dx + dy * dy;*/
    }

    @Override
    public double getSquareSegmentDistance(T p0, T p1, T p2) {
        double x0, y0, x1, y1, x2, y2, dx, dy, t;

        x1 = pointExtractor.getX(p1);
        y1 = pointExtractor.getY(p1);
        x2 = pointExtractor.getX(p2);
        y2 = pointExtractor.getY(p2);
        x0 = pointExtractor.getX(p0);
        y0 = pointExtractor.getY(p0);

        dx = x2 - x1;
        dy = y2 - y1;

        if (dx != 0.0d || dy != 0.0d) {
            t = ((x0 - x1) * dx + (y0 - y1) * dy)
                    / (dx * dx + dy * dy);

            if (t > 1.0d) {
                x1 = x2;
                y1 = y2;
            } else if (t > 0.0d) {
                x1 += dx * t;
                y1 += dy * t;
            }
        }

        dx = x0 - x1;
        dy = y0 - y1;

        return dx * dx + dy * dy;
    }
}
