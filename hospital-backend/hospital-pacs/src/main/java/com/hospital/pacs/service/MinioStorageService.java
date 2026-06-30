package com.hospital.pacs.service;

import com.hospital.pacs.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MinioStorageService {

    private final MinioProperties minioProperties;
    private MinioClient client;

    @PostConstruct
    void init() throws Exception {
        client = MinioClient.builder()
                .endpoint(normalizeEndpoint(minioProperties.getEndpoint()))
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();
        boolean exists = client.bucketExists(BucketExistsArgs.builder()
                .bucket(minioProperties.getBucket())
                .build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(minioProperties.getBucket()).build());
        }
    }

    public List<String> uploadStudySources(Long checkRequestId, MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("请至少上传一个 DICOM 或 NIfTI 文件");
        }
        String prefix = studySourcePrefix(checkRequestId);
        List<String> keys = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String filename = file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
            String objectKey = prefix + sanitize(filename);
            try (InputStream in = file.getInputStream()) {
                client.putObject(PutObjectArgs.builder()
                        .bucket(minioProperties.getBucket())
                        .object(objectKey)
                        .stream(in, file.getSize(), -1)
                        .contentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType())
                        .build());
            } catch (Exception e) {
                throw new IllegalStateException("上传 MinIO 失败: " + filename, e);
            }
            keys.add(objectKey);
        }
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("未收到有效文件");
        }
        return keys;
    }

    public InputStream openObject(String objectKey) throws Exception {
        return client.getObject(GetObjectArgs.builder()
                .bucket(minioProperties.getBucket())
                .object(objectKey)
                .build());
    }

    public String studySourcePrefix(Long checkRequestId) {
        return "studies/" + checkRequestId + "/source/";
    }

    public String studyResultPrefix(Long checkRequestId) {
        return "studies/" + checkRequestId + "/";
    }

    public String reportSnapshotPrefix(Long checkRequestId) {
        return studyResultPrefix(checkRequestId) + "report/";
    }

    public void uploadBytes(String objectKey, byte[] data, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectKey)
                    .stream(new java.io.ByteArrayInputStream(data), data.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("上传 MinIO 失败: " + objectKey, e);
        }
    }

    public String bucket() {
        return minioProperties.getBucket();
    }

    private String sanitize(String filename) {
        return filename.replace("\\", "_").replace("/", "_");
    }

    private String normalizeEndpoint(String endpoint) {
        try {
            URI uri = URI.create(endpoint.trim());
            String host = uri.getHost();
            if (host == null) {
                return endpoint;
            }
            String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
            int port = uri.getPort();
            return port > 0 ? scheme + "://" + host + ":" + port : scheme + "://" + host;
        } catch (Exception ignored) {
            return endpoint;
        }
    }
}
