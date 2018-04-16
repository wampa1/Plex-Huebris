package com.huebris.plexhuebris.controller;

import com.huebris.plexhuebris.service.MediaStreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class MediaScanController {

    private static final Logger log = LoggerFactory.getLogger( MediaScanController.class );

    public MediaScanController(MediaStreamService mediaStreamService) {
        this.mediaStreamService = mediaStreamService;
    }

    private MediaStreamService mediaStreamService;

    @RequestMapping(path = "/api/startDemo", produces = MediaType.APPLICATION_JSON_VALUE )
    public void startDemo( ) {
        mediaStreamService.start();
    }
    @RequestMapping(path = "/api/stop", produces = MediaType.APPLICATION_JSON_VALUE )
    public void stopScanning() {

        mediaStreamService.stop();

    }

}
