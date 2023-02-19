package com.topsecret.paper.sandbox;

import com.secretlib.model.ProgressMessage;
import com.secretlib.model.ProgressStepEnum;
import com.secretlib.util.HiUtils;
import com.secretlib.util.Log;
import com.secretlib.util.Parameters;
import com.topsecret.paper.codec.WalkerPaper;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * @author FFRADET
 */
public class EncoderPaperV1 {

    private static final Log LOG = new Log(EncoderPaperV1.class);

    private int widthOdd;
    private int heightOdd;

    private static boolean isRGBLower(int[] pix, int r, int g, int b) {
        int pr = pix[0] & 0xff;
        int pg = pix[1] & 0xff;
        int pb = pix[2] & 0xff;

        return ((pr < r) || (pg < g) || (pb < b));
    }

    private void printEye(Graphics g, int x, int y, int size) {
        g.setColor(Color.BLACK);
        g.fillRect(x, y, size, size);

        g.setColor(Color.WHITE);
        g.drawRect(x, y, size, size);
        g.drawRect(x+2, y+2, size-4, size-4);
        g.drawRect(x+4, y+4, size-8, size-8);
    }

    private void printRules(BufferedImage img) {
/*
        for (int b = 0; b < WalkerPaper.BORDER; b++) {
            for (int i = 0; i < r.getWidth(); i++) {
                int c = ((i < WalkerPaper.BORDER) || (i >= r.getWidth()-WalkerPaper.BORDER)) ? 0 : (i == r.getWidth()-WalkerPaper.BORDER-1) ? 1 : (i & 1);
                r.setPixel(i, b, BITS[c]);
                r.setPixel(i, r.getHeight() - 1-b, BITS[c]);
            }

            for (int i = 0; i < r.getHeight(); i++) {
                int c = ((i < WalkerPaper.BORDER) || (i >= r.getHeight()-WalkerPaper.BORDER)) ? 0 : (i == r.getHeight()-WalkerPaper.BORDER-1) ? 1 : (i & 1);
                r.setPixel(b, i, BITS[c]);
                r.setPixel(r.getWidth() - 1-b, i, BITS[c]);
            }
        }
 */
        Graphics g = img.getGraphics();
        g.setColor(Color.BLACK);
        for (int i = 0; i < WalkerPaper.BORDER; i++) {
            g.drawRect(i, i, img.getWidth() - 1 - i*2, img.getHeight() - 1 - i*2);
        }

        int rule = WalkerPaper.BORDER-1;
        for (int i = 0; i < widthOdd - rule * 2; i++) {
            g.setColor(((i & 1) == 0) ? Color.WHITE : Color.BLACK);
            g.drawLine(i + rule, rule, i + rule, rule);
            g.drawLine(i + rule, heightOdd-1-rule, i + rule, heightOdd-1-rule);
        }

        for (int i = 0; i < heightOdd - rule * 2; i++) {
            g.setColor(((i & 1) == 0) ? Color.WHITE : Color.BLACK);
            g.drawLine(rule, i + rule, rule, i + rule);
            g.drawLine(widthOdd-1-rule, i + rule, widthOdd-1-rule, i + rule);
        }
        
/*
        int size = WalkerPaper.BORDER-1;
        printEye(g, 1, 1, size);
        printEye(g, img.getWidth()-size-2, 1, size);
        printEye(g, 1, img.getHeight()-size-2, size);
        printEye(g, img.getWidth()-size-2, img.getHeight()-size-2, size);
*/    }


    // TODO : compare average color with pixel color : diff > threshold : bit 1 ; 0 otherwise

    /**
     * compute average color around x,y
     * @param r
     * @param x
     * @param y
     * @return
     */
    public static void computeAvgColor(WritableRaster r, int x, int y, int nbComp, int[] avg) {
//        LOG.debug("x = " + x + " ; y = " + y);
        r.getPixels(x-1, y-1, 3,3, avg);

        Arrays.fill(avg, 4*nbComp, 5*nbComp, 0);

        int c = 0;
        for (int i = 0; i < 9*nbComp; i++, c++) {
            if (c == nbComp) {
                c = 0;
            }
            if ((i<4*nbComp) || (i>=5*nbComp)) {
                avg[4*nbComp+c] += avg[i];
            }
        }
        for (c = 0; c < nbComp; c++) {
            avg[4*nbComp+c] /= 8;
        }
    }

    /**
     *
     * @param pix
     * @return
     */
    public static int computeLuminosity(int[] pix, int from, int to) {
        int lum = 0;
        for (int c = from; c < to; c++) {
            lum += pix[c];
        }
        lum /= (to - from);
        return lum;
    }

    /**
     *
     * @param avg
     * @param pix
     * @return
     */
    private boolean isBit(int[] avg, int[] pix) {
        // TODO
        return false;
    }


    public BufferedImage encode(BufferedImage img, byte[] data, Parameters params) {
        LOG.begin("encode");

        widthOdd = img.getWidth();
        heightOdd = img.getHeight();
        if ((widthOdd & 1) == 0) {
            widthOdd--;
        }
        if ((heightOdd & 1) == 0) {
            heightOdd--;
        }

        BufferedImage imgOut = new BufferedImage(widthOdd, heightOdd, img.getType());
        imgOut.getGraphics().drawImage(img, 0,0, null);

        boolean bInterrupted = false;

        if (data.length > 0) {
            int nbComp = imgOut.getColorModel().getNumComponents();
            byte[] pass = params.getKm().getBytes(StandardCharsets.UTF_8);
            byte[] hash = HiUtils.genHash(pass, params.getHashAlgo());

            WalkerPaper w = new WalkerPaper(hash, widthOdd, heightOdd);

            int idxHash = (pass.length > 0) ? (((int) pass[0]) & 0xff) : 0xff;
            idxHash %= hash.length;

            int octMax = w.getBitMax() / 8;
            int nbChanged = 0;
            LOG.debug("Total space per pixel's component bit (bytes) = " + octMax);
            LOG.debug("Space required (bytes) = " + data.length);

            LOG.debug("Writing data : " + HiUtils.toStringHex(data, 16));

            WritableRaster r = imgOut.getRaster();
            printRules(imgOut);

            int[] pix = new int[nbComp];
            int[] avg = new int[nbComp*9];

            long timeStart = System.currentTimeMillis();
            ProgressMessage msg = new ProgressMessage(ProgressStepEnum.ENCODE, 0);
            for (int i = 0; i < 8; i++) {
                msg.setNbBitsTotal(w.getBitMax(), i);
            }

            msg.setNbBitsCapacity(data.length * 8);

            int bit = 1;
            int idxData = 0;
            int bitMask = 1 << params.getBitStart();
            byte curByte = (byte) (data[0] ^ hash[idxHash]);
            int thresholdRed = 0x80;
            int thresholdGre = 0x80;
            int thresholdBlu = 0x80;
            while (w.getIdxDataBit() < data.length * 8) {
                int x = w.getTmpPixX();
                int y = w.getTmpPixY();


                boolean flag = (curByte & bit) > 0;
                computeAvgColor(r, x, y, nbComp, avg);

//                r.getPixel(x, y, pix);
//                int lumPix = computeLuminosity(pix, 0, pix.length);
                int lumAvg = computeLuminosity(avg, nbComp*4, nbComp*4+3);

                if (flag) {
                    // scale luminosity to be detectable
                    int delta = 0x20;
                    if (lumAvg >= 0x80) {
                        delta = -delta;
                    }
                    for (int c = 0; c < nbComp; c++) {
                        pix[c] = avg[nbComp*4+c] + delta;
                    }
                    nbChanged++;
                } else {
                    // Average = bit 0
                    for (int c = 0; c < nbComp; c++) {
                        pix[c] = avg[nbComp*4+c];
                    }
                }
/*
                if (flag) {
                    // Write a bit 1
                    if (isRGBLower(pix, thresholdRed, thresholdGre, thresholdBlu)) {
                        // Original image says "bit 0 here!"
                        pix[0] = 0xff;
                        pix[1] = 0xff;
                        pix[2] = 0x00;
                        nbChanged++;
                    } else {
//                        pix[0] = 0;
//                        pix[1] = thresholdGre;
//                        pix[2] = 0;
                    }
                } else {
                    // Write a bit 0
                    if (!isRGBLower(pix, thresholdRed, thresholdGre, thresholdBlu)) {
                        // Original image says "bit 1 here!"
                        pix[0] = thresholdRed;
                        pix[1] = thresholdGre;
                        pix[2] = thresholdBlu;
                        nbChanged++;
                    } else {
//                        pix[0] = thresholdRed;
//                        pix[1] = 0;
//                        pix[2] = 0;
                    }
                }
*/
                r.setPixel(x, y, pix);

                if (bit == 0x80) {
                    idxData++;
                    if (idxData < data.length) {
                        idxHash++;
                        idxHash %= hash.length;
                        curByte = (byte) (data[idxData] ^ hash[idxHash]);
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
                    if (params.isAutoExtendBit()) {
                        bitMask <<= 1;
                        w.clearUsedIdx();
                        w.inc();
                        if (bitMask > 0x80) {
                            // No more space
                            bInterrupted = true;
                            break;
                        }
                    } else {
                        // No more space
                        bInterrupted = true;
                        break;
                    }
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
        if (bInterrupted) {
            imgOut = null;
        }
        LOG.end("encode");
        return imgOut;
    }

}
