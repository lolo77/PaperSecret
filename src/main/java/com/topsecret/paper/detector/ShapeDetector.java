package com.topsecret.paper.detector;

import com.secretlib.util.Log;
import com.topsecret.paper.util.Vector2D;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
public class ShapeDetector {

    private static final Log LOG = new Log(ShapeDetector.class);

    private List<Shape> lstShapes;
    private HashMap<Interval, Shape> hmShapes;

    private byte[] bw;
    private int width;

    private int curIdx;
    private int curX;
    private int curY;
    private int endIdx;

    public ShapeDetector() {
        lstShapes = new ArrayList<>();
        hmShapes = new HashMap<>();
        bw = null;
        width = 0;
    }

    public List<Shape> getLstShapes() {
        return lstShapes;
    }

    /**
     * debug purpose
     *
     * @param img
     */
    public void draw(BufferedImage img) {
        for (Shape shape : lstShapes) {
            shape.draw(img);
        }
    }

    /**
     * Detects the next black raster line
     *
     * @return
     */
    public Interval next() {
        // Skip white
        while (curIdx < endIdx) {
            while ((curIdx < endIdx) && (curX < width) && (bw[curIdx] != 0)) {
                curIdx++;
                curX++;
            }
            if (curX < width) {
                int begX = curX;
                while ((curIdx < endIdx) && (curX < width) && (bw[curIdx] == 0)) {
                    curIdx++;
                    curX++;
                }
                return new Interval(begX, curX - 1, curY);
            } else {
                curX = 0;
                curY++;
                curIdx = curY * width;
            }
        }
        return null;
    }

    public Interval nextLine() {
        // Skip white
        curX = 0;
        curIdx = curY * width;
        while (curIdx < endIdx) {
            while ((curIdx < endIdx) && (curX < width) && (bw[curIdx] != 0)) {
                curIdx++;
                curX++;
            }
            if (curX < width) {
                int begX = curX;
                curX = width-1;
                curIdx = curY * width + curX;
                while ((curX > 0) && (bw[curIdx] != 0)) {
                    curIdx--;
                    curX--;
                }
                return new Interval(begX, curX, curY);
            } else {
                curX = 0;
                curY++;
                curIdx = curY * width;
            }
        }
        return null;
    }

    public void detect(byte[] bw, int w, int h) {
        this.bw = bw;
        width = w;
        curIdx = 0;
        endIdx = w * h;
        curX = 0;
        curY = 0;
        int oldY = -1;

        HashMap<Interval, Shape> htCurInter = new HashMap<>();
        while (curIdx < endIdx) {
            Interval i = nextLine();

            if (i != null) {
                LOG.debug("Interval : " + i.toString());

                if (oldY != i.getY()) {
                    if (oldY < i.getY() - 1) {
                        hmShapes = new HashMap<>();
                        LOG.debug("new raster line : after empty area");
                    } else {
                        hmShapes = htCurInter;
                        LOG.debug("new raster line : adjacent");
                    }
                    oldY = i.getY();
                    htCurInter = new HashMap<>();
                }

                List<Shape> lstShaOver = new ArrayList<>();
                for (Interval inter : hmShapes.keySet()) {
                    if (inter.overlaps(i)) {
                        Shape shape = hmShapes.get(inter);
                        lstShaOver.add(shape);
                        LOG.debug("Overlapping shape #" + shape.getId());
                    }
                }
                Shape theShape = null;
                if (lstShaOver.size() == 0) {
                    theShape = new Shape(i);
                    lstShapes.add(theShape);
                    LOG.debug("new shape #" + theShape.getId() + " : " + i.toString() + " / " + theShape.toString());
                } else {
                    theShape = lstShaOver.get(0);
                    theShape.add(i);
                    LOG.debug("extending shape # " + theShape.getId() + " : " + i.toString() + " / " + theShape.toString());

                    if (lstShaOver.size() > 1) {
                        LOG.debug("Attaching " + lstShaOver.size() + " shapes together : " + i.toString());
                        for (int idx = 1; idx < lstShaOver.size(); idx++) {
                            Shape shape = lstShaOver.get(idx);
                            LOG.debug("shape #" + shape.getId() + " -> " + shape.getLast().toString());
                            if (shape != theShape) {
                                lstShapes.remove(shape);
                                LOG.debug("removed shape #" + shape.getId());
                                theShape.addAll(shape.getList());

                                for (Map.Entry<Interval, Shape> entry : hmShapes.entrySet()) {
                                    if (entry.getValue() == shape) {
                                        LOG.debug("replaced in hmShapes : #" + shape.getId() + " -> #" + theShape.getId());
                                        entry.setValue(theShape);
                                    }
                                }
                                for (Map.Entry<Interval, Shape> entry : htCurInter.entrySet()) {
                                    if (entry.getValue() == shape) {
                                        LOG.debug("replaced in htCurInter : #" + shape.getId() + " -> #" + theShape.getId());
                                        entry.setValue(theShape);
                                    }
                                }
                            }
                        }

                    }
                }
                htCurInter.put(i, theShape);
            }
            curY++;
        }

        for (Shape shape : lstShapes) {
            shape.complete(new Vector2D(w,h));
/*
            if (shape.isRect()) {
                LOG.debug("RECT   : " + shape.toString());
            }
            if (shape.isCircle()) {
                LOG.debug("CIRCLE : " + shape.toString());
            }
*/
        }
    }
}
