
package org.backend;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.HttpMultipartMode;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.json.JSONObject;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

public class User {

    private String username;
    private String password;
    private String bearerToken;
    private String uploadedFileName;

    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public boolean register(String url, HttpClient client) throws IOException, InterruptedException {
        String json = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        return response.statusCode() == 200;
    }

    public boolean login(String url, HttpClient client) throws IOException, InterruptedException {
        String json = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JSONObject jsonResponse = new JSONObject(response.body());
            String token = jsonResponse.getString("jwtToken");
            bearerToken = "Bearer " + token;
            return true;
        }
        return false;
    }

    public boolean uploadFile(String url, File file) throws IOException {
        if (bearerToken == null) {
            System.err.println("Please login first to upload a file.");
            return false;
        }

        // Using Apache HttpClient
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Authorization", bearerToken);

            // Building the multipart entity with key 'file'
            HttpEntity entity = MultipartEntityBuilder.create()
                    .setMode(HttpMultipartMode.EXTENDED)
                    .addBinaryBody("file", file)
                    .build();

            httpPost.setEntity(entity);

            HttpClientResponseHandler<Boolean> responseHandler = response -> {
                int status = response.getCode();
                if (status >= 200 && status < 300) {
                    EntityUtils.consume(response.getEntity());
                    return true;
                } else {
                    System.err.println("Upload failed with status code: " + status);
                    return false;
                }
            };

            return client.execute(httpPost, responseHandler);
        }
    }

    public boolean downloadFile(String url, String fileName) throws IOException {
        if (bearerToken == null) {
            System.err.println("Please login first to download a file.");
            return false;
        }

        // Using Apache HttpClient
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Authorization", bearerToken);

            // Building the multipart entity with the key 'file' and filename as value
            HttpEntity entity = MultipartEntityBuilder.create()
                    .setMode(HttpMultipartMode.EXTENDED)
                    .addTextBody("file", fileName)
                    .build();

            httpPost.setEntity(entity);

            HttpClientResponseHandler<Boolean> responseHandler = response -> {
                int status = response.getCode();
                if (status == 200) {
                    // Save the downloaded file
                    Path downloadPath = Paths.get("Downloads");
                    if (!Files.exists(downloadPath)) {
                        Files.createDirectories(downloadPath);
                    }

                    byte[] fileData = EntityUtils.toByteArray(response.getEntity());
                    Files.write(downloadPath.resolve(fileName), fileData);
                    return true;
                } else {
                    System.err.println("Download failed with status code: " + status);
                    return false;
                }
            };

            return client.execute(httpPost, responseHandler);
        }
    }

    public String getUsername() {
        return username;
    }

    public void setUploadedFileName(String fileName) {
        this.uploadedFileName = fileName;
    }

    public String getUploadedFileName() {
        return uploadedFileName;
    }

    private static HttpRequest.BodyPublisher ofFile(File file) throws IOException {
        return HttpRequest.BodyPublishers.ofByteArray(Files.readAllBytes(file.toPath()));
    }
}

