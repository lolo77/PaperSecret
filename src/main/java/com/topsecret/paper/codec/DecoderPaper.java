package com.topsecret.paper.codec;

import com.secretlib.exception.BagParseFinishException;
import com.secretlib.exception.NoBagException;
import com.secretlib.exception.TruncatedBagException;
import com.secretlib.model.HiDataBag;
import com.secretlib.model.HiDataBagBuilder;
import com.secretlib.util.HiUtils;
import com.secretlib.util.Log;
import com.topsecret.paper.detector.Shape;
import com.topsecret.paper.detector.ShapeDetector;
import com.topsecret.paper.detector.exception.DetectorNoShapeException;
import com.topsecret.paper.detector.exception.DetectorShapeNotARectangleException;
import com.topsecret.paper.util.ParamPaper;
import com.topsecret.paper.util.Vector2D;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

public class DecoderPaper {

    private static final Log LOG = new Log(DecoderPaper.class);

    private static final boolean DEBUG_GRAPH = false;


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
//            LOG.debug("x = " + i.x + " ; y = " + i.y + " ; scalar = " + scalar);
            r.getPixel((int) i.x, (int) i.y, pix);
            tab[idx] = getAvg(pix); // To BW

            idx++;
        }
        return tab;
    }

    private BufferedImage extractImage(BufferedImage img, Shape outer, ParamPaper p) {

        int res = p.getExtractRes();
        BufferedImage imgExt = new BufferedImage(res, res, BufferedImage.TYPE_INT_RGB);
        WritableRaster rExt = imgExt.getRaster();

        Vector2D p00 = outer.getLstPointsMax().get(0);
        Vector2D p10 = outer.getLstPointsMax().get(1);
        Vector2D p11 = outer.getLstPointsMax().get(2);
        Vector2D p01 = outer.getLstPointsMax().get(3);

        Vector2D a = new Vector2D();
        Vector2D b = new Vector2D();
        int[] pix = new int[3];

        for (int y = 0; y < res; y++) {
            double scalar = (double) y / (double) res;
            a.interpolate(p00, p01, scalar);
            b.interpolate(p10, p11, scalar);

            int[] extract = extractLine(img, a, b, res);

            for (int x = 0; x < res; x++) {
                pix[0] = extract[x];
                pix[1] = pix[0];
                pix[2] = pix[0];
                rExt.setPixel(x, y, pix);
            }
        }

        return imgExt;
    }

    public static int computeFastRadonTransform(int[][] pixels, int threshold) {
        int blocH = pixels.length;
        int blocW = pixels[0].length;
        // Fast Radon transform
        int minH = Integer.MAX_VALUE;
        int maxH = 0;
        for (int i = 0; i < blocW; i++) {
            int edgeAvg = 0;
            for (int j = 0; j < blocH; j++) {
                edgeAvg += pixels[j][i];
            }
            edgeAvg /= blocH;
            if (edgeAvg > maxH) {
                maxH = edgeAvg;
            }
            if (edgeAvg < minH) {
                minH = edgeAvg;
            }
        }
        int difH = maxH - minH;

        int minW = Integer.MAX_VALUE;
        int maxW = 0;
        for (int i = 0; i < blocH; i++) {
            int edgeAvg = 0;
            for (int j = 0; j < blocW; j++) {
                edgeAvg += pixels[i][j];
            }
            edgeAvg /= blocW;
            if (edgeAvg > maxW) {
                maxW = edgeAvg;
            }
            if (edgeAvg < minW) {
                minW = edgeAvg;
            }
        }
        int difW = maxW - minW;

//        LOG.debug("difW = " + difW + ", difH = " + difH);

        int out = (difH >= threshold) ? 1 : 0;
        out |= (difW >= threshold) ? 2 : 0;
        return out;
    }

    private int findMark(BufferedImage img, int x, int y, int w, int h, boolean invert, ParamPaper p) {
        int x1 = x * img.getWidth() / w;
        int x2 = (x + 1) * img.getWidth() / w;
        int y1 = y * img.getHeight() / h;
        int y2 = (y + 1) * img.getHeight() / h;
        int blocW = x2 - x1;
        int blocH = y2 - y1;

        int nbComp = img.getColorModel().getNumComponents();
        int[] pixelsComp = img.getRaster().getPixels(x1, y1, blocW, blocH, (int[]) null);
        int[][] pixels = new int[blocH][blocW];

        int _x = 0;
        int _y = 0;
        for (int i = 0; i < pixelsComp.length; i += nbComp, _x++) {
            if (_x == blocW) {
                _x = 0;
                _y++;
            }
            pixels[_y][_x] = pixelsComp[i];
        }

        int out = computeFastRadonTransform(pixels, p.getThresholdDecodeRadon());
        if (invert) {
            int outInv = ((out & 1) << 1) | ((out & 2) >> 1);
            out = outInv;
        }
//        LOG.debug("findMark returns " + out + " @ " + x1 + "," + y1 + "," + x2 + "," + y2);

        return out;
    }

    private void readAllMarks(BufferedImage img, int res, ParamPaper p) {
        BufferedImage imgOut = null;
        Graphics g = null;
        imgOut = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());
        g = imgOut.getGraphics();
        g.drawImage(img, 0, 0, null);

        for (int y = 0; y < res; y++) {
            for (int x = 0; x < res; x++) {
                int b2 = findMark(img, x, y, res, res, false, p);
                int x1 = x * img.getWidth() / res;
//                int x2 = (x + 1) * img.getWidth() / res;
//                int y1 = y * img.getHeight() / res;
                int y2 = (y + 1) * img.getHeight() / res;
                g.drawString("" + b2, x1, y2);
            }
        }

        try {
            ImageIO.write(imgOut, "png", new File("debug_marks.png"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param img    the input image containing a secret bit stream
     * @param res    x&y resolution (2-bits boxes)
     * @param params decoding parameters
     * @param o      orientation to fine-tune
     * @return the decrypted
     * @throws Exception if a bag is truncated or any other error occurs
     */
    private HiDataBag tryDecode(BufferedImage img, int res, ParamPaper params, int o) throws Exception {
        LOG.debug("tryDecode res : " + res);

        final int NB_ORIENTATIONS = 8;

        byte[] pass = params.getKm().getBytes(StandardCharsets.UTF_8);
        byte[] hash = HiUtils.genHash(pass, params.getHashAlgo());

        // Default threshold scan from 8 to 64 increment by 4
        int t1 = 0x08;
        int t2 = 0x41;
        int ti = 0x04;

        int o1 = 0;
        int o2 = NB_ORIENTATIONS;

        if (o >= 0) {
            // Fine tune thresholds t1..t2 increment by 1
            t1 = params.getThresholdDecodeRadon() - ti;
            t2 = Math.min(params.getThresholdDecodeRadon() + ti * 2 + 1, t2);
            ti = 1;

            // Limit to this orientation "o"
            o1 = o;
            o2 = o + 1;
        }

        for (int t = t1; t < t2; t += ti) {
//            LOG.debug("Trying with threshold " + t);
            params.setThresholdDecodeRadon(t);

            HiDataBagBuilder[] bagBuilders = new HiDataBagBuilder[NB_ORIENTATIONS];
            HiDataBag[] bags = new HiDataBag[NB_ORIENTATIONS];
            for (int i = 0; i < bags.length; i++) {
                bags[i] = new HiDataBag();
                bagBuilders[i] = new HiDataBagBuilder(bags[i]);
            }

            WalkerPaper w = new WalkerPaper(hash, res, res);

            int bit = 0;
            byte[] curByte = new byte[NB_ORIENTATIONS];
            Arrays.fill(curByte, (byte) 0);

            byte[] data = new byte[w.getBitMax() / 8 + 1];
            Arrays.fill(data, (byte) 0);

            try {
                while (true) {
                    int nbInvalidBags = 0;
                    for (int orientation = o1; orientation < o2; orientation++) {
                        if (bagBuilders[orientation] == null) {
                            nbInvalidBags++;
                            if (nbInvalidBags == NB_ORIENTATIONS) {
                                throw new NoBagException();
                            }
                            continue;
                        }

                        int x = w.getTmpPixXOriented(orientation >> 1);
                        int y = w.getTmpPixYOriented(orientation >> 1);

                        int b2 = findMark(img, x, y, res, res, (orientation & 1) > 0, params);
//                LOG.debug("x = " + x + " ; y = " + y + " ; b2 : " + b2);
                        curByte[orientation] |= b2 << bit;

                        if (bit == 6) {
                            try {
//                                LOG.debug("Byte[" + orientation + "] : " + String.format("%02X", curByte[orientation] & 0xff) + " / " + Character.toString((char) curByte[orientation]));
                                bagBuilders[orientation].write(curByte[orientation]);
                            } catch (NoBagException e) {
                                bagBuilders[orientation] = null;
                                //LOG.debug("No bag in orientation " + orientation);
                            } catch (BagParseFinishException e) {
                                LOG.debug("Bag parsed");
                                HiDataBag theBag = bags[orientation];
                                theBag.decryptAll(params);
                                if (theBag.hasUnencryptedItem()) {
                                    // At least one data is decyphered
                                    LOG.info("tryDecode success @ threshold " + t);
                                    return theBag;
                                }
                                if (o == -1) {
                                    // Enter fine tune pass
                                    try {
                                        params.setThresholdDecodeRadon(t);
                                        theBag = tryDecode(img, res, params, orientation);
                                        if (theBag.hasUnencryptedItem()) {
                                            // At least one data is decyphered
                                            LOG.info("tryDecode success after fine pass ");
                                            return theBag;
                                        }
                                    } catch (Exception e2) {
                                        LOG.debug("Secret corrupted : " + e2.getClass().toString());
                                        return null;
                                    }
                                }
                                bagBuilders[orientation] = null;
                            }
                        }
                    }

                    if (bit == 6) {
                        Arrays.fill(curByte, (byte) 0);
                        bit = 0;
                    } else {
                        bit += 2;
                    }

                    if (!w.inc()) {
                        // No more space
                        break;
                    }
                }
            } catch (NoBagException e) {
                // NO OP
            }
        }
        throw new TruncatedBagException();
    }

    public static int computeLuminosity(int[] pix, int from, int to) {
        int lum = 0;
        for (int c = from; c < to; c++) {
            lum += pix[c];
        }
        lum /= (to - from);
        return lum;
    }

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
                int lum = computeLuminosity(pix, 0, 3);
                if (lum < threshold) {
                    bw[idxByte] = 0;
                }
                idxByte++;
            }
        }

        return bw;
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


    public HiDataBag decode(BufferedImage img, ParamPaper p) throws Exception {

        byte[] bw = convertImgToMono(img, p.getMonoThreshold());

        if (DEBUG_GRAPH) {
            BufferedImage imgBW = convertImgMonoToImage(img, bw);
            ImageIO.write(imgBW, "png", new File("bw.png"));
        }

        ShapeDetector detector = new ShapeDetector();
        detector.detect(bw, img.getWidth(), img.getHeight());

        if (DEBUG_GRAPH) {
            BufferedImage imgDetector = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            imgDetector.getGraphics().drawImage(img, 0, 0, null);
            detector.draw(imgDetector);
            ImageIO.write(imgDetector, "png", new File("detect.png"));
        }

        List<Shape> lst = detector.getLstShapes();
        Shape outer = null;
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

        BufferedImage imgExt = extractImage(img, outer, p);

        if (DEBUG_GRAPH) {
            ImageIO.write(imgExt, "png", new File("ext.png"));
        }

//        readAllMarks(imgExt, 224, p);
//        return null;
//        return tryDecode(imgExt, 96, p, -1);

        for (int res = 32; res <= 512; res += 32) {
            try {
                HiDataBag bag = tryDecode(imgExt, res, p, -1);
                LOG.info("Secret found @ res " + res);
                if (bag.verifyHash())
                    return bag;
                else
                    LOG.debug("Hash mismatch");
            } catch (IOException e) {
                // No secret found at this resolution, try next
            } catch (GeneralSecurityException e) {
                LOG.info("Secret found but malformed.");
                break;
            }
        }

        return null;
    }
}
