package com.grpc.server.fileupload.server.utils;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class DiskFileStorage {

    private final ByteArrayOutputStream byteArrayOutputStream;

    public DiskFileStorage() {
        this.byteArrayOutputStream = new ByteArrayOutputStream();
    }


    public ByteArrayOutputStream getStream() {
        return this.byteArrayOutputStream;
    }

    public void write(String fileNameWithType, String author, String nodeId) throws IOException {
        String defaultPath = "output/";
        defaultPath = createDirectory(defaultPath.concat(nodeId).concat("/"));
        defaultPath = createDirectory(defaultPath.concat(author).concat("/"));
        try (FileOutputStream fileOutputStream = new FileOutputStream(defaultPath.concat(fileNameWithType))) {
            this.byteArrayOutputStream.writeTo(fileOutputStream);
        }
    }

    private String createDirectory(String path) {
        File directory = new File(path);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return path;
    }

    public List<Path> getAllFiles(String nodeId) throws IOException {
        String nodePath = "output/" + nodeId;
        List<Path> fileList = new ArrayList<>();

        Files.walk(Paths.get(nodePath))
                .filter(Files::isRegularFile)
                .forEach(fileList::add);

        return fileList;
    }

    public void deleteFile(String fileNameWithType, String author, String nodeId) throws IOException {
        String filePath = "output/" + nodeId + "/" + author + "/" + fileNameWithType;
        File file = new File(filePath);
        if (file.exists()) {
            if (!file.delete()) {
                throw new IOException("Failed to delete file: " + filePath);
            }
        }
    }

    public void close() throws IOException {
        this.byteArrayOutputStream.close();
    }
}
