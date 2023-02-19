package com.topsecret.paper.codec;

import com.secretlib.exception.HiDataEncodeSpaceException;
import com.secretlib.model.ProgressMessage;
import com.secretlib.model.ProgressStepEnum;
import com.secretlib.util.HiUtils;
import com.secretlib.util.Log;
import com.topsecret.paper.util.ParamPaper;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * @author FFRADET
 */
public class EncoderPaper {

    private static final Log LOG = new Log(EncoderPaper.class);

    private static final boolean DEBUG_GRAPH = true;


    private static class CleanReturn {
        int avg;
        boolean cleaned;
    }

    /**
     * @param img
     * @param g
     * @param x
     * @param y
     * @param w
     * @param h
     * @param val2b
     * @param p
     * @param ret
     */
    private static void setValue(BufferedImage img, Graphics g, int x, int y, int w, int h, int val2b, ParamPaper p, CleanReturn ret) {
        int x1 = x * img.getWidth() / w;
        int x2 = (x + 1) * img.getWidth() / w;
        int y1 = y * img.getHeight() / h;
        int y2 = (y + 1) * img.getHeight() / h;

        int lx = x2 - x1;
        int ly = y2 - y1;

        x1 -= lx / 4;
        y1 -= ly / 4;
        x2 += lx / 4;
        y2 += ly / 4;

        cleanArea(img, g, x1, y1, x2, y2, p, ret);
        int deltaComp = p.getThresholdEncodeColor();
        int newColComp = (ret.avg >= 0x80) ? ret.avg - deltaComp : ret.avg + deltaComp;
        Color c = new Color(newColComp, newColComp, newColComp);

        LOG.debug("setValue " + val2b + " @ " + x1 + "," + y1 + "," + x2 + "," + y2);

        g.setColor(c);
        if ((val2b & 1) > 0) {
            // Vertical
            int m = (x1 + x2) / 2;
            g.drawLine(m, y1, m, y2 - 1);
        }
        if ((val2b & 2) > 0) {
            // Horizontal
            int m = (y1 + y2) / 2;
            g.drawLine(x1, m, x2 - 1, m);
        }
    }

    private static final int[][] GAUSSIAN_MATRIX = new int[][]
            {{2, 4, 5, 4, 2},
                    {4, 9, 12, 9, 4},
                    {5, 12, 15, 12, 5},
                    {4, 9, 12, 9, 4},
                    {2, 4, 5, 4, 2}
            };

    private static final int GAUSSIAN_MATRIX_X = GAUSSIAN_MATRIX[0].length;
    private static final int GAUSSIAN_MATRIX_Y = GAUSSIAN_MATRIX.length;

    private static final int GAUSSIAN_SUM;

    static {
        int sum = 0;
        for (int y = 0; y < GAUSSIAN_MATRIX_Y; y++) {
            for (int x = 0; x < GAUSSIAN_MATRIX_X; x++) {
                sum += GAUSSIAN_MATRIX[y][x];
            }
        }
        GAUSSIAN_SUM = sum;
    }

    private static void applyFilter(int[] pixelsComp, int nbComp, int blocW, int blocH, int ic, int x, int y, int[] newcolor) {
        int x1 = x - GAUSSIAN_MATRIX_X / 2;
        int y1 = y - GAUSSIAN_MATRIX_Y / 2;

        Arrays.fill(newcolor, 0);

        for (int _y = 0; _y < GAUSSIAN_MATRIX_Y; _y++) {
            int ay = _y + y1;
            ay = Math.max(0, ay);
            ay = Math.min(blocH - 1, ay);
            for (int _x = 0; _x < GAUSSIAN_MATRIX_X; _x++) {
                int ax = _x + x1;
                ax = Math.max(0, ax);
                ax = Math.min(blocW - 1, ax);
                newcolor[ic] += pixelsComp[(ay * blocW + ax) * nbComp + ic] * GAUSSIAN_MATRIX[_y][_x];
            }
        }

        newcolor[ic] /= GAUSSIAN_SUM;
    }

    private static void gaussianFilter(int[] pixelsComp, int nbComp, int blocW, int blocH, int ic) {
        int[] save = Arrays.copyOf(pixelsComp, pixelsComp.length);

        int[] col = new int[3];
        for (int _y = 0; _y < blocH; _y++) {
            for (int _x = 0; _x < blocW; _x++) {
                applyFilter(save, nbComp, blocW, blocH, ic, _x, _y, col);
                pixelsComp[(_x + _y * blocW) * nbComp + ic] = col[ic];
            }
        }

    }


    private static void extractPixelsComponent(int[] pixelsComp, int nbComp, int blocW, int[][] pixels, int[] avgComps, int ic) {
        avgComps[ic] = 0;
        int avgNb = 0;
        int _x = 0;
        int _y = 0;
        for (int i = 0; i < pixelsComp.length; i += nbComp, _x++) {
            if (_x == blocW) {
                _x = 0;
                _y++;
            }
            avgComps[ic] += pixelsComp[i + ic];
            pixels[_y][_x] = pixelsComp[i + ic];
            avgNb++;
        }
        avgComps[ic] /= avgNb;
    }

    /**
     * @param img
     * @param g
     * @param x1
     * @param y1
     * @param x2
     * @param y2
     * @param p
     * @param ret
     */
    private static void cleanArea(BufferedImage img, Graphics g, int x1, int y1, int x2, int y2, ParamPaper p, CleanReturn ret) {
        int blocW = x2 - x1;
        int blocH = y2 - y1;

        int nbComp = img.getColorModel().getNumComponents();
        int nbCompColor = Math.min(3, nbComp); // Exclude Alpha
        int[] pixelsComp = img.getRaster().getPixels(x1, y1, blocW, blocH, (int[]) null);

        int[][] pixels = new int[blocH][blocW];
        int[] avgComps = new int[nbCompColor];

        int out = 1;
        while (out != 0) {
            out = 0;
            for (int ic = 0; ic < avgComps.length; ic++) {
                extractPixelsComponent(pixelsComp, nbComp, blocW, pixels, avgComps, ic);
                int outc = DecoderPaper.computeFastRadonTransform(pixels, p.getThresholdEncodeRadonClean());
                if (outc != 0) {
                    gaussianFilter(pixelsComp, nbComp, blocW, blocH, ic);
                    ret.cleaned = true;
                }
                out += outc;
            }
        }

        img.getRaster().setPixels(x1, y1, blocW, blocH, pixelsComp);

        int avg = 0;
        for (int ic = 0; ic < avgComps.length; ic++) {
            avg += avgComps[ic];
        }
        avg /= avgComps.length;
        ret.avg = avg;
    }

    public BufferedImage encode(BufferedImage img, byte[] data, ParamPaper params) throws Exception {
        LOG.begin("encode");

        if (img.getColorModel().getNumComponents() < 3) {
            throw new Exception("RGB color components is the minimum required.");
        }

        BufferedImage imgDebug = null;
        Graphics gd = null;
        if (DEBUG_GRAPH) {
            imgDebug = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());
            gd = imgDebug.getGraphics();
            gd.drawImage(img, 0, 0, null);
        }

        int iw = img.getWidth();
        int ih = img.getHeight();
        int ow = params.getOutputWidth();
        int oh = params.getOutputHeight();

        if (iw < ih) {
            int i = iw;
            iw = ih;
            ih = i;
        }

        if (ow < oh) {
            int i = ow;
            ow = oh;
            oh = i;
        }

        // Here ow >= oh and iw >= ih

        if (iw > ow) {
            ih = ih * ow / iw;
            iw = ow;
        }

        if (ih > oh) {
            iw = iw * oh / ih;
            ih = oh;
        }

        BufferedImage imgOut = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_RGB);
        Graphics g = imgOut.getGraphics();
        // TODO set background color for Alpha PNG
        g.drawImage(img, 0, 0, iw, ih, null);

        g.setColor(Color.BLACK);
        g.drawRect(0, 0, iw - 1, ih - 1);

        int res = (Math.min(iw, ih) / 8) & ~0x1F;
        if (res < 32) {
            throw new HiDataEncodeSpaceException();
        }
        if (res > 512) {
            res = 512;
        }

        LOG.debug("res : " + res);

        boolean bInterrupted = false;

        if (data.length > 0) {
            byte[] pass = params.getKm().getBytes(StandardCharsets.UTF_8);
            byte[] hash = HiUtils.genHash(pass, params.getHashAlgo());

            WalkerPaper w = new WalkerPaper(hash, res, res);

            int octMax = (w.getBitMax() * 2) / 8; // 2 bits per position
            int nbChanged = 0;
            LOG.debug("Total space per pixel's component bit (bytes) = " + octMax);
            LOG.debug("Space required (bytes) = " + data.length);

            LOG.debug("Writing data : " + HiUtils.toStringHex(data, 16));

            WritableRaster r = imgOut.getRaster();

            long timeStart = System.currentTimeMillis();
            ProgressMessage msg = new ProgressMessage(ProgressStepEnum.ENCODE, 0);
            for (int i = 0; i < 8; i++) {
                msg.setNbBitsTotal(w.getBitMax() * 2, i);
            }

            msg.setNbBitsCapacity(data.length * 8);

            int _x = -1;
            int _y = -1;
            int bit = 0;
            int idxData = 0;
            byte curByte = (byte) data[0];
            CleanReturn ret = new CleanReturn();
            while (idxData < data.length) {
                int x = w.getTmpPixX();
                int y = w.getTmpPixY();

                int b2 = (curByte >> bit) & 0x03;
                bit += 2;

                setValue(imgOut, g, x, y, res, res, b2, params, ret);

                if ((ret.cleaned) || (b2 != 0)) {
                    nbChanged++;
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
                    gd.setColor(Color.GREEN);
                    gd.drawRect(x1, y1, x2 - x1 - 1, y2 - y1 - 1);
                }


                if (bit == 8) {
                    idxData++;
                    if (idxData < data.length) {
                        curByte = (byte) data[idxData];
                    }
                    bit = 0;
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
                nbBitsStored = w.getIdxDataBit() * 2;
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
