package com.example.media.services.media.aws;

import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.model.DetectLabelsRequest;
import com.amazonaws.services.rekognition.model.DetectLabelsResult;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.Label;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.example.media.models.aws.AwsImage;
import com.example.media.services.media.MediaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class AwsMediaService implements MediaService {

    @Value("${aws.bucketName}")
    private String awsBucketName;

    @Value("${aws.s3}")
    private String awsS3Url;

    private final AmazonS3 amazonS3Client;
    private final AmazonRekognition amazonRekognition;

    @Autowired
    public AwsMediaService(AmazonS3 amazonS3Client, AmazonRekognition amazonRekognition) {
        this.amazonS3Client = amazonS3Client;
        this.amazonRekognition = amazonRekognition;
    }

    @Override
    public AwsImage uploadImage(MultipartFile multipartFile) throws IOException {
        log.info("uploadImage started");
        File file = this.convertMultiPartToFile(multipartFile);
        amazonS3Client.putObject(new PutObjectRequest(this.awsBucketName, file.getName(), file));
        log.info("uploadImage finished");
        GetObjectRequest request = new GetObjectRequest(this.awsBucketName, file.getName());
        Optional<S3ObjectSummary> objectSummary = this.amazonS3Client
                .listObjects(this.awsBucketName)
                .getObjectSummaries()
                .stream()
                .filter(obj -> obj.getKey().equals(request.getKey()))
                .findFirst();
        log.info("objectSummary: {}", objectSummary);
        return objectSummary.map(s3ObjectSummary -> new AwsImage(
                this.getImagePublicUrl(s3ObjectSummary.getKey()),
                s3ObjectSummary.getKey(),
                s3ObjectSummary.getLastModified(),
                s3ObjectSummary.getSize()
        )).orElse(null);
    }

    @Override
    public List<AwsImage> listImages() {
        log.info("getting listImages");
        return amazonS3Client.listObjects(this.awsBucketName)
                .getObjectSummaries()
                .stream()
                .map(s3ObjectSummary ->
                        new AwsImage(
                                this.getImagePublicUrl(s3ObjectSummary.getKey()),
                                s3ObjectSummary.getKey(),
                                s3ObjectSummary.getLastModified(),
                                s3ObjectSummary.getSize()
                        )
                ).toList();
    }

    @Override
    public List<AwsImage> listImagesByLabel(String searchLabel) {
        log.info("searchLabel: {}", searchLabel);
        List<AwsImage> images = new ArrayList<>();
        List<S3ObjectSummary> objectSummaries = this.amazonS3Client
                .listObjects(this.awsBucketName)
                .getObjectSummaries();

        List<String> objectKeys = objectSummaries
                .stream()
                .map(S3ObjectSummary::getKey)
                .toList();

        for (String objectKey : objectKeys) {
            S3Object s3Object = this.amazonS3Client.getObject(this.awsBucketName, objectKey);
            Optional<S3ObjectSummary> objectSummary = objectSummaries
                    .stream()
                    .filter(obj -> obj.getKey().equals(s3Object.getKey()))
                    .findFirst();
            S3ObjectInputStream objectInputStream = s3Object.getObjectContent();

            ByteBuffer imageBytes = null;
            try {
                imageBytes = ByteBuffer.wrap(objectInputStream.readAllBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            Image image = new Image().withBytes(imageBytes);

            DetectLabelsRequest request = new DetectLabelsRequest()
                    .withImage(image)
                    .withMaxLabels(10)
                    .withMinConfidence(70F);

            DetectLabelsResult result = this.amazonRekognition.detectLabels(request);

            List<Label> labels = result.getLabels();
            for (Label label : labels) {
                String searchLabelFormatted = this.formatSearchLabel(searchLabel);
                if (label.getName().equals(searchLabelFormatted)) {
                    objectSummary.ifPresent(s3ObjectSummary ->
                            images.add(new AwsImage(
                                            this.getImagePublicUrl(s3ObjectSummary.getKey()),
                                            s3ObjectSummary.getKey(),
                                            s3ObjectSummary.getLastModified(),
                                            s3ObjectSummary.getSize()
                                    )
                            ));
                    log.info("Label: {}", label.getName());
                    log.info("Confidence: {}", label.getConfidence());
                }
            }

            try {
                objectInputStream.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return images;
    }

    private String getImagePublicUrl(String imageName) {
        return awsS3Url + imageName;
    }

    private File convertMultiPartToFile(MultipartFile file) throws IOException {
        File convFile = new File(file.getOriginalFilename());
        FileOutputStream fos = new FileOutputStream(convFile);
        fos.write(file.getBytes());
        fos.close();
        return convFile;
    }

    private String formatSearchLabel(String searchLabel) {
        searchLabel = searchLabel.toLowerCase();
        return searchLabel.substring(0, 1).toUpperCase() + searchLabel.substring(1);
    }
}
