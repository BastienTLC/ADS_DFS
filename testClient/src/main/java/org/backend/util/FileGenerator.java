package org.backend.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FileGenerator {
    private static final Random random = new Random();

    public static File generateRandomFile(int sizeInBytes, String fileName) throws IOException {
        byte[] data = new byte[sizeInBytes];
        random.nextBytes(data);

        File tempFile = File.createTempFile(fileName, ".dat");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(data);
        }

        return tempFile;
    }

    public static List<File> generateRandomFile(int numberOfFiles, int fileSize) throws IOException {
        List<File> files = new ArrayList<>();
        for (int i = 0; i < numberOfFiles; i++) {
            files.add(generateRandomFile(fileSize, "file" + i));
        }
        return files;
    }
}
