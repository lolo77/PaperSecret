package com.topsecret.plugin.papersecret.stream;

import com.secretlib.io.stream.HiDataAbstractOutputStream;
import com.secretlib.util.Log;
import com.secretlib.util.Parameters;
import com.topsecret.plugin.papersecret.codec.EncoderPaper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HiDataPaperOutputStream extends HiDataAbstractOutputStream {
    private static final Log LOG = new Log(HiDataPaperOutputStream.class);
    static final String CODEC_NAME = "Paper/1/PNG";
    static final List<String> EXTENTIONS = new ArrayList();

    private BufferedImage imgIn = null;

    public HiDataPaperOutputStream() {

    }

    public List<String> getExtensions() {
        return Collections.unmodifiableList(EXTENTIONS);
    }

    public boolean matches(String ext) {
        return EXTENTIONS.indexOf(ext) >= 0;
    }

    @Override
    public HiDataAbstractOutputStream create(InputStream inputStream, OutputStream outputStream, Parameters parameters) throws IOException {
        return new HiDataPaperOutputStream(inputStream, outputStream, parameters);
    }

    public HiDataPaperOutputStream(InputStream inputStream, OutputStream outputStream, Parameters parameters) throws IOException {
        super(outputStream, parameters);
        try {
            imgIn = ImageIO.read(inputStream);
        } catch (Exception e) {
            LOG.error("Exception while loading source : " + e.getMessage());
            throw new IOException(e);
        }
        finally {
            inputStream.close();
        }
    }

    @Override
    public void close() throws IOException {
        try {
            EncoderPaper enc = new EncoderPaper();
            // buf (secret data) is filled by one or more calls to this.write
            BufferedImage imgOut = enc.encode(imgIn, buf.toByteArray(), params);
            ImageIO.write(imgOut, "PNG", out);
        } catch (Exception e) {
            LOG.error("Exception while encoding or writing : " + e.getMessage());
            throw new IOException(e);
        }
        finally {
            out.close();
        }
    }

    @Override
    public String getCodecName() {
        return CODEC_NAME;
    }


    static {
        EXTENTIONS.add("png");
    }

}
