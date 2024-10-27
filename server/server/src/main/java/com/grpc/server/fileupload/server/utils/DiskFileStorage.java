package com.grpc.server.fileupload.server.utils;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

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

    public void close() throws IOException {
        this.byteArrayOutputStream.close();
    }
}
