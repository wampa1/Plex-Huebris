package com.huebris.plexhuebris.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HueService {
    private static final Logger log = LoggerFactory.getLogger( HueService.class );

    @Value("${hue.server}")
    private String HUE_SERVER;

    @Value("${hue.user}")
    private String HUE_USER;

    private ObjectMapper objectMapper = new ObjectMapper();

    public void sendColorToHueLights(List<Integer> lights, Color color ) {
        for ( Integer light : lights ) {
            try {
                sendColorToHueLights( light, color );
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    public void sendColorToHueLights(int light, Color color ) throws Exception {

        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        String uri = HUE_SERVER;
        String path = "/api/" + HUE_USER + "/lights/" + light + "/state";

        URL url = new URL(uri + path);
        HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
        httpCon.setDoOutput(true);
        httpCon.setRequestMethod("PUT");

        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put( "on", hsb[2] > 0.03?true:false);
        if ( hsb[2]> 0.03 ) {
            propertyMap.put( "sat",  Math.round(hsb[1] * 254.0));
            propertyMap.put( "bri", Math.round(hsb[2] * 254.0));
            propertyMap.put( "hue", Math.round(hsb[0] * 65536.0));
        }

        log.info( "   Raw Brightness: " + hsb[2] );

        OutputStreamWriter out = new OutputStreamWriter(
                httpCon.getOutputStream());

        out.write(objectMapper.writeValueAsString( propertyMap ));

        out.close();
        httpCon.getInputStream();
    }
}
