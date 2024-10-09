package com.grpc.client.fileupload.client.utils;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

// In order to be able to download files, this will be used, similar to how /output is used on server side
public class DiskFileStorage {

    private final ByteArrayOutputStream byteArrayOutputStream;

    public DiskFileStorage() {
        this.byteArrayOutputStream = new ByteArrayOutputStream();
    }


    public ByteArrayOutputStream getStream() {
        return this.byteArrayOutputStream;
    }

    public void write(String fileNameWithType) throws IOException {
        String DEFAULT_PATH = "downloads/";
        try (FileOutputStream fileOutputStream = new FileOutputStream(DEFAULT_PATH.concat(fileNameWithType))) {
            this.byteArrayOutputStream.writeTo(fileOutputStream);
        }
    }

    public void close() throws IOException {
        this.byteArrayOutputStream.close();
    }
}