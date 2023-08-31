package com.example.media.services.media;

import com.example.media.models.aws.AwsImage;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface MediaService {

    AwsImage uploadImage(MultipartFile file) throws IOException;

    List<AwsImage> listImages();

    List<AwsImage> listImagesByLabel(String label);
}
