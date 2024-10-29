package com.grpc.client.fileupload.client.Factory;

import com.grpc.client.fileupload.client.model.CustomMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FileFactory {


    public static List<MultipartFile> generateBinaryFiles(int numberOfFiles, int fileSize) {
        List<MultipartFile> files = new ArrayList<>();
        for (int i = 0; i < numberOfFiles; i++) {
            files.add(generateBinaryFile(fileSize, "file" + i + ".bin"));
        }
        return files;
    }


    public static MultipartFile generateBinaryFile(int fileSize, String fileName) {
        Random random = new Random();
        byte[] data = new byte[fileSize];
        random.nextBytes(data);

        return new CustomMultipartFile(
                fileName,
                fileName,
                "application/octet-stream",
                data                   // Content of the file
        );
    }
}
