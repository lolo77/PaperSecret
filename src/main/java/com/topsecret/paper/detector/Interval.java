package com.topsecret.paper.detector;

import java.util.Comparator;

public class Interval {
    private int xMin;
    private int xMax;
    private int y;

    public Interval(int xMin, int xMax, int y) {
        this.xMin = xMin;
        this.xMax = xMax;
        this.y = y;
    }

    public boolean isInside(int x) {
        return ((xMin <= x) && (x <= xMax));
    }


    public boolean overlaps(Interval other) {
        return (other.xMax >= xMin) && (other.xMin <= xMax);
    }


    public int getxMin() {
        return xMin;
    }

    public void setxMin(int xMin) {
        this.xMin = xMin;
    }

    public int getxMax() {
        return xMax;
    }

    public void setxMax(int xMax) {
        this.xMax = xMax;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    @Override
    public String toString() {
        return "Interval{" +
                "xMin=" + xMin +
                ", xMax=" + xMax +
                ", y=" + y +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Interval interval = (Interval) o;

        if (xMin != interval.xMin) return false;
        if (xMax != interval.xMax) return false;
        return y == interval.y;
    }

    @Override
    public int hashCode() {
        int result = xMin;
        result = 31 * result + xMax;
        result = 31 * result + y;
        return result;
    }

    public static class CompareY implements Comparator<Interval> {

        @Override
        public int compare(Interval o1, Interval o2) {
            return o1.y-o2.y;
        }
    }
}
