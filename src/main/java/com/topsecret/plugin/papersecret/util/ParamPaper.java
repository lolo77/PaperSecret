package com.topsecret.plugin.papersecret.util;

import com.secretlib.util.BatchParameters;
import com.secretlib.util.Log;
import com.secretlib.util.Parameters;

import java.util.Iterator;

public class ParamPaper extends BatchParameters {
    private static final Log LOG = new Log(ParamPaper.class);

    // The higher, the most discrete
    // The lower, the most mean color clean squares
    public static final String THRESHOLD_ENCODE_RADON_CLEAN = "ter";
    private int thresholdEncodeRadonClean = 0x20; // 10

    // The higher, the easier to read
    // The lower, the most discrete
    public static final String THRESHOLD_ENCODE_COLOR = "tec";
    private int thresholdEncodeColor = 0x60; // 40

    public static final String THRESHOLD_DECODE_RADON = "tdr";
    private int thresholdDecodeRadon = thresholdEncodeRadonClean * 2;

    public static final String OUTPUT_WIDTH = "ow";
    private int outputWidth = 2000; // Default 300dpi A4 printable area width (landscape)

    public static final String OUTPUT_HEIGHT = "oh";
    private int outputHeight = 1500; // Default 300dpi A4 printable area height (landscape)

    public static final String EXTRACT_RES = "er";
    private int extractRes = 4096;

    public static final String MONO_THRESHOLD = "mt";
    private int monoThreshold = 0x80;


    public ParamPaper(Parameters p) {
        Integer i = (Integer)p.getExtendedParams().get(MONO_THRESHOLD);
        if (i != null) {
            setMonoThreshold(i);
        }

        i = (Integer)p.getExtendedParams().get(EXTRACT_RES);
        if (i != null) {
            setExtractRes(i);
        }

        i = (Integer)p.getExtendedParams().get(OUTPUT_HEIGHT);
        if (i != null) {
            setOutputHeight(i);
        }

        i = (Integer)p.getExtendedParams().get(OUTPUT_WIDTH);
        if (i != null) {
            setOutputWidth(i);
        }

        i = (Integer)p.getExtendedParams().get(THRESHOLD_DECODE_RADON);
        if (i != null) {
            setThresholdDecodeRadon(i);
        }

        i = (Integer)p.getExtendedParams().get(THRESHOLD_ENCODE_COLOR);
        if (i != null) {
            setThresholdEncodeColor(i);
        }

        i = (Integer)p.getExtendedParams().get(THRESHOLD_ENCODE_RADON_CLEAN);
        if (i != null) {
            setThresholdEncodeRadonClean(i);
        }
    }


    public ParamPaper(String[] args) {
        super(args);
    }

    protected static int parseArgInt(Iterator<String> iter) {
        String s = iter.next();
        try {
            int v = Integer.parseInt(s);
            return v;
        } catch (NumberFormatException e) {
            LOG.error("NumberFormatException : '" + s + "' must be an integer");
            throw e;
        }
    }

    @Override
    protected boolean consumeArgExt(String arg, Iterator<String> iter) {
        super.consumeArgExt(arg, iter);
        try {
            if (THRESHOLD_ENCODE_RADON_CLEAN.equals(arg)) {
                int v = parseArgInt(iter);
                if (v < 0) {
                    LOG.warn("ter (Threshold Encode Radon) must be >= 0 ; set to minimum");
                    v = 0;
                } else if (v > 0xFF) {
                    LOG.warn("ter (Threshold Encode Radon) must be <= 255 ; set to maximum");
                    v = 0xFF;
                }
                thresholdEncodeRadonClean = v;
            }

            if (THRESHOLD_ENCODE_COLOR.equals(arg)) {
                int v = parseArgInt(iter);
                if (v < 0x10) {
                    LOG.warn("tec (Threshold Encode Color) must be >= 16 ; set to minimum");
                    v = 0x10;
                } else if (v > 0x80) {
                    LOG.warn("tec (Threshold Encode Color) must be <= 128 ; set to maximum");
                    v = 0x80;
                }
                thresholdEncodeColor = v;
            }

            if (THRESHOLD_DECODE_RADON.equals(arg)) {
                int v = parseArgInt(iter);
                if (v < 0x20) {
                    LOG.warn("tdr (Threshold Decode Radon) must be >= 32 ; set to minimum");
                    v = 0x20;
                } else if (v > 0x80) {
                    LOG.warn("tdr (Threshold Decode Radon) must be <= 128 ; set to maximum");
                    v = 0x80;
                }
                thresholdDecodeRadon = v;
            }

            if (OUTPUT_WIDTH.equals(arg)) {
                int v = parseArgInt(iter);
                if (v < 128) {
                    LOG.warn("ow (Output Width) must be >= 128 ; set to minimum");
                    v = 128;
                }
                if (v > 8192) {
                    LOG.warn("ow (Output Width) must be <= 8192 ; set to maximum");
                    v = 8192;
                }
                outputWidth = v;
            }

            if (OUTPUT_HEIGHT.equals(arg)) {
                int v = parseArgInt(iter);
                if (v < 128) {
                    LOG.warn("oh (Output Height) must be >= 128 ; set to minimum");
                    v = 128;
                }
                if (v > 8192) {
                    LOG.warn("oh (Output Height) must be <= 8192 ; set to maximum");
                    v = 8192;
                }
                outputHeight = v;
            }
            if (EXTRACT_RES.equals(arg)) {
                int v = parseArgInt(iter);
                if (v < 1024) {
                    LOG.warn("er (Extract Res) must be >= 1024 ; set to minimum");
                    v = 1024;
                }
                if (v > 8192) {
                    LOG.warn("er (Extract Res) must be <= 8192 ; set to maximum");
                    v = 8192;
                }
                extractRes = v;
            }
            if (MONO_THRESHOLD.equals(arg)) {
                int v = parseArgInt(iter);
                if (v < 16) {
                    LOG.warn("mt (Mono Threshold) must be >= 16 ; set to minimum");
                    v = 16;
                }
                if (v > 240) {
                    LOG.warn("mt (Mono Threshold) must be <= 240 ; set to maximum");
                    v = 240;
                }
                monoThreshold = v;
            }
        } catch (RuntimeException e) {
            // NO OP
        }
        return true;
    }

    public int getThresholdEncodeRadonClean() {
        return thresholdEncodeRadonClean;
    }

    public void setThresholdEncodeRadonClean(int thresholdEncodeRadonClean) {
        this.thresholdEncodeRadonClean = thresholdEncodeRadonClean;
    }

    public int getThresholdEncodeColor() {
        return thresholdEncodeColor;
    }

    public void setThresholdEncodeColor(int thresholdEncodeColor) {
        this.thresholdEncodeColor = thresholdEncodeColor;
    }

    public int getThresholdDecodeRadon() {
        return thresholdDecodeRadon;
    }

    public void setThresholdDecodeRadon(int thresholdDecodeRadon) {
        this.thresholdDecodeRadon = thresholdDecodeRadon;
    }

    public int getOutputWidth() {
        return outputWidth;
    }

    public void setOutputWidth(int outputWidth) {
        this.outputWidth = outputWidth;
    }

    public int getOutputHeight() {
        return outputHeight;
    }

    public void setOutputHeight(int outputHeight) {
        this.outputHeight = outputHeight;
    }

    public int getExtractRes() {
        return extractRes;
    }

    public void setExtractRes(int extractRes) {
        this.extractRes = extractRes;
    }

    public int getMonoThreshold() {
        return monoThreshold;
    }

    public void setMonoThreshold(int monoThreshold) {
        this.monoThreshold = monoThreshold;
    }
}
