package com.topsecret.plugin.papersecret.sandbox;

import com.secretlib.exception.HiDataEncodeSpaceException;
import com.secretlib.model.ProgressMessage;
import com.secretlib.model.ProgressStepEnum;
import com.secretlib.util.HiUtils;
import com.secretlib.util.Log;
import com.secretlib.util.Parameters;
import com.topsecret.plugin.papersecret.codec.WalkerPaper;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author FFRADET
 */
public class EncoderPaperV2 {

    private static final Log LOG = new Log(EncoderPaperV2.class);

    private static final boolean DEBUG_GRAPH = true;

    int[][] luminosity;
    int[][][] avgColor;

    /**
     * @param r
     * @param x1
     * @param y1
     * @param x2
     * @param y2
     * @return
     */
    public static int computeAvgLuminosity(WritableRaster r, int x1, int y1, int x2, int y2) {
        int[] pix = r.getPixels(x1, y1, x2 - x1, y2 - y1, (int[]) null);
        int nbComp = r.getNumBands();
        int sum = 0;
        int nb = 0;
        for (int i = 0; i < pix.length; i++) {
            if ((nbComp == 4) && (i % nbComp == 3)) {
                // skip alpha component
                continue;
            }
            sum += pix[i] & 0xff;
            nb++;
        }
        return sum / nb;
    }

    public static int[][] computeImageLuminosity(BufferedImage img, int w, int h) {
        int[][] luminosity = new int[h][w];

        int x1 = 0;
        for (int x = 0; x < w; x++) {
            int x2 = (x + 1) * img.getWidth() / w;
            int y1 = 0;
            for (int y = 0; y < h; y++) {
                int y2 = (y + 1) * img.getHeight() / h;
                luminosity[y][x] = computeAvgLuminosity(img.getRaster(), x1, y1, x2, y2);
                y1 = y2;
            }
            x1 = x2;
        }

        return luminosity;
    }

    public static int computeAvgLuminosityAround(int[][] luminosity, int x, int y, int w, int h) {
        int nb = 0;
        int lum = 0;
        for (int _y = y - 1; _y <= y + 1; _y++) {
            if ((_y < 0) || (_y >= h)) {
                continue;
            }

            for (int _x = x - 1; _x <= x + 1; _x++) {
                if ((_x < 0) || (_x >= w)) {
                    continue;
                }
                if ((_x == x) && (_y == y)) {
                    continue;
                }
                lum += luminosity[_y][_x];
                nb++;
            }
        }
        lum /= nb;
        return lum;
    }

    private static void setValue(BufferedImage img, Graphics g, int x, int y, int w, int h, Color c) {
        int x1 = x * img.getWidth() / w;
        int x2 = (x + 1) * img.getWidth() / w;
        int y1 = y * img.getHeight() / h;
        int y2 = (y + 1) * img.getHeight() / h;
        int blocW = x2 - x1;
        int blocH = y2 - y1;
        g.setColor(c);
        g.fillRect(x1,y1,blocW, blocH);
    }

    private static void setLuminosity(BufferedImage img, int[][] luminosity, int x, int y, int w, int h, int newLum) {
        int x1 = x * img.getWidth() / w;
        int x2 = (x + 1) * img.getWidth() / w;
        int y1 = y * img.getHeight() / h;
        int y2 = (y + 1) * img.getHeight() / h;
        int blocW = x2 - x1;
        int blocH = y2 - y1;
        int area = blocW * blocH;
        WritableRaster r = img.getRaster();
        int nbComp = r.getNumBands();
        int lastComp = Math.min(3, nbComp);
        int areaComp = area * lastComp;

        int lumStart = luminosity[y][x];
        int lumDelta = (newLum - lumStart) * areaComp;

        int[] pix = r.getPixels(x1, y1, blocW, blocH, (int[]) null);
        int lumDeltaRem = Math.abs(lumDelta);
        while (lumDeltaRem > 0) {
            for (int i = 0; i < pix.length; i += nbComp) {
                if (lumDeltaRem == 0) {
                    break;
                }
                for (int c = i; c < i + lastComp; c++) {
                    int localLumDelta = lumDelta / areaComp;
                    if (localLumDelta == 0) {
                        localLumDelta = (lumDelta > 0) ? 1 : -1;
                    }
                    int oldPix = pix[c];
                    pix[c] += localLumDelta;

                    if (pix[c] > 0xff) {
                        pix[c] = 0xff;
                    }
                    if (pix[c] < 0) {
                        pix[c] = 0x00;
                    }
                    lumDeltaRem -= Math.abs(pix[c] - oldPix);
                    if (lumDeltaRem == 0) {
                        break;
                    }
                }
            }
            lumDelta = (lumDelta > 0) ? lumDeltaRem : -lumDeltaRem;
        }
        r.setPixels(x1, y1, blocW, blocH, pix);

        int checkSum = computeAvgLuminosity(r, x1, y1, x2, y2);
        if (checkSum != newLum) {
            LOG.debug("checkSum error at x1 = " + x1 + " ; y1 = " + y1 + " : " + checkSum + " / " + newLum);
        }
    }
/*
    public int[] computeAvgColorAround(WritableRaster r, int x1, int y1, int x2, int y2) {
        int w = x2 - x1;
        int h = y2 - y1;
        int area = w * h;
        int nbComp = r.getNumBands();
        int[] pix = r.getPixels(x1, y1, w, h, (int[]) null);
        int[] avg = new int[nbComp];
        for (int i = 0; i < pix.length; i++) {
            avg[i % nbComp] += pix[i] & 0xff;
        }
        for (int c = 0; c < avg.length; c++) {
            avg[c] /= area;
        }
        return avg;
    }

    public void computeImageColor(BufferedImage img, int w, int h) {
        int nbComp = img.getColorModel().getNumComponents();
        avgColor = new int[h][w][nbComp];

        int x1 = 0;
        for (int x = 0; x < w; x++) {
            int x2 = (x + 1) * img.getWidth() / w;
            int y1 = 0;
            for (int y = 0; y < h; y++) {
                int y2 = (y + 1) * img.getHeight() / h;
                avgColor[y][x] = computeAvgColorAround(img.getRaster(), x1, y1, x2, y2);
                y1 = y2;
            }
            x1 = x2;
        }
    }

    private void computeAvgColorAround(int x, int y, int w, int h, int[] pix) {
        Arrays.fill(pix, 0);
        int nb = 0;
        for (int _y = y - 1; _y <= y + 1; _y++) {
            if ((_y < 0) || (_y >= h)) {
                continue;
            }

            for (int _x = x - 1; _x <= x + 1; _x++) {
                if ((_x < 0) || (_x >= w)) {
                    continue;
                }
                if ((_x == x) && (_y == y)) {
                    continue;
                }
                for (int c = 0; c < pix.length; c++) {
                    pix[c] += avgColor[_y][_x][c];
                }
                nb++;
            }
        }

        for (int c = 0; c < pix.length; c++) {
            pix[c] /= nb;
        }
    }
*/
    public BufferedImage encode(BufferedImage img, byte[] data, Parameters params) throws HiDataEncodeSpaceException {
        LOG.begin("encode");

        BufferedImage imgDebug = null;
        Graphics gd = null;
        if (DEBUG_GRAPH) {
            imgDebug = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());
            gd = imgDebug.getGraphics();
            gd.drawImage(img, 0, 0, null);
        }
        BufferedImage imgOut = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());
        Graphics g = imgOut.getGraphics();
        g.drawImage(img, 0, 0, null);

        g.setColor(Color.BLACK);
        g.drawRect(0, 0, img.getWidth() - 1, img.getHeight() - 1);

        int res = (Math.min(img.getWidth(), img.getHeight()) / 8) & ~0x1F;
        if (res < 32) {
            throw new HiDataEncodeSpaceException();
        }
        if (res > 512) {
            res = 512;
        }

        LOG.debug("res : " + res);
        luminosity = computeImageLuminosity(imgOut, res, res);

        boolean bInterrupted = false;

        if (data.length > 0) {
            int nbComp = imgOut.getColorModel().getNumComponents();
            byte[] pass = params.getKm().getBytes(StandardCharsets.UTF_8);
            byte[] hash = HiUtils.genHash(pass, params.getHashAlgo());

            WalkerPaper w = new WalkerPaper(hash, res, res);

            int octMax = w.getBitMax() / 8;
            int nbChanged = 0;
            LOG.debug("Total space per pixel's component bit (bytes) = " + octMax);
            LOG.debug("Space required (bytes) = " + data.length);

            LOG.debug("Writing data : " + HiUtils.toStringHex(data, 16));

            WritableRaster r = imgOut.getRaster();

            long timeStart = System.currentTimeMillis();
            ProgressMessage msg = new ProgressMessage(ProgressStepEnum.ENCODE, 0);
            for (int i = 0; i < 8; i++) {
                msg.setNbBitsTotal(w.getBitMax(), i);
            }

            msg.setNbBitsCapacity(data.length * 8);

            int markIntensity = 0x90;
            Color dark = new Color(0, 0, 0, markIntensity);
            Color light = new Color(255,255,255, markIntensity);

            int _x = -1;
            int _y = -1;
            int bit = 1;
            int idxData = 0;
            byte curByte = (byte) data[0];
            while (w.getIdxDataBit() < data.length * 8) {
                int x = w.getTmpPixX();
                int y = w.getTmpPixY();

                boolean flag = (curByte & bit) > 0;
                int lumAvg = computeAvgLuminosityAround(luminosity, x, y, res, res);
/*
                int lum = luminosity[y][x];
                LOG.debug("x = " + x + " ; y = " + y + " ; lumAvg = " + lumAvg + " ; lum = " + lum + " ; deltaLum = " + (Math.abs(lumAvg-lum)) + " ; flag : " + flag);
*/

                if (flag) {
                    setValue(imgOut, g, x, y, res, res, (lumAvg >= 0x80) ? dark : light);
//                    LOG.debug("Changed to " + lumAvg);
                    nbChanged++;
                } else {
                    // Average = bit 0
                    setLuminosity(imgOut, luminosity, x, y, res, res, lumAvg);
                }

                if (DEBUG_GRAPH) {
                    int x1 = x * img.getWidth() / res;
                    int x2 = (x + 1) * img.getWidth() / res;
                    int y1 = y * img.getHeight() / res;
                    int y2 = (y + 1) * img.getHeight() / res;
                    int nx = (x1 + x2) / 2;
                    int ny = (y1 + y2) / 2;
                    if (_x >= 0) {
                        gd.setColor(Color.BLUE);
                        gd.drawLine(_x, _y, nx, ny);
                    }
                    _x = nx;
                    _y = ny;
                    gd.setColor(flag ? Color.GREEN : Color.RED);
                    gd.drawRect(x1, y1, x2 - x1 - 1, y2 - y1 - 1);
                }


                if (bit == 0x80) {
                    idxData++;
                    if (idxData < data.length) {
                        curByte = (byte) data[idxData];
                    }
                    bit = 1;
                } else {
                    bit <<= 1;
                }

                if ((idxData & 0x01ff) == 0) {
                    long timeCur = System.currentTimeMillis();
                    if (timeCur - timeStart > 100) {
                        timeStart = timeCur;
                        msg.setProgress((double) idxData / (double) (data.length));
                        msg.setNbBitsChanged(nbChanged);
                        msg.setNbBitsUsed(w.getIdxDataBit());
                        params.getProgressCallBack().update(msg);
                    }
                }

                if (!w.inc()) {
                    // No more space
                    bInterrupted = true;
                    break;
                }
            }

            int nbBitsStored = 0;
            int nbBitsChanged = 0;

            if (!bInterrupted) {
                nbBitsStored = w.getIdxDataBit();
                nbBitsChanged = nbChanged;
            }

            msg.setProgress((double) idxData / (double) (data.length));
            msg.setNbBitsUsed(nbBitsStored);
            msg.setNbBitsChanged(nbBitsChanged);
            params.getProgressCallBack().update(msg);
        }

        if (DEBUG_GRAPH) {
            try {
                ImageIO.write(imgDebug, "png", new File("encoder.png"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (bInterrupted) {
            throw new HiDataEncodeSpaceException();
        }
        LOG.end("encode");
        return imgOut;
    }

}
