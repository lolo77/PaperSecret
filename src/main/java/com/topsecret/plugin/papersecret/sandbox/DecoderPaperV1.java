package com.topsecret.plugin.papersecret.sandbox;

import com.secretlib.util.Log;
import com.secretlib.util.Parameters;
import com.topsecret.plugin.papersecret.detector.Shape;
import com.topsecret.plugin.papersecret.detector.ShapeDetector;
import com.topsecret.plugin.papersecret.detector.exception.DetectorNoShapeException;
import com.topsecret.plugin.papersecret.detector.exception.DetectorShapeNotARectangleException;
import com.topsecret.plugin.papersecret.util.Vector2D;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.util.Arrays;
import java.util.List;

public class DecoderPaperV1 {

    private static final Log LOG = new Log(DecoderPaperV1.class);

    private static final int MONOCHROME_THRESHOLD = 0x80;
    private static final double DCT_SUBPIXEL_INTERPOLATION = 16.0;
    private static final int DCT_MAX_DETECTION_LENGTH = 128;
    private static final int DCT_NOISE_THRESHOLD = 16;
    private static final int DCT_STABLE_THRESHOLD = 10;
    private static final int EXTRACT_RES_X = 2048;
    private static final int EXTRACT_RES_Y = 2048;
    private static final int RESOLUTION_PRECISION = 2048;


    private int[] pixelX = null;
    private int[] pixelY = null;

    Shape outer = null;


    private static byte[] convertImgToMono(BufferedImage img, int threshold) {

        byte[] bw = new byte[img.getWidth() * img.getHeight()];
        Arrays.fill(bw, (byte) 0xFF);

        int nbComp = img.getColorModel().getNumComponents();
        int[] pix = new int[nbComp];
        WritableRaster r = img.getRaster();
        int idxByte = 0;
        int[] avg = new int[nbComp * 9];
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                r.getPixel(x, y, pix);
                int lum = EncoderPaperV1.computeLuminosity(pix, 0, 3);
                if (lum < threshold) {
                    bw[idxByte] = 0;
                }
                idxByte++;
            }
        }

        return bw;
    }


    private static int getAvg(int[] pix) {
        int avg = 0;
        int compMax = Math.min(pix.length, 3); // Exclude Alpha
        for (int i = 0; i < compMax; i++) {
            avg += pix[i] & 0xff;
        }
        avg /= compMax;
        return avg;
    }

    private static int[] extractLine(BufferedImage img, Vector2D a, Vector2D b, int resolution) {
        int nbComp = img.getColorModel().getNumComponents();
        int[] pix = new int[nbComp];
        int[] near = new int[9];
        WritableRaster r = img.getRaster();

        int[] tab = new int[resolution];

        Vector2D i = new Vector2D();
        int idx = 0;
        while (idx < resolution) {
            double scalar = (double) (idx) / (double) resolution;
            i.interpolate(a, b, scalar);
            r.getPixel((int) i.x, (int) i.y, pix);
            tab[idx] = getAvg(pix); // To BW

            idx++;
        }
        return tab;
    }


    public static int[] computeDCT(int[] tab) {
        int[] ac = new int[tab.length - 1];
        Arrays.fill(ac, 0);

        for (int i = 1; i < tab.length; i++) {
            double di = (double) i;
            di /= DCT_SUBPIXEL_INTERPOLATION;
            for (int j = 0; j < tab.length; j++) {
                double alpha = (double) j / di;
                double cos = Math.cos(alpha * Math.PI * 2);
                double val = tab[j] * cos;
                ac[i - 1] += (int) val;
            }
            // Keep precision as we don't really care about the value itself
//            ac[i - 1] /= tab.length;
        }

        return ac;
    }

    private static double computePixelSize(int[] extract) {
        int[] dct = computeDCT(extract);
        int idxMin = -1;
        int idxMax = -1;
        int valMax = 0;
        int valMin = Integer.MAX_VALUE;
        for (int idx = (int) DCT_SUBPIXEL_INTERPOLATION; idx < dct.length; idx++) {
            if (dct[idx] < valMin) {
                valMin = dct[idx];
            }
            if (dct[idx] > valMax) {
                valMax = dct[idx];
                idxMin = idx;
                idxMax = idx;
            } else {
                if (dct[idx] == valMax) {
                    idxMax = idx;
                }
            }
        }

        double idxMoy = (double) (idxMin + idxMax) / 2.0;
        int valDelta = valMax - valMin;

        if ((idxMoy > 0) && (valDelta > DCT_NOISE_THRESHOLD)) {
            return idxMoy / (DCT_SUBPIXEL_INTERPOLATION * 2.0);
        }

        return 0;
    }

    private int detectRuleElementSize(BufferedImage img, Vector2D p00, Vector2D p10, Vector2D p11, Vector2D p01) {
        Vector2D a = new Vector2D();
        Vector2D b = new Vector2D();

        int resX = EXTRACT_RES_X;
        int resY = EXTRACT_RES_Y;
        int pixSize = -1;
        int nbPixSize = 0;
        int stablePixSize = -1;

        LOG.debug("detectRuleElementSize");

        for (int y = 0; y < DCT_MAX_DETECTION_LENGTH; y++) {
            double scalar = (double) y / (double) resY;
            a.interpolate(p00, p01, scalar);
            b.interpolate(p10, p11, scalar);

            int[] extract = extractLine(img, a, b, resX);

            double dPixSize = computePixelSize(extract) * RESOLUTION_PRECISION;
            int iPixSize = (int) dPixSize;
            LOG.debug("y = " + y + " ; dPixSize = " + dPixSize);

            if (iPixSize > 0) {
                if (pixSize == -1) {
                    pixSize = iPixSize;
                }

                if (pixSize == iPixSize) {
                    nbPixSize++;
                    if (nbPixSize == DCT_STABLE_THRESHOLD) {
                        stablePixSize = pixSize;
                        break;
                    }
                } else {
                    pixSize = iPixSize;
                    nbPixSize = 0;
                }
            }
        }
        return stablePixSize;
    }

    private Vector2D detectRuleLocation(BufferedImage img, Vector2D p00, Vector2D p10, Vector2D p11, Vector2D p01) {
        Vector2D a = new Vector2D();
        Vector2D b = new Vector2D();

        int resX = EXTRACT_RES_X;
        int resY = EXTRACT_RES_Y;
        int pixSize = -1;
        int nbPixSize = 0;
        int yStart = -1;

        LOG.debug("detectRuleLocation");

        for (int y = 0; y < DCT_MAX_DETECTION_LENGTH; y++) {
            double scalar = (double) y / (double) resY;
            a.interpolate(p00, p01, scalar);
            b.interpolate(p10, p11, scalar);

            int[] extract = extractLine(img, a, b, resX);

            double dPixSize = computePixelSize(extract) * RESOLUTION_PRECISION;
            int iPixSize = (int) dPixSize;
            LOG.debug("y = " + y + " ; dPixSize = " + dPixSize);

            if (iPixSize > 0) {
                if (pixSize == -1) {
                    pixSize = iPixSize;
                    yStart = y;
                }

                if (pixSize == iPixSize) {
                    nbPixSize++;
                    if (nbPixSize == DCT_STABLE_THRESHOLD) {
                        return new Vector2D(iPixSize, (y + yStart) / 2);
                    }
                } else {
                    pixSize = iPixSize;
                    nbPixSize = 0;
                    yStart = y;
                }
            }
        }
        return null;
    }

    public void initResolution(BufferedImage img) {
        Vector2D p10 = outer.getLstPointsMax().get(0);
        Vector2D p11 = outer.getLstPointsMax().get(1);
        Vector2D p01 = outer.getLstPointsMax().get(2);
        Vector2D p00 = outer.getLstPointsMax().get(3);

        Vector2D ruleX = detectRuleLocation(img, p00, p10, p11, p01);
        LOG.debug("ruleX : " + ruleX.toString());


        Vector2D ruleY = detectRuleLocation(img, p01, p00, p10, p11);
        LOG.debug("ruleY : " + ruleY.toString());

        /*

        int resX = detectRuleElementSize(img, p00, p10, p11, p01);
        int resY = detectRuleElementSize(img, p01, p00, p10, p11);

        LOG.debug("resX = " + resX + " (x" + RESOLUTION_PRECISION + ")");
        LOG.debug("resY = " + resY + " (x" + RESOLUTION_PRECISION + ")");

//        int imgResX = (int)((double)Math.round(EXTRACT_RES_X * RESOLUTION_PRECISION / (double)resX));
//        int imgResY = (int)((double)Math.round(EXTRACT_RES_Y * RESOLUTION_PRECISION / (double)resY));

        int imgResX = EXTRACT_RES_X * RESOLUTION_PRECISION / resX;
        int imgResY = EXTRACT_RES_Y * RESOLUTION_PRECISION / resY;

        LOG.debug("imgResX = " + imgResX);
        LOG.debug("imgResY = " + imgResY);


        pixelX = new int[imgResX + 1];
        int idx = 0;
        for (int x = resX / 2; x < EXTRACT_RES_X * RESOLUTION_PRECISION; x += resX, idx++) {
            pixelX[idx] = x / RESOLUTION_PRECISION;
        }

        pixelY = new int[imgResY + 1];
        idx = 0;
        for (int y = resY / 2; y < EXTRACT_RES_X * RESOLUTION_PRECISION; y += resY, idx++) {
            pixelY[idx] = y / RESOLUTION_PRECISION;
        }
        */
    }


    private BufferedImage rebuildImage(BufferedImage img) {
        BufferedImage imgExt = new BufferedImage(pixelX.length, pixelY.length, BufferedImage.TYPE_INT_RGB);
        WritableRaster rExt = imgExt.getRaster();

        Vector2D p10 = outer.getLstPointsMax().get(0);
        Vector2D p11 = outer.getLstPointsMax().get(1);
        Vector2D p01 = outer.getLstPointsMax().get(2);
        Vector2D p00 = outer.getLstPointsMax().get(3);

        Vector2D a = new Vector2D();
        Vector2D b = new Vector2D();

        int resX = EXTRACT_RES_X;
        int resY = EXTRACT_RES_Y;

        WritableRaster r = img.getRaster();
        int[] p = new int[4];
        for (int idxY = 0; idxY < pixelY.length; idxY++) {
            double scalar = (double) pixelY[idxY] / (double) resY;
            a.interpolate(p00, p01, scalar);
            b.interpolate(p10, p11, scalar);

            int[] extract = extractLine(img, a, b, resX);
            for (int idxX = 0; idxX < pixelX.length; idxX++) {
                int pix = extract[pixelX[idxX]];
                p[0] = pix;
                p[1] = pix;
                p[2] = pix;
                p[3] = 0xff;
                rExt.setPixel(idxX, idxY, p);
            }
        }

        return imgExt;
    }


    private BufferedImage extractImage(BufferedImage img) {

        BufferedImage imgExt = new BufferedImage(EXTRACT_RES_X, EXTRACT_RES_Y, BufferedImage.TYPE_INT_RGB);
        WritableRaster rExt = imgExt.getRaster();

        Vector2D p10 = outer.getLstPointsMax().get(0);
        Vector2D p11 = outer.getLstPointsMax().get(1);
        Vector2D p01 = outer.getLstPointsMax().get(2);
        Vector2D p00 = outer.getLstPointsMax().get(3);

        Vector2D a = new Vector2D();
        Vector2D b = new Vector2D();
        int[] pix = new int[3];

        for (int y = 0; y < EXTRACT_RES_Y; y++) {
            double scalar = (double) y / (double) EXTRACT_RES_Y;
            a.interpolate(p00, p01, scalar);
            b.interpolate(p10, p11, scalar);

            int[] extract = extractLine(img, a, b, EXTRACT_RES_X);

            for (int x = 0; x < EXTRACT_RES_X; x++) {
                pix[0] = extract[x];
                pix[1] = pix[0];
                pix[2] = pix[0];
                rExt.setPixel(x, y, pix);
            }
        }

        Graphics g = imgExt.getGraphics();
        for (int idxY = 0; idxY < pixelY.length; idxY++) {
            for (int idxX = 0; idxX < pixelX.length; idxX++) {
                pix[0] = 0xFF;
                pix[1] = 0;
                pix[2] = 0;
                ShapeV1.drawCross(g, pixelX[idxX], pixelY[idxY], 5, Color.GREEN);
            }
        }

        return imgExt;
    }

    private BufferedImage convertImgMonoToImage(BufferedImage img, byte[] bw) {
        BufferedImage imgBW = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        int[] pix = new int[4];
        pix[3] = 0xff;
        int i = 0;
        for (int y = 0; y < imgBW.getHeight(); y++) {
            for (int x = 0; x < imgBW.getWidth(); x++) {
                pix[0] = pix[1] = pix[2] = bw[i];
                imgBW.getRaster().setPixel(x, y, pix);
                i++;
            }
        }
        return imgBW;
    }

    public void decode(BufferedImage img, Parameters p) throws Exception {

        byte[] bw = convertImgToMono(img, MONOCHROME_THRESHOLD);
        BufferedImage imgBW = convertImgMonoToImage(img, bw);

        ImageIO.write(imgBW, "png", new File("bw.png"));


        ShapeDetector detector = new ShapeDetector();
        detector.detect(bw, img.getWidth(), img.getHeight());

        BufferedImage imgDetector = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        imgDetector.getGraphics().drawImage(img, 0, 0, null);
        detector.draw(imgDetector);

        ImageIO.write(imgDetector, "png", new File("detect.png"));


        List<Shape> lst = detector.getLstShapes();
        if (lst.size() > 0) {
            lst.sort(new Shape.SurfaceComparator());
            outer = lst.get(0);
            LOG.debug(outer.toString());
        } else {
            LOG.error("Shape not found");
            throw new DetectorNoShapeException();
        }

        if ((outer == null) || (outer.getLstPointsMax().size() != 4)) {
            LOG.error("Outer 4 points not found");
            throw new DetectorShapeNotARectangleException();
        }

        initResolution(img);

        BufferedImage imgExt = extractImage(img);
        ImageIO.write(imgExt, "png", new File("ext.png"));

        BufferedImage imgReb = rebuildImage(img);
        ImageIO.write(imgReb, "png", new File("rebuilt.png"));

    }
}
