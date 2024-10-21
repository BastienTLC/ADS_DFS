package com.grpc.server.fileupload.server.types;

public class Message {
    private String id;
    private long timestamp;
    private String author;
    private String topic;
    private String content;
    private byte[] data;

    public Message(String id, long timestamp, String author, String topic, String content, byte[] data) {
        this.id = id;
        this.timestamp = timestamp;
        this.author = author;
        this.topic = topic;
        this.content = content;
        this.data = data;
    }

    // Getters and setters for each field
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}