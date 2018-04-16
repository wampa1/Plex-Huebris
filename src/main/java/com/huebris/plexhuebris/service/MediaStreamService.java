package com.huebris.plexhuebris.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.media.jai.Histogram;
import javax.media.jai.JAI;
import javax.media.jai.ParameterBlockJAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.registry.RenderedRegistryMode;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Component
public class MediaStreamService {

    private HueService hueService;

    private boolean running;
    private boolean killThread;
    private int sleepDurationMs = 2000;
    private int lookAheadMs = 2000;

    @Value("${plex.server}")
    private String PLEX_SERVER;
    @Value("${plex.port}")
    private String PLEX_PORT;

    @Value("${plex.token}")
    private String PLEX_TOKEN;

    private static int nSteps = 4;

    private ExecutorService executor = Executors.newCachedThreadPool();

    private static final Logger log = LoggerFactory.getLogger( MediaStreamService.class );

    public MediaStreamService(HueService hueService) {
        this.hueService = hueService;

        Runnable r = new Runnable() {
            @Override
            public void run() {
                startServer();
            }
        };

        Future future = executor.submit(r);

    }

    public void start() {
        running = true;
    }

    public void stop() {
        running = false;
    }

    public void destroy() {
        running = false;
        killThread = true;
    }

    public void startServer() {

        double red = 0;
        double green = 0;
        double blue = 0;

        while ( !killThread ) {

            if ( running ) {
                MediaInformation mediaInformation = getMediaInformation();
                if ( mediaInformation.getVideoId() > 0l ) {
                    BufferedImage bufferedImage = getImage( mediaInformation.getVideoId(), mediaInformation.getTimeStampOffset()  );
                    Color c = getImageHistogram( bufferedImage );

                    double rStep = ((double)c.getRed() - red) / (double)nSteps;
                    double gStep = ((double)c.getGreen() - green) / (double)nSteps;
                    double bStep = ((double)c.getBlue() - blue) / (double)nSteps;

                    for ( int i = 0; i < nSteps; i++ ) {
                        double tmpRed = red + (double)i * rStep;
                        double tmpGreen = green + (double)i * gStep;
                        double tmpBlue = blue + (double)i * bStep;
                        Color z = new Color( getColor(tmpRed), getColor(tmpGreen), getColor(tmpBlue));
                        log.info( "  Requesting Color Change(" + i + ") to " + z );
                        hueService.sendColorToHueLights(new ArrayList<>(Arrays.asList(6,7)), z);
                        try {
                            Thread.sleep(sleepDurationMs/nSteps);
                        } catch (InterruptedException e) {
                            log.error(e.getMessage(), e);
                        }
                        if ( !running ) {
                            break;
                        }
                    }
                    red = c.getRed();
                    green = c.getGreen();
                    blue = c.getBlue();
                }            }
        }
    }

    private int getColor(double rawColorValue) {
        long val = Math.round( rawColorValue);
        if( val < 0 )
            val = 0;

        if(val > 255)
            val = 255;
        return (int)val;
    }
    private MediaInformation getMediaInformation() {
        MediaInformation result = new MediaInformation();
        String xml = null;
        try {
            xml = getHTML( PLEX_SERVER + ":" + PLEX_PORT + "/status/sessions?X-Plex-Token=" + PLEX_TOKEN );
            XmlMapper xmlMapper = new XmlMapper();
            JsonNode node = xmlMapper.readTree(xml.getBytes());
            result.setTimeStampOffset( node.get("Video").get("viewOffset").asLong() );
            result.setVideoId(node.get("Video").get("Media").get("Part").get("id").asLong());
            log.info( "Retrieved Video Information");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return result;
    }

    private BufferedImage getImage(long videoId, long timeStampOffset) {

        String imageTranscode = "/library/parts/" + videoId + "/indexes/sd/" + (timeStampOffset + lookAheadMs) + "?X-Plex-Token=" + PLEX_TOKEN;
        try {
            URL url = new URL(PLEX_SERVER + ":" + PLEX_PORT + "/photo/:/transcode?X-Plex-Token=" + PLEX_TOKEN + "&width=700&height=394&minSize=1&url=" + URLEncoder.encode( imageTranscode, "UTF-8"));
            BufferedImage img = ImageIO.read(url);
            log.info( " Got New Image");
            return img;

        } catch (IOException e) {
            log.error( e.getMessage(), e);
        }


        return null;
    }

    private String getHTML(String urlToRead) throws Exception {
        StringBuilder result = new StringBuilder();
        URL url = new URL(urlToRead);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line;
        while ((line = rd.readLine()) != null) {
            result.append(line);
        }
        rd.close();
        return result.toString();
    }

    /*
     * Where bi is your image, (x0,y0) is your upper left coordinate, and (w,h)
     * are your width and height respectively
     */
    public Color averageColor(BufferedImage bi, int x0, int y0, int w, int h) {
        int x1 = x0 + w;
        int y1 = y0 + h;
        long sumr = 0, sumg = 0, sumb = 0;
        long num = 0;
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                Color pixel = new Color(bi.getRGB(x, y));
                sumr += pixel.getRed();
                sumg += pixel.getGreen();
                sumb += pixel.getBlue();
                num++;
            }
        }
        Color result = null;
        try {
            result = new Color((int)(sumr / num), (int)(sumg / num), (int)(sumb / num));
        } catch ( Exception e ) {
            log.error( e.getMessage(), e);
        }
        return result;
    }

    private Color getImageHistogram(BufferedImage bi ) {
        // Set up the parameters for the Histogram object.
        int[] bins = {256, 256, 256};             // The number of bins.
        double[] low = {0.0D, 0.0D, 0.0D};        // The low value.
        double[] high = {256.0D, 256.0D, 256.0D}; // The high value.
        // Construct the Histogram object.
        Histogram hist = new Histogram(bins, low, high);
        // Create the parameter block.
        PlanarImage planarImage = PlanarImage.wrapRenderedImage( bi );


        ParameterBlockJAI pb =
                new ParameterBlockJAI("Histogram",
                        RenderedRegistryMode.MODE_NAME);

        pb.setSource("source0", planarImage);
        pb.setParameter("numBins", new int[]{128,128,128});

        try{
            PlanarImage dst = JAI.create("histogram", pb,null);
            hist = (Histogram) dst.getProperty("histogram");
            // Print 3-band histogram.

            int r = (int)(indexOfMax( hist.getBins(0)) * 255.0/128.0);
            int g = (int)(indexOfMax( hist.getBins(1)) * 255.0/128.0);
            int b = (int)(indexOfMax( hist.getBins(2)) * 255.0/128.0);

            return new Color( r, g, b );

        } catch (Throwable t) {
            log.error( t.getMessage(), t);
        }
        return null;
        // Retrieve the histogram data.
    }
    private int indexOfMax( int[] vals ) {
        int result = 0;
        for ( int i = 1; i < vals.length; i++ ) {
            if ( vals[i] > vals[result] ) {
                result = i;
            }
        }
        return result;
    }
    public Color mode(BufferedImage bi, int x0, int y0, int w, int h) {

        HashMap<Integer, Integer> red = new LinkedHashMap<>();
        HashMap<Integer, Integer> blue = new LinkedHashMap<>();
        HashMap<Integer, Integer> green = new LinkedHashMap<>();

        int x1 = x0 + w;
        int y1 = y0 + h;
        long sumr = 0, sumg = 0, sumb = 0;
        long num = 0;

        int rMax = 0;
        int rValue = 0;

        int gMax = 0;
        int gValue = 0;

        int bMax = 0;
        int bValue = 0;

        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                Color pixel = new Color(bi.getRGB(x, y));

                Integer r = pixel.getRed();
                Integer currentValue = red.get( r );
                if ( currentValue == null ) {
                    currentValue = 0;
                }
                red.put( r, currentValue++ );

                if ( currentValue > rMax ) {
                    rMax = currentValue;
                    rValue = r;
                }

                Integer g = pixel.getRed();
                currentValue = green.get( g );
                if ( currentValue == null ) {
                    currentValue = 0;
                }
                green.put( g, currentValue++ );
                if ( currentValue > gMax ) {
                    gMax = currentValue;
                    gValue = g;
                }


                Integer b = pixel.getBlue();
                currentValue = blue.get( b );
                if ( currentValue == null ) {
                    currentValue = 0;
                }
                blue.put( b, currentValue++ );
                if ( currentValue > bMax ) {
                    bMax = currentValue;
                    bValue = b;
                }
            }
        }

        Color result = null;
        try {
            result = new Color(rValue, gValue, bValue);
        } catch ( Exception e ) {
            log.error( e.getMessage(), e);
        }
        return result;
    }
    private class MediaInformation {
        private long videoId;
        private long timeStampOffset;

        public long getVideoId() {
            return videoId;
        }

        public void setVideoId(long videoId) {
            this.videoId = videoId;
        }

        public long getTimeStampOffset() {
            return timeStampOffset;
        }

        public void setTimeStampOffset(long timeStampOffset) {
            this.timeStampOffset = timeStampOffset;
        }
    }
}
