package com.grpc.server.fileupload.server.utils;

import de.uniba.wiai.lspi.chord.data.ID;
import de.uniba.wiai.lspi.chord.service.Chord;
import de.uniba.wiai.lspi.chord.service.Key;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class DiskFileStorage {

    private final ByteArrayOutputStream byteArrayOutputStream;
    private final Chord chord;

    public DiskFileStorage(Chord chord) {
        this.byteArrayOutputStream = new ByteArrayOutputStream();
        this.chord = chord;
    }


    public ByteArrayOutputStream getStream() {
        return this.byteArrayOutputStream;
    }

    /*public void write(String fileNameWithType) throws IOException {
        String DEFAULT_PATH = "output/";
        try (FileOutputStream fileOutputStream = new FileOutputStream(DEFAULT_PATH.concat(fileNameWithType))) {
            this.byteArrayOutputStream.writeTo(fileOutputStream);
        }
    }*/

    public void writeOnTheRing(String fileNameWithType) throws IOException {
        // Convert the ByteArrayOutputStream to a byte array
        byte[] fileData = byteArrayOutputStream.toByteArray();

        try {
            // Generate a unique key for the file using a hash of the filename
            Key key = Hash.hash(fileNameWithType);

            // Insert the file data into the Chord network
            chord.insert(key, fileData);
            System.out.println("File '" + fileNameWithType + "' written to the Chord network with key: " + key.toString());
        } catch (Exception e) {
            throw new IOException("Failed to write file to the Chord network", e);
        }
    }

    public void close() throws IOException {
        this.byteArrayOutputStream.close();
    }
}
