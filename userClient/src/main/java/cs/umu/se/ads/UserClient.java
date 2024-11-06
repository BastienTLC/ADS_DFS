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

import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.entity.mime.HttpMultipartMode;

import org.json.JSONObject;

// This program requires a Downloads/ directory, where files will be downloaded.
// Files for uploading will be taken from current directory.
// In order to start uploading/downloading files, login and the bearer
// token will be used automatically.

// In rare cases some specific usernames that have been registered before
// will not let us login. This occurs with testuser:testuser for me.
// Might need to check that multiple UserClients can use this program at the same time.


public class UserClient {

    private static String BEARER_TOKEN = null;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        while (true) {
            System.out.println();
            System.out.println("Choose an option:");
            System.out.println("1. Register");
            System.out.println("2. Login");
            System.out.println("3. Upload File");
            System.out.println("4. Download File");
            System.out.println("5. Exit");


            int choice = -1;

            if (scanner.hasNextInt()) {
                choice = scanner.nextInt();
                scanner.nextLine();
            } else {
                System.out.println("Invalid input. Please enter a number.");
                scanner.nextLine();
                continue;
            }

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
            // System.out.println("Registration failed: " + response.body());
            System.out.println("Registration failed!");
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
            // System.out.println("Updated BEARER_TOKEN: " + BEARER_TOKEN);

        } else {
            // System.out.println("Login failed: " + response.body());
            System.out.println("Login failed! Please provide valid credentials.");
        }
    }

    // sometimes an error message is called even if it uploaded successfully
    private static void uploadFile() throws IOException {
        if (BEARER_TOKEN == null) {
            System.out.println("Please login first to upload a file.");
            return;
        }

        System.out.println("Enter the file path to upload:");
        String filePath = scanner.nextLine();
        File file = new File(filePath);r

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

            HttpClientResponseHandler<String> responseHandler = response -> {
                int status = response.getCode();
                if (status >= 200 && status < 300) {
                    return EntityUtils.toString(response.getEntity());
                } else {
                    throw new HttpResponseException(status, "Unexpected response status: " + status);
                }
            };

            String responseBody = client.execute(httpPost, responseHandler);
            System.out.println(responseBody);
        }
    }

    private static void downloadFile() throws IOException {
        if (BEARER_TOKEN == null) {
            System.out.println("Please login first to download a file.");
            return;
        }
        
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
            HttpClientResponseHandler<Void> responseHandler = response -> {
                int status = response.getCode();
                if (status == 200) {
                    Path downloadPath = Paths.get("Downloads");
                    if (!Files.exists(downloadPath)) {
                        Files.createDirectories(downloadPath);
                    }

                    Files.write(downloadPath.resolve(fileName), EntityUtils.toByteArray(response.getEntity()));
                    System.out.println("File downloaded successfully to ./Downloads/");
                    return null;
                } else {
//                    String responseBody = EntityUtils.toString(response.getEntity());
//                    System.out.println("Failed to download file. Response code: " + status);
//                    System.out.println("Response Body: " + responseBody);
                    System.out.println("Failed to download file! Please double check the filename and try again.");
                    return null;
                }
            };

            client.execute(httpPost, responseHandler);
        }
    }
}


