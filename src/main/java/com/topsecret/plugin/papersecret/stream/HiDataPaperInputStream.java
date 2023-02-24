package com.topsecret.plugin.papersecret.stream;

import com.secretlib.io.stream.HiDataAbstractInputStream;
import com.secretlib.util.Log;
import com.secretlib.util.Parameters;
import com.topsecret.plugin.papersecret.codec.DecoderPaper;
import com.topsecret.plugin.papersecret.detector.exception.DetectorNoShapeException;
import com.topsecret.plugin.papersecret.detector.exception.DetectorShapeNotARectangleException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HiDataPaperInputStream extends HiDataAbstractInputStream {
    private static final Log LOG = new Log(HiDataPaperInputStream.class);

    static final String CODEC_NAME = "Paper/1";

    static final List<String> EXTENTIONS = new ArrayList();

    public HiDataPaperInputStream() {

    }

    @Override
    public String getCodecName() {
        return CODEC_NAME;
    }

    @Override
    public String getOutputCodecName() {
        return HiDataPaperOutputStream.CODEC_NAME;
    }

    @Override
    public List<String> getExtensions() {
        return Collections.unmodifiableList(EXTENTIONS);
    }


    @Override
    public boolean matches(byte[] bytes) {
        ByteArrayInputStream buf = new ByteArrayInputStream(bytes);
        try {
            ImageIO.read(buf);
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    @Override
    public HiDataAbstractInputStream create(InputStream inputStream, Parameters parameters) throws IOException {
        return new HiDataPaperInputStream(inputStream, parameters);
    }


    public HiDataPaperInputStream(InputStream in, Parameters p) throws IOException {
        try {
            BufferedImage img = ImageIO.read(in);
            DecoderPaper decoder = new DecoderPaper();
            bag = decoder.decode(img, p);
            if (bag != null) {
                LOG.debug(bag.toString());
            } else {
                LOG.debug("Secret not found.");
            }
        } catch (DetectorNoShapeException | DetectorShapeNotARectangleException e) {
            LOG.warn("Exception : " + e.getMessage());
            throw new IOException(e);
        }
        finally {
            in.close();
        }
    }



    static {
        EXTENTIONS.add("jpg");
        EXTENTIONS.add("jpe");
        EXTENTIONS.add("jpeg");
        EXTENTIONS.add("jfif");
        EXTENTIONS.add("png");
        EXTENTIONS.add("bmp");
    }
}
