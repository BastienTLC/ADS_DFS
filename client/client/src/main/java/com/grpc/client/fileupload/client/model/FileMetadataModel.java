package com.grpc.client.fileupload.client.model;

import com.devProblems.FileMetadata;
import com.fasterxml.jackson.annotation.JsonProperty;

public class FileMetadataModel {
    @JsonProperty("fileNameWithType")
    private String fileNameWithType;

    @JsonProperty("contentLength")
    private int contentLength;

    //constructor
    public FileMetadataModel(String fileNameWithType, int contentLength) {
        this.fileNameWithType = fileNameWithType;
        this.contentLength = contentLength;
    }

    //contructor by grpc FileMetadata
    public FileMetadataModel(FileMetadata fileMetadata) {
        this.fileNameWithType = fileMetadata.getFileNameWithType();
        this.contentLength = fileMetadata.getContentLength();
    }

    // Getters et Setters
    public String getFileNameWithType() {
        return fileNameWithType;
    }

    public void setFileNameWithType(String fileNameWithType) {
        this.fileNameWithType = fileNameWithType;
    }

    public int getContentLength() {
        return contentLength;
    }

    public void setContentLength(int contentLength) {
        this.contentLength = contentLength;
    }


}

