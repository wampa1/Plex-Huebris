package com.huebris.plexhuebris.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huebris.plexhuebris.service.MediaStreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.IOException;

@RestController
public class HookController {
    private static final Logger log = LoggerFactory.getLogger( HookController.class );

    public HookController(MediaStreamService mediaStreamService) {
        this.mediaStreamService = mediaStreamService;
    }

    private MediaStreamService mediaStreamService;
    private ObjectMapper objectMapper = new ObjectMapper();
    @RequestMapping(name = "/api/hook", consumes = MediaType.MULTIPART_FORM_DATA_VALUE )
    public void hook(MultipartHttpServletRequest request) {

        String json =request.getParameter("payload");
        try {
            JsonNode jsonNode = objectMapper.readTree( json );
            String event = jsonNode.get("event").asText();
            log.info(event );

            if ( "media.pause".equals( event )) {
                mediaStreamService.stop();
            } else if ( "media.play".equals(event ) ) {
                mediaStreamService.start();
            } else if ( "media.resume".equals(event ) ) {
                mediaStreamService.start();
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }

    }

}
