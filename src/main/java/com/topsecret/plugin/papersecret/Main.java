package com.topsecret.plugin.papersecret;

import com.secretlib.io.stream.HiDataStreamFactory;
import com.secretlib.model.ChunkData;
import com.secretlib.model.DefaultProgressCallback;
import com.secretlib.model.HiDataBag;
import com.secretlib.util.HiUtils;
import com.secretlib.util.Log;
import com.topsecret.event.TopEventBase;
import com.topsecret.event.TopEventPluginStart;
import com.topsecret.event.TopEventPluginStop;
import com.topsecret.plugin.Pluggable;
import com.topsecret.plugin.papersecret.codec.DecoderPaper;
import com.topsecret.plugin.papersecret.codec.EncoderPaper;
import com.topsecret.plugin.papersecret.stream.HiDataPaperInputStream;
import com.topsecret.plugin.papersecret.stream.HiDataPaperOutputStream;
import com.topsecret.plugin.papersecret.util.ParamPaper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;


public class Main implements Pluggable {

    private static final Log LOG = new Log(Main.class);


    public void startCodec() {
        LOG.debug("start codec");
        HiDataStreamFactory.registerInputStream(new HiDataPaperInputStream());
        HiDataStreamFactory.registerOutputStream(new HiDataPaperOutputStream());
    }


    public void stopCodec() {
        LOG.debug("stop codec");
        HiDataStreamFactory.unregisterInputStream(new HiDataPaperInputStream());
        HiDataStreamFactory.unregisterOutputStream(new HiDataPaperOutputStream());
    }


    @Override
    public void onPluginEvent(TopEventBase e) {
        if (e instanceof TopEventPluginStart) {
            startCodec();
        }
        if (e instanceof TopEventPluginStop) {
            stopCodec();
        }
    }


    //------------------------- Test section


    public static void encodePaper(ParamPaper p) throws Exception {

//        String sFilename = "C:\\Users\\ffradet\\Desktop\\tmp\\paper\20200315_155650.jpg";
//        String sFilename =  "C:\\Users\\ffradet\\Desktop\\tmp\\paper\top-secret_image_with_no_empty_places_data_structures_schemas_m_d56f6287-7955-40bc-9a1d-ca64af744564.png";
//        String sFilename =  "C:\\Users\\ffradet\\Desktop\\tmp\\paper\LanesboroMinnesota.jpg";
        String sFilename = "C:\\Users\\ffradet\\Desktop\\tmp\\paper\\TellurideColorado.jpg";

//        String sFilename =  "C:\\Users\\ffradet\\Desktop\\tmp\\paper\20140810_122433.jpg";
//        String sFilename =  "C:\\Users\\ffradet\\Desktop\\tmp\\paper\IMG_20230109_232850.jpg";
//        String sFilename = "C:\\Users\\ffradet\\Desktop\\tmp\\paper\disney - original.jpg";
//        String sFilename =  "C:\\Users\\ffradet\\Desktop\\tmp\\paper\doc.png";
//        String sFilename = "test50x50.png";
//        String sFilename = "C:\\Users\\ffradet\\Desktop\\tmp\\paper\QR-org.png";
//        String sFileSecret = "C:\\Users\\ffradet\\Desktop\\tmp\\paper\disney.jpg";
        String sFileSecret = "C:\\Users\\ffradet\\Desktop\\tmp\\paper\\another_secret.txt";
//        String sFileSecret = "C:\\Users\\ffradet\\Desktop\\tmp\\paper\QR-org.png";

        //byte[] data = new String("toto titi secret").getBytes(StandardCharsets.UTF_8);
        FileInputStream fis = new FileInputStream(sFileSecret);
        byte[] bufData = HiUtils.readAllBytes(fis);
        fis.close();

        HiDataBag bag = new HiDataBag();

        ChunkData data = new ChunkData();
        data.setName("TheSecret!");
        data.setData(bufData);
        bag.addItem(data);
        bag.encryptAll(p);
        bag.addHash(null);

        LOG.debug("bag : " + bag.toString());

        BufferedImage img = ImageIO.read(new File(sFilename));

        EncoderPaper enc = new EncoderPaper();
        img = enc.encode(img, bag.toByteArray(), p);

        ImageIO.write(img, "PNG", new File(sFilename + ".gauss.out.png"));

    }

    private static void decodePaper(ParamPaper p) throws Exception {

//        String sFilename =  "C:\\Users\\ffradet\\Desktop\\tmp\\paper\top-secret_image_with_no_empty_places_data_structures_schemas_m_d56f6287-7955-40bc-9a1d-ca64af744564.png.out.png";
//        String sFilename =  "C:\\Users\\ffradet\\Desktop\\tmp\\paper\20200315_155650.jpg.out.png";
//        String sFilename =  "C:\\Users\\ffradet\\Desktop\\tmp\\paper\LanesboroMinnesota.jpg.out.png";
//        String sFilename =  "C:\\Users\\ffradet\\Desktop\\tmp\\paper\TellurideColorado.jpg.out.png";
//        String sFilename =  "C:\\Users\\ffradet\\Desktop\\tmp\\paper\20200315_155650.jpg.gauss.out.png";
//        String sFilename =  "C:\\Users\\ffradet\\Desktop\\tmp\\paper\20200315_155650_scan300dpi2.jpg";

        String sFilename = "C:\\Users\\ffradet\\Desktop\\tmp\\paper\\TellurideColorado.jpg.gauss.out.png";

//        String sFilename =  "C:\\Users\\ffradet\\Desktop\\tmp\\paper\TellurideColorado_scan300dpi2.jpg";
//        String sFilename = "C:\\Users\\ffradet\\Desktop\\tmp\\paper\LanesboroMinnesota_scan300dpi2.jpg";

//        String sFilename =  "C:\\Users\\ffradet\\Desktop\\tmp\\paper\TellurideColorado_scan300dpi3.jpg";
//        String sFilename = "C:\\Users\\ffradet\\Desktop\\tmp\\paper\LanesboroMinnesota_scan300dpi3.jpg";

        BufferedImage img = ImageIO.read(new File(sFilename));
        DecoderPaper decoder = new DecoderPaper();
        HiDataBag bag = decoder.decode(img, p);
        if (bag != null) {
            LOG.debug(bag.toString());
        } else {
            LOG.debug("Secret not found.");
        }
    }


    /**
     * Test purposes only - never called as a plugin
     *
     * @param args
     */
    public static void main(String[] args) {
        Log.setLevel(Log.DEBUG);
        ParamPaper p = new ParamPaper(args);
        p.setProgressCallBack(new DefaultProgressCallback());

        p.setOutputHeight(1500);
        p.setThresholdEncodeColor(0x80);
        try {
            encodePaper(p);
            decodePaper(p);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}