package com.topsecret.paper.sandbox;

import com.secretlib.exception.BagParseFinishException;
import com.secretlib.exception.NoBagException;
import com.secretlib.exception.TruncatedBagException;
import com.secretlib.model.HiDataBag;
import com.secretlib.model.HiDataBagBuilder;
import com.secretlib.util.HiUtils;
import com.secretlib.util.Log;
import com.secretlib.util.Parameters;
import com.topsecret.paper.detector.Interval;
import com.topsecret.paper.util.Vector2D;
import com.topsecret.paper.codec.WalkerPaper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

public class DecoderPaperV2 {

    private static final Log LOG = new Log(DecoderPaperV2.class);

    private static final boolean DEBUG_GRAPH = true;
    private static final int MONOCHROME_THRESHOLD = 0x80;
    private static final int EXTRACT_RES_X = 1024 * 8;
    private static final int EXTRACT_RES_Y = 1024 * 8;


    ShapeV1 outer = null;

    int[][] luminosity;


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

    private BufferedImage extractImage(BufferedImage img) {

        BufferedImage imgExt = new BufferedImage(EXTRACT_RES_X, EXTRACT_RES_Y, BufferedImage.TYPE_INT_RGB);
        WritableRaster rExt = imgExt.getRaster();

        Vector2D p10 = outer.lstPointsMax.get(0);
        Vector2D p11 = outer.lstPointsMax.get(1);
        Vector2D p01 = outer.lstPointsMax.get(2);
        Vector2D p00 = outer.lstPointsMax.get(3);

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


    private HiDataBag tryDecode(BufferedImage img, int res, Parameters params) throws Exception {

        LOG.debug("tryDecode res : " + res);
/*
        BufferedImage imgOut = null;
        Graphics g = null;
        if (DEBUG_GRAPH) {
            imgOut = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());
            g = imgOut.getGraphics();
            g.drawImage(img, 0, 0, null);
        }
*/
        byte[] pass = params.getKm().getBytes(StandardCharsets.UTF_8);
        byte[] hash = HiUtils.genHash(pass, params.getHashAlgo());
        HiDataBagBuilder[] bagBuilders = new HiDataBagBuilder[4];
        HiDataBag[] bags = new HiDataBag[4];
        for (int i = 0; i < 4; i++) {
            bags[i] = new HiDataBag();
            bagBuilders[i] = new HiDataBagBuilder(bags[i]);
        }

        WalkerPaper w = new WalkerPaper(hash, res, res);

        int[][] luminosity = EncoderPaperV2.computeImageLuminosity(img, res, res);
/*
        int _x = -1;
        int _y = -1;
*/
        int bit = 1;
        byte[] curByte = new byte[4];
        Arrays.fill(curByte, (byte) 0);

        byte[] data = new byte[w.getBitMax() / 8 + 1];
        Arrays.fill(data, (byte) 0);

        boolean bInterrupted = false;
        while (!bInterrupted) {
            int nbInvalidBags = 0;
            for (int orientation = 0; orientation < 4; orientation++) {
                if (bagBuilders[orientation] == null) {
                    nbInvalidBags++;
                    if (nbInvalidBags == 4) {
                        throw new NoBagException();
                    }
                    continue;
                }

                int x = w.getTmpPixXOriented(orientation);
                int y = w.getTmpPixYOriented(orientation);

                int lumAvg = EncoderPaperV2.computeAvgLuminosityAround(luminosity, x, y, res, res);
                int lum = luminosity[y][x];
                boolean flag = (Math.abs(lum - lumAvg) >= 0x20);
                //if ((w.getIdxDataBit() >= 40) && (w.getIdxDataBit() < 48)) {
                LOG.debug("x = " + x + " ; y = " + y + " ; lumAvg = " + lumAvg + " ; lum = " + lum + " ; flag : " + flag);
                //}
                if (flag) {
                    // Bit 1
                    curByte[orientation] |= bit;
                } else {
                    // Bit 0
                }
/*
                if (DEBUG_GRAPH) {// && (w.getIdxDataBit() >= 40) && (w.getIdxDataBit() < 48)) {
                    int x1 = x * img.getWidth() / res;
                    int x2 = (x + 1) * img.getWidth() / res;
                    int y1 = y * img.getHeight() / res;
                    int y2 = (y + 1) * img.getHeight() / res;
                    int nx = (x1 + x2) / 2;
                    int ny = (y1 + y2) / 2;
                    if (_x >= 0) {
                        g.setColor(Color.BLUE);
                        g.drawLine(_x, _y, nx, ny);
                    }
                    _x = nx;
                    _y = ny;

                    g.setColor(flag ? Color.GREEN : Color.RED);
                    g.drawRect(x1, y1, x2 - x1, y2 - y1);
                }
*/
                if (bit == 0x80) {
                    try {
                        bagBuilders[orientation].write(curByte[orientation]);
                        LOG.debug("Byte[" + orientation + "] : " + Character.toString((char) curByte[orientation]));
                    } catch (NoBagException e) {
                        bagBuilders[orientation] = null;
                        LOG.debug("No bag in orientation " + orientation);
                    } catch (BagParseFinishException e) {
                        bags[orientation].decryptAll(params);
                        return bags[orientation];
                    }
                }
            }

            if (bit == 0x80) {
                Arrays.fill(curByte, (byte) 0);
                bit = 1;
            } else {
                bit <<= 1;
            }

            if (!w.inc()) {
                // No more space
                bInterrupted = true;
                break;
            }
        }
/*
        if (DEBUG_GRAPH) {
            try {
                ImageIO.write(imgOut, "png", new File("decoder.png"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
*/
        throw new TruncatedBagException();
    }

    public HiDataBag decode(BufferedImage img, Parameters p) throws Exception {
/*
        byte[] bw = convertImgToMono(img, MONOCHROME_THRESHOLD);
        BufferedImage imgBW = convertImgMonoToImage(img, bw);

        ImageIO.write(imgBW, "png", new File("bw.png"));

        Log.setLevel(Log.CRITICAL);
        ShapeDetector detector = new ShapeDetector();
        detector.detect(bw, img.getWidth(), img.getHeight());
        Log.setLevel(Log.DEBUG);

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
            throw new DecoderNoShapeException();
        }

        if ((outer == null) || (outer.lstPointsMax.size() != 4)) {
            LOG.error("Outer 4 points not found");
            throw new DecoderShapeNotARectangleException();
        }
*/
        outer = new ShapeV1(new Interval(0, 0, 0));
/*        outer.lstPointsMax.add(new Vector2D(3298,43));
        outer.lstPointsMax.add(new Vector2D(3293,2401));
        outer.lstPointsMax.add(new Vector2D(139,2402));
        outer.lstPointsMax.add(new Vector2D(134,41));
*/
        /*
        outer.lstPointsMax.add(new Vector2D(3300, 45));
        outer.lstPointsMax.add(new Vector2D(3300, 2403));
        outer.lstPointsMax.add(new Vector2D(148, 2407));
        outer.lstPointsMax.add(new Vector2D(140, 47));
*/

        outer.lstPointsMax.add(new Vector2D(4127,0));
        outer.lstPointsMax.add(new Vector2D(4127,3095));
        outer.lstPointsMax.add(new Vector2D(0,3095));
        outer.lstPointsMax.add(new Vector2D(0,0));


/*        outer.lstPointsMax.add(new Vector2D(2559,0));
        outer.lstPointsMax.add(new Vector2D(2559,1919));
        outer.lstPointsMax.add(new Vector2D(0,1919));
        outer.lstPointsMax.add(new Vector2D(0,0));
*/

        BufferedImage imgExt = extractImage(img);
        ImageIO.write(imgExt, "png", new File("ext.png"));

//        tryDecode(imgExt, 384, p);

        for (int res = 32; res <= 512; res += 32) {
            try {
                HiDataBag bag = tryDecode(imgExt, res, p);
                LOG.debug("Secret found at res " + res);
                return bag;
            } catch (IOException e) {
                // NO OP
                // No secret found
            } catch (GeneralSecurityException e) {
                LOG.debug("Secret found but malformed.");
                break;
            }
        }
        return null;
    }
}
