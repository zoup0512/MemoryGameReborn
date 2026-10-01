package com.cube.memorygames.ui.hex.polygon;

import java.util.ArrayList;
import java.util.List;

public class Polygon {
    private final BoundingBox _boundingBox;
    private final List<Line> _sides;

    private static class BoundingBox {
        public float xMax;
        public float xMin;
        public float yMax;
        public float yMin;

        private BoundingBox() {
            this.xMax = Float.NEGATIVE_INFINITY;
            this.xMin = Float.NEGATIVE_INFINITY;
            this.yMax = Float.NEGATIVE_INFINITY;
            this.yMin = Float.NEGATIVE_INFINITY;
        }
    }

    public static class Builder {
        private BoundingBox _boundingBox = null;
        private boolean _firstPoint = true;
        private boolean _isClosed = false;
        private List<Line> _sides = new ArrayList();
        private List<Point> _vertexes = new ArrayList();

        public Builder addVertex(Point point) {
            if (this._isClosed) {
                this._vertexes = new ArrayList();
                this._isClosed = false;
            }
            updateBoundingBox(point);
            this._vertexes.add(point);
            if (this._vertexes.size() > 1) {
                this._sides.add(new Line((Point) this._vertexes.get(this._vertexes.size() - 2), point));
            }
            return this;
        }

        public Builder close() {
            validate();
            this._sides.add(new Line((Point) this._vertexes.get(this._vertexes.size() - 1), (Point) this._vertexes.get(0)));
            this._isClosed = true;
            return this;
        }

        public Polygon build() {
            validate();
            if (!this._isClosed) {
                this._sides.add(new Line((Point) this._vertexes.get(this._vertexes.size() - 1), (Point) this._vertexes.get(0)));
            }
            return new Polygon(this._sides, this._boundingBox);
        }

        private void updateBoundingBox(Point point) {
            if (this._firstPoint) {
                this._boundingBox = new BoundingBox();
                this._boundingBox.xMax = point.x;
                this._boundingBox.xMin = point.x;
                this._boundingBox.yMax = point.y;
                this._boundingBox.yMin = point.y;
                this._firstPoint = false;
                return;
            }
            if (point.x > this._boundingBox.xMax) {
                this._boundingBox.xMax = point.x;
            } else if (point.x < this._boundingBox.xMin) {
                this._boundingBox.xMin = point.x;
            }
            if (point.y > this._boundingBox.yMax) {
                this._boundingBox.yMax = point.y;
            } else if (point.y < this._boundingBox.yMin) {
                this._boundingBox.yMin = point.y;
            }
        }

        private void validate() {
            if (this._vertexes.size() < 3) {
                throw new RuntimeException("Polygon must have at least 3 points");
            }
        }
    }

    private Polygon(List<Line> sides, BoundingBox boundingBox) {
        this._sides = sides;
        this._boundingBox = boundingBox;
    }

    public static Builder Builder() {
        return new Builder();
    }

    public boolean contains(Point point) {
        if (inBoundingBox(point)) {
            Line ray = createRay(point);
            int intersection = 0;
            for (Line side : this._sides) {
                if (intersect(ray, side)) {
                    intersection++;
                }
            }
            if (intersection % 2 == 1) {
                return true;
            }
        }
        return false;
    }

    public List<Line> getSides() {
        return this._sides;
    }

    private boolean intersect(Line ray, Line side) {
        Point intersectPoint;
        float x;
        if (ray.isVertical() || side.isVertical()) {
            if (ray.isVertical() && !side.isVertical()) {
                x = ray.getStart().x;
                intersectPoint = new Point(x, (side.getA() * x) + side.getB());
            } else if (ray.isVertical() || !side.isVertical()) {
                return false;
            } else {
                x = side.getStart().x;
                intersectPoint = new Point(x, (ray.getA() * x) + ray.getB());
            }
        } else if (ray.getA() - side.getA() == 0.0f) {
            return false;
        } else {
            x = (side.getB() - ray.getB()) / (ray.getA() - side.getA());
            intersectPoint = new Point(x, (side.getA() * x) + side.getB());
        }
        if (side.isInside(intersectPoint) && ray.isInside(intersectPoint)) {
            return true;
        }
        return false;
    }

    private Line createRay(Point point) {
        return new Line(new Point(this._boundingBox.xMin - ((this._boundingBox.xMax - this._boundingBox.xMin) / 100.0f), this._boundingBox.yMin), point);
    }

    private boolean inBoundingBox(Point point) {
        if (point.x < this._boundingBox.xMin || point.x > this._boundingBox.xMax || point.y < this._boundingBox.yMin || point.y > this._boundingBox.yMax) {
            return false;
        }
        return true;
    }
}
