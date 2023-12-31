package com.example.media.controllers;

import com.example.media.models.aws.AwsImage;
import com.example.media.services.media.MediaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@CrossOrigin
@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaService mediaService;

    @Autowired
    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping("/images")
    public List<AwsImage> listImages(@RequestParam(value = "label", required = false) String label) {
        if (label == null) {
            log.info("starting listAllImages");
            return this.mediaService.listImages();
        }
        log.info("starting listSearchImages");
        return this.mediaService.listImagesByLabel(label);
    }

    @PostMapping("/upload")
    public AwsImage uploadImage(@RequestParam("imageFile") MultipartFile file) throws IOException {
        return this.mediaService.uploadImage(file);
    }

}
