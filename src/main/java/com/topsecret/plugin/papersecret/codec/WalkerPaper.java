package com.topsecret.plugin.papersecret.codec;

import com.secretlib.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * @author FFRADET
 */
public class WalkerPaper {

    private static final Log LOG = new Log(WalkerPaper.class);

    // Current values to compute coordinates
    private int idxDataBit;
    private int idxHash;
    private byte[] hash;
    private byte[] usedIdx = null;
    private int nbUsed = 0;
    private int idxPixel;

    // Input fixed values
    private int bitMax;

    private int exploitWidth;
    private int exploitHeight;

    private int side;

    public static final int BORDER = 0;
    public static final int BORDER_INT = BORDER+1;

    // Coordinates
    private int tmpPixX = 0;
    private int tmpPixY = 0;


    public WalkerPaper(byte[] hash, int w, int h) {
        this.hash = hash;

        idxHash = Math.abs((int) hash[0]);
        idxHash %= hash.length;

        idxPixel = Math.abs(hash[idxHash]);
        idxDataBit = 0;
        updateInitParams(w, h);
    }

    public void clearUsedIdx() {
        Arrays.fill(usedIdx, (byte) 0);
        nbUsed = 0;
    }

    public boolean inc() {
        if (nbUsed >= bitMax) {
            return false;
        }
        idxHash %= hash.length;
        idxPixel += (hash[idxHash++] & 0xFF) << 8;
        adjust();

        int iUsedIdxByte = idxPixel >> 3;
        int iUsedIdxBit = 1 << (idxPixel & 7);
        usedIdx[iUsedIdxByte] |= iUsedIdxBit;
        nbUsed++;

        idxDataBit++;

        updateTmp();
        return true;
    }


    private void adjust() {

        idxPixel %= bitMax;

        int iUsedIdxByte = idxPixel >> 3;
        int iUsedIdxBit = 1 << (idxPixel & 7);

        while ((usedIdx[iUsedIdxByte] & iUsedIdxBit) > 0) {
            idxPixel++;

            idxPixel %= bitMax;

            iUsedIdxByte = idxPixel >> 3;
            iUsedIdxBit = 1 << (idxPixel & 7);
        }
    }


    private void updateTmp() {
        tmpPixX = BORDER_INT + 1 + (idxPixel % exploitWidth) * 2;
        tmpPixY = BORDER_INT + 1 + (idxPixel / exploitWidth) * 2;
    }


    public void updateInitParams(int w, int h) {
        side = w;
        exploitWidth = (w - 3) / 2;
        exploitHeight = (h - 3) / 2;
        bitMax = exploitWidth * exploitHeight;
        usedIdx = new byte[bitMax / 8 + 1];

        clearUsedIdx();
        adjust();

        int iUsedIdxByte = idxPixel >> 3;
        int iUsedIdxBit = 1 << (idxPixel & 7);
        usedIdx[iUsedIdxByte] |= iUsedIdxBit;
        nbUsed++;

        updateTmp();
    }

    /**
     * Must only be used when exploitWidth = exploitHeight
     * @param o the orientation (0..3)
     * @return the rotated X coordinate
     */
    public int getTmpPixXOriented(int o) {
        int c = tmpPixX;
        if ((o & 1) > 0) {
            c = side-1 - tmpPixY;
        }
        if ((o & 2) > 0) {
            c = side-1 - c;
        }
        return c;
    }

    /**
     * Must only be used when exploitWidth = exploitHeight
     * @param o the orientation (0..3)
     * @return the rotated Y coordinate
     */
    public int getTmpPixYOriented(int o) {
        int c = tmpPixY;
        if ((o & 1) > 0) {
            c = tmpPixX;
        }
        if ((o & 2) > 0) {
            c = side-1 - c;
        }
        return c;
    }

    // Debug only
    public static void main(String a[]) {
        Log.setLevel(Log.DEBUG);
        WalkerPaper w = new WalkerPaper("A".getBytes(StandardCharsets.UTF_8), 10, 10);
        for (int i = 0; i < 4; i++) {
            LOG.debug("x[" + i + "] = " + w.getTmpPixXOriented(i));
            LOG.debug("y[" + i + "] = " + w.getTmpPixYOriented(i));
        }
    }

    public int getBitMax() {
        return bitMax;
    }

    public int getTmpPixX() {
        return tmpPixX;
    }

    public int getTmpPixY() {
        return tmpPixY;
    }

    public int getIdxDataBit() {
        return idxDataBit;
    }
}
