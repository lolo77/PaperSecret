package com.topsecret.plugin.papersecret.detector;

import com.secretlib.util.Log;
import com.topsecret.plugin.papersecret.util.Vector2D;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author Florent FRADET
 */
public class Shape {

    private static final Log LOG = new Log(Shape.class);
    List<Interval> lstIntervals;

    List<Vector2D> lstPointsMax = new ArrayList<>();

    double centroidX = -1;
    double centroidY = -1;
    double surface = 0;
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
            int d = (int) p.getSubtracted(target).getLengthSq();
            if (d < dist) {
                nearest = p;
                dist = d;
            }
        }
    }


    public Shape(Interval inter) {
        id = _id++;
        lstIntervals = new ArrayList<>();
        add(inter);
    }

    public List<Interval> getList() {
        return lstIntervals;
    }

    public void add(Interval i) {
        lstIntervals.add(i);
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

    public void complete(Vector2D boundingMax) {
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
        finders.add(new ClosestPointFinder(new Vector2D(0, 0)));
        finders.add(new ClosestPointFinder(new Vector2D(boundingMax.getX(), 0)));
        finders.add(new ClosestPointFinder(boundingMax));
        finders.add(new ClosestPointFinder(new Vector2D(0, boundingMax.getY())));

        for (Interval i : lstIntervals) {
            updateFinders(finders, new Vector2D(i.getxMin(), i.getY()));
            updateFinders(finders, new Vector2D(i.getxMax(), i.getY()));
        }

        for (ClosestPointFinder f : finders) {
            lstPointsMax.add(f.nearest);
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

    public static void drawCross(Graphics g, int x, int y, int size, Color c) {
        g.setColor(c);
        g.drawRect(x - size, y - size, size * 2, size * 2);
        g.drawLine(x - size, y - size, x + size, y + size);
        g.drawLine(x + size, y - size, x - size, y + size);
    }

    public void draw(BufferedImage img) {
        Graphics g = img.getGraphics();

        g.setColor(new Color(0x8000FF00, true));

        for (Interval i : lstIntervals) {
            g.drawLine(i.getxMin(), i.getY(), i.getxMax(), i.getY());
        }

        g.setColor(Color.BLUE);
        for (Vector2D v : lstPointsMax) {
            drawCross(g, (int) v.getX(), (int) v.getY(), 3, Color.GREEN);
        }

//        drawCross(g, (int) centroidX, (int) centroidY, 3, Color.RED);

    }

    public int getId() {
        return id;
    }

    public List<Vector2D> getLstPointsMax() {
        return lstPointsMax;
    }

    @Override
    public String toString() {
        return "Shape{" +
                "centroidX=" + centroidX +
                ", centroidY=" + centroidY +
                ", surface=" + surface +
                ", id=" + id +
                '}';
    }

    public static class SurfaceComparator implements Comparator<Shape> {

        @Override
        public int compare(Shape o1, Shape o2) {
            return (int) (o2.surface - o1.surface);
        }
    }
}
