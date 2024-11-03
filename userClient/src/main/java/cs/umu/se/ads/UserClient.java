package cs.umu.se.ads;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.entity.mime.HttpMultipartMode;

import org.json.JSONObject;

// This program requires a Downloads/ directory, where files will be placed.
// Files for uploading will be taken from current directory.
// In order to start uploading/downloading files, login and the bearer
// token will be used automatically.
// Not sure if tests should be measured using this UserClient or not


public class UserClient {

    // this bearer token is replaced automatically upon logging in successfully
    private static String BEARER_TOKEN = "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0dXNlcjIiLCJpYXQiOjE3MzA2MjU3MDIsImV4cCI6MTczMDY2MTcwMn0.W43sAW8IYGf3koThvlnnhqgYv6opfX4ln1x6RR3PEAA";
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        while (true) {
            System.out.println("Choose an option:");
            System.out.println("1. Register");
            System.out.println("2. Login");
            System.out.println("3. Upload File");
            System.out.println("4. Download File");
            System.out.println("5. Exit");
            int choice = scanner.nextInt();
            scanner.nextLine();

            try {
                switch (choice) {
                    case 1 -> register();
                    case 2 -> login();
                    case 3 -> uploadFile();
                    case 4 -> downloadFile();
                    case 5 -> {
                        System.out.println("Exiting...");
                        return;
                    }
                    default -> System.out.println("Invalid choice, please try again.");
                }
            } catch (IOException | InterruptedException e) {
                System.out.println("An error occurred: " + e.getMessage());
            }
        }
    }

    private static void register() throws IOException, InterruptedException {
        System.out.print("Enter username: ");
        String username = scanner.nextLine();
        System.out.print("Enter password: ");
        String password = scanner.nextLine();

        String json = String.format("{\"username\": \"%s\", \"password\": \"%s\"}", username, password);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost/client/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            System.out.println("Registration successful!");
        } else {
            System.out.println("Registration failed: " + response.body());
        }
    }

    private static void login() throws IOException, InterruptedException {
        System.out.print("Enter username: ");
        String username = scanner.nextLine();
        System.out.print("Enter password: ");
        String password = scanner.nextLine();

        String json = String.format("{\"username\": \"%s\", \"password\": \"%s\"}", username, password);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost/client/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            System.out.println("Login successful!");

            // extracting the JwtToken from the response
            JSONObject jsonResponse = new JSONObject(response.body());
            String token = jsonResponse.getString("jwtToken");

            // this will update BEARER_TOKEN with the new token
            BEARER_TOKEN = "Bearer " + token;
            System.out.println("Updated BEARER_TOKEN: " + BEARER_TOKEN);

        } else {
            System.out.println("Login failed: " + response.body());
        }
    }

    // sometimes an error message is called even if it uploaded successfully
    private static void uploadFile() throws IOException {
        System.out.println("Enter the file path to upload:");
        String filePath = scanner.nextLine();
        File file = new File(filePath);

        // creating the HTTP client
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost("http://localhost/client/upload");
            httpPost.setHeader("Authorization", BEARER_TOKEN);

            //  building the multipart entity with key 'file'
            HttpEntity entity = MultipartEntityBuilder.create()
                    .setMode(HttpMultipartMode.EXTENDED)
                    .addBinaryBody("file", file)
                    .build();

            httpPost.setEntity(entity);

            // executing request
            try (CloseableHttpResponse response = client.execute(httpPost)) {
                System.out.println("Response Code: " + response.getCode());
                String responseBody = EntityUtils.toString(response.getEntity());
                System.out.println("Response Body: " + responseBody);
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void downloadFile() throws IOException {
        System.out.println("Enter the filename to download:");
        String fileName = scanner.nextLine();

        // creating the HTTP client
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost("http://localhost/client/download");
            httpPost.setHeader("Authorization", BEARER_TOKEN);

            // Building the multipart entity with the key 'file' and filename as value
            HttpEntity entity = MultipartEntityBuilder.create()
                    .setMode(HttpMultipartMode.EXTENDED)
                    .addTextBody("file", fileName)
                    .build();

            httpPost.setEntity(entity);

            // executing request
            try (CloseableHttpResponse response = client.execute(httpPost)) {
                if (response.getCode() == 200) {
                    // this download path is not created properly currently
                    Path downloadPath = Paths.get("Downloads");
                    if (!Files.exists(downloadPath)) {
                        Files.createDirectories(downloadPath);
                    }

                    // writing the downloaded file to the Downloads directory
                    Files.write(downloadPath.resolve(fileName), EntityUtils.toByteArray(response.getEntity()));
                    System.out.println("File downloaded to ./Downloads/ successfully.");
                } else {
                    System.out.println("Failed to download file. Response code: " + response.getCode());
                    String responseBody = null;
                    try {
                        responseBody = EntityUtils.toString(response.getEntity());
                    } catch (ParseException e) {
                        throw new RuntimeException(e);
                    }
                    System.out.println("Response Body: " + responseBody);
                }
            }
        }
    }
}
