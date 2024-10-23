package com.grpc.server.fileupload.server.base;

import com.grpc.server.fileupload.server.types.FileMetadataModel;
import com.grpc.server.fileupload.server.types.Message;
import java.util.HashMap;
import java.util.Map;

public class FileStore {
    private final Map<String, FileMetadataModel> storage;

    public FileStore() {
        this.storage = new HashMap<>();
    }

    public void addFileMetadata(String fileName, FileMetadataModel fileMetadataModel) {
        this.storage.put(fileName, fileMetadataModel);
    }

}
