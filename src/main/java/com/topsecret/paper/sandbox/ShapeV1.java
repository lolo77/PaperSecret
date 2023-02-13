package com.topsecret.paper.sandbox;

import com.secretlib.util.Log;
import com.topsecret.paper.detector.Interval;
import com.topsecret.paper.util.Vector2D;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * Reference : https://www.researchgate.net/publication/308540963_Fast_Method_for_Rectangle_Detection
 * Fast Method for Rectangle Detection
 * Cheng Wang
 * Software engineering department, Hainan College of Software Technology, Hainan 571400, China
 * naturavo@163.com
 * 6th International Conference on Machinery, Materials, Environment, Biotechnology and Computer (MMEBC 2016)
 *
 * Model, adaptation & Java coding by Florent FRADET
 * Adaptations :
 * - Ratios Length & Surface (rl, rs) computation corrected
 * - Added circle & ellipsis detection
 */
public class ShapeV1 {

    private static final Log LOG = new Log(ShapeV1.class);
    List<Interval> lstIntervals;

    List<Vector2D> lstPointsMax = new ArrayList<>();

    int yMin = Integer.MAX_VALUE;
    int yMax = -1;


    double centroidX = -1;
    double centroidY = -1;

    double ptFarX = -1;
    double ptFarY = -1;
    double distSqrFar = -1;

    double ptNearX = -1;
    double ptNearY = -1;
    double distSqrNear = Integer.MAX_VALUE;
    double slopeNear = Integer.MAX_VALUE;

    double ptNearOrthoX = -1;
    double ptNearOrthoY = -1;
    double distSqrNearOrtho = Integer.MAX_VALUE;
    double slopeNearOrtho = Integer.MAX_VALUE;

    double surface = 0;
    double sqrFactor = 0;
    double rs, rl;
    boolean rect = false;
    boolean circle = false;

    int id;

    static int _id = 0;


    private class ClosestPointFinder {
        Vector2D target;
        Vector2D nearest;
        int dist;

        public ClosestPointFinder(Vector2D t) {
            target = t;
            dist = Integer.MAX_VALUE;
            nearest = null;
        }

        public void update(Vector2D p) {
            int d = (int)p.getSubtracted(target).getLengthSq();
            if (d < dist) {
                nearest = p;
                dist = d;
            }
        }
    }


    public ShapeV1(Interval inter) {
        id = _id++;
        lstIntervals = new ArrayList<>();
        add(inter);
    }

    public List<Interval> getList() {
        return lstIntervals;
    }

    public void add(Interval i) {
        lstIntervals.add(i);
        yMin = Math.min(yMin, i.getY());
        yMax = Math.max(yMax, i.getY());
    }

    public void addAll(List<Interval> lst) {
        for (Interval i : lst) {
            add(i);
        }
    }

    public Interval getLast() {
        if (lstIntervals.size() > 0)
            return lstIntervals.get(lstIntervals.size() - 1);
        return null;
    }

    protected void simplify() {
        for (int y = yMin; y <= yMax; y++) {
            simplifyRaster(y);
        }
    }

    public void complete(Vector2D boundingMax) {
        simplify();
        computeCentroid();
        computeRectCorners(boundingMax);
    }

    protected void updateFinders(List<ClosestPointFinder> finders, Vector2D p) {
        for (ClosestPointFinder f : finders) {
            f.update(p);
        }
    }

    protected void computeRectCorners(Vector2D boundingMax) {
        List<ClosestPointFinder> finders = new ArrayList<>();
        finders.add(new ClosestPointFinder(new Vector2D(0,0)));
        finders.add(new ClosestPointFinder(new Vector2D(boundingMax.getX(),0)));
        finders.add(new ClosestPointFinder(boundingMax));
        finders.add(new ClosestPointFinder(new Vector2D(0,boundingMax.getY())));

        for (Interval i : lstIntervals) {
            updateFinders(finders, new Vector2D(i.getxMin(), i.getY()));
            updateFinders(finders, new Vector2D(i.getxMax(), i.getY()));
        }

        for (ClosestPointFinder f : finders) {
            lstPointsMax.add(f.nearest);
        }
    }


    protected void updatePoints(int x, int y) {



//        if (boundingMin == null) {
//            boundingMin = new Vector2D(x,y);
//        }
//        if (boundingMax == null) {
//            boundingMax = new Vector2D(x,y);
//        }
//        boundingMin.set(Math.min(x, boundingMin.getX()), Math.min(y, boundingMin.getY()));
//        boundingMax.set(Math.max(x, boundingMax.getX()), Math.max(y, boundingMax.getY()));

   /*     double dx = (x - centroidX);
        double dy = (y - centroidY);
        double sqrDist = dx * dx + dy * dy;

        if (sqrDist > distSqrFar) {
            distSqrFar = sqrDist;
            ptFarX = x;
            ptFarY = y;
            Vector2D pt = new Vector2D(x,y);
            if (lstPointsMax.size() == 0) {
                lstPointsMax.add(pt);
            } else {
                lstPointsMax.set(lstPointsMax.size()-1, pt);
            }
        }

        if (sqrDist < distSqrNear) {
            distSqrNear = sqrDist;
            ptNearX = x;
            ptNearY = y;
        }*/
    }


    protected void updateNearOrtho(int x, int y) {
        double dx = ((double) x - centroidX);
        double dy = ((double) y - centroidY);
        double sno = Math.abs((dy / dx) * slopeNear + 1.0);

        if (sno < slopeNearOrtho) {
            distSqrNearOrtho = dx * dx + dy * dy;
            slopeNearOrtho = sno;
            ptNearOrthoX = x;
            ptNearOrthoY = y;
        }
    }


    protected void computePoints() {
            for (Interval i : lstIntervals) {
                updatePoints(i.getxMin(), i.getY());
                updatePoints(i.getxMax(), i.getY());
            }


/*
            slopeNear = (ptNearY - centroidY) / (ptNearX - centroidX);

            last = null;
            for (Interval i : lstIntervals) {
                if ((i.getY() == yMin) || (i.getY() == yMax)) {
                    for (int x = i.getxMin(); x < i.getxMax(); x++) {
                        updateNearOrtho(x, i.getY());
                    }
                } else {
                    int xa = Math.min(last.getxMin(), i.getxMin());
                    int xb = Math.max(last.getxMin(), i.getxMin());
                    for (int x = xa; x <= xb; x++) {
                        updateNearOrtho(x, i.getY());
                    }
                    xa = Math.min(last.getxMax(), i.getxMax());
                    xb = Math.max(last.getxMax(), i.getxMax());
                    for (int x = xa; x <= xb; x++) {
                        updateNearOrtho(x, i.getY());
                    }
                }
                last = i;
            }

            double da = Math.sqrt(distSqrNear);
            double db = Math.sqrt(distSqrNearOrtho);
//            double dm = Math.sqrt(distSqrFar);

            rs = Math.abs((surface - da * db * 4) / surface);
            rl = Math.abs((distSqrFar - (distSqrNear + distSqrNearOrtho)) / distSqrFar);
            double rlno = Math.abs((distSqrFar / distSqrNearOrtho) - 1);

            rect = (rs <= 0.5) && (rl <= 0.5) && (rlno > 0.5);

            double rlcn = Math.abs((distSqrNear / distSqrNearOrtho) - 1);
            double rlcf = Math.abs((distSqrNear / distSqrFar) - 1);

            // Circle OR Ellipsis
            circle = ((rlcn < 0.1) && (rlcf < 0.1)) || (rlno <= 0.5);

            findPoints();*/
    }

    protected void findPoints() {

        lstIntervals.sort(new Interval.CompareY());

        List<Vector2D> lstPtsContour = new ArrayList<>();

        Interval inter;
        int idx = 0;
        while (idx < lstIntervals.size()) {
            inter = lstIntervals.get(idx);
            lstPtsContour.add(new Vector2D(inter.getxMax(), inter.getY()));
            idx++;
        }

        while (idx > 0) {
            idx--;
            inter = lstIntervals.get(idx);
            lstPtsContour.add(new Vector2D(inter.getxMin(), inter.getY()));
        }

//        findPointsMaxByLine(lstPtsContour, 0.8);
//        findPointsMaxByAngle(lstPtsContour);
        lstPointsMax.addAll(lstPtsContour);

        if (lstPointsMax.size() > 4) {
            List<Vector2D> toRemove = new ArrayList<>();
            List<Vector2D> pts = new ArrayList<>();
         //   pts.add(lstPointsMax.get(lstPointsMax.size() - 1));
            pts.addAll(lstPointsMax);
            pts.add(lstPointsMax.get(0));

            double threshold = 0.99;

            Iterator<Vector2D> iter = pts.iterator();
            Vector2D a = iter.next();
            Vector2D b = iter.next();
            while (iter.hasNext()) {
                Vector2D c = iter.next();
//                Vector2D d = iter.next();
                double spreadABC = b.orthoLengthSq(a, c);
                if (spreadABC < threshold) {
                    toRemove.add(b);
                    b = a;
                }
                /*else {
                    double spreadBCD = c.orthoLengthSq(b, d);
                    if (spreadBCD < threshold) {
                        toRemove.add(c);
                    } else {
                        double spreadABD = b.orthoLengthSq(a, d);
                        double spreadACD = c.orthoLengthSq(a, d);
                        boolean alignABD = spreadABD < threshold;
                        boolean alignACD = spreadACD < threshold;
                        if (alignABD != alignACD) {
                            if (alignABD) {
                                toRemove.add(c);
                            } else {
                                toRemove.add(b);
                            }
                        }
                    }
                }
*/
                a = b;
                b = c;
  //              c = d;
            }

            for (Vector2D v : toRemove) {
                lstPointsMax.remove(v);
            }
        }
    }


    protected void findPointsMaxByLine(List<Vector2D> lstPtsContour, double curveThreshold) {
        List<Vector2D> lstLastPoints = new ArrayList<>();

        if (lstPtsContour.size() < 4) {
            return;
        }

        Iterator<Vector2D> iter = lstPtsContour.iterator();
        Vector2D vFirst = iter.next();
        Vector2D vLast = iter.next();
        Vector2D vCur;
        double spread;

        lstPointsMax.add(vFirst);

        while (iter.hasNext()) {
            vCur = iter.next();
            spread = vCur.orthoLengthSq(vFirst, vLast);

            if (spread > curveThreshold) {
                lstPointsMax.add(vLast);
                vFirst = vLast;
            }

            vLast = vCur;
        }

    }

    protected void findPointsMaxByAngle(List<Vector2D> lstPtsContour) {
        List<Vector2D> lstLastPoints = new ArrayList<>();

        lstPointsMax.add(lstPtsContour.get(0));
        int checkLength = 5;
        for (Vector2D v : lstPtsContour) {
            lstLastPoints.add(v);
            if (lstLastPoints.size() > checkLength) {
                lstLastPoints.remove(0);

                Vector2D v0 = lstLastPoints.get(0);
                Vector2D v1 = lstLastPoints.get(checkLength / 2);
                Vector2D v2 = lstLastPoints.get(checkLength - 1);

                Vector2D v01 = v1.getSubtracted(v0);
                Vector2D v12 = v2.getSubtracted(v1);
                v01.normalize();
                v12.normalize();

                double a = v01.dot(v12);
                double ang = Math.acos(a);

                double deg = ang * 180 / Math.PI;

                // Not PI/2 to allow malformed corners to be detected
                if (ang >= Math.PI / 4) {
                    lstPointsMax.add(v1);
                }
            }
        }
    }

    protected void computeCentroid() {
        if (centroidX == -1) {
            double xMoy = 0;
            double yMoy = 0;
            double s = 0;
            for (Interval i : lstIntervals) {
                double delta = i.getxMax() - i.getxMin() + 1;
                xMoy += (double) (i.getxMin() + i.getxMax()) / 2.0;
                yMoy += delta * i.getY();
                s += delta;
            }
            xMoy /= lstIntervals.size();
            yMoy /= s;
            centroidX = xMoy;
            centroidY = yMoy;
            surface = s;
        }
    }

    public Interval simplifyRaster(int y) {
        int xMin = Integer.MAX_VALUE;
        int xMax = -1;
        Iterator<Interval> iter = lstIntervals.iterator();
        while (iter.hasNext()) {
            Interval i = iter.next();
            if (i.getY() != y)
                continue;
            if (i.getxMin() < xMin) {
                xMin = i.getxMin();
            }
            if (i.getxMax() > xMax) {
                xMax = i.getxMax();
            }
//            LOG.debug("shape #" + id + " : removed interval " + i.toString());
            iter.remove();
        }
        Interval simple = new Interval(xMin, xMax, y);
        lstIntervals.add(simple);
//        LOG.debug("shape #" + id + " : simple interval " + simple.toString());
        return simple;
    }

    public boolean isRect() {
        return rect;
    }

    public boolean isCircle() {
        return circle;
    }

    public static void drawCross(Graphics g, int x, int y, int size, Color c) {
        g.setColor(c);
        g.drawRect(x - size, y - size, size * 2, size * 2);
        g.drawLine(x - size, y - size, x + size, y + size);
        g.drawLine(x + size, y - size, x - size, y + size);
    }

    public void draw(BufferedImage img) {
        Graphics g = img.getGraphics();

        g.setColor(isRect() ? Color.GREEN : Color.LIGHT_GRAY);
        if (isCircle()) {
            g.setColor(Color.RED);
        }
        for (Interval i : lstIntervals) {
            g.drawLine(i.getxMin(), i.getY(), i.getxMax(), i.getY());
        }

        for (Vector2D v : lstPointsMax) {
            drawCross(g, (int) v.getX(), (int) v.getY(), 3, Color.GREEN);
        }
/*
        drawCross(g, (int) centroidX, (int) centroidY, 3, Color.RED);
        drawCross(g, (int) ptFarX, (int) ptFarY, 3, Color.CYAN);
        drawCross(g, (int) ptNearX, (int) ptNearY, 3, Color.GREEN);
        drawCross(g, (int) ptNearOrthoX, (int) ptNearOrthoY, 3, Color.ORANGE);
*/
    }

    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        return "Shape{" +
                "centroidX=" + centroidX +
                ", centroidY=" + centroidY +
                ", surface=" + surface +
                ", rect=" + rect +
                ", circle=" + circle +
                ", id=" + id +
                '}';
    }

    public static class SurfaceComparator implements Comparator<ShapeV1> {

        @Override
        public int compare(ShapeV1 o1, ShapeV1 o2) {
            return (int) (o2.surface - o1.surface);
        }
    }
}
