package org.backend;

import org.backend.util.FileGenerator;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class TestClient {


    private static final String SERVER_URL = "http://localhost/client";
    private HttpClient httpClient = HttpClient.newHttpClient();
    private List<User> users = new ArrayList<>();
    private ExecutorService executorService;

    public static void main(String[] args) {
        TestClient testClient = new TestClient();
        try {
            // Example usage:
            System.out.println("Starting tests...");
            System.out.println("Test 1: Upload/download time vs. concurrent users");
            testClient.testUploadDownloadTimeVsConcurrentUsers(5, 20, 20, 100);
            //testClient.testUploadDownloadTimeVsFileSize(5, 0, 1024, 102400);
            //testClient.testAuthentication();
            //testClient.testUserIsolation();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            testClient.shutdown();
        }
    }


    // Test the evolution of upload/download time as a function of the number of concurrent users.
    public void testUploadDownloadTimeVsConcurrentUsers(int iterations, int increment, int startBound, int endBound) throws IOException, InterruptedException {
        String csvFile = "upload_download_time_vs_concurrent_users.csv";
        try (PrintWriter writer = new PrintWriter(new FileWriter(csvFile))) {
            writer.println("Iteration,ConcurrentUsers,UploadTime(ms),DownloadTime(ms), UniqueUploadsMessageTime(ms/user), UniqueDownloadsMessageTime(ms/user)");
            for (int userCount = startBound; userCount <= endBound; userCount += increment) {
                for (int i = 1; i <= iterations; i++) {
                    // Create users
                    System.out.println("Creating" + userCount + " users...");
                    createUsers(userCount);

                    // Perform upload and download tests
                    System.out.println("Performing upload and download tests for " + userCount + " users...");
                    // upload de 1024 bytes
                    long uploadTime = performConcurrentUploads(1024);
                    System.out.println("Upload time: " + uploadTime + " ms");
                    System.out.println("Performing download tests for " + userCount + " users...");
                    long downloadTime = performConcurrentDownloads();
                    System.out.println("Download time: " + downloadTime + " ms");
                    long uniqueUploadsMessageTime = uploadTime / userCount;
                    long uniqueDownloadsMessageTime = downloadTime / userCount;

                    // Log the result
                    writer.printf("%d,%d,%d,%d,%d,%d%n", i, userCount, uploadTime, downloadTime, uniqueUploadsMessageTime, uniqueDownloadsMessageTime);
                    writer.flush();

                    // Clean up
                    users.clear();
                }
                System.out.println("Finished tests for " + userCount + " users.");
                System.out.println("--------------------------------------------------");
            }
            System.out.println("All tests completed.");
        }
    }

    // Test the evolution of upload/download time as a function of the file size.
    public void testUploadDownloadTimeVsFileSize(int iterations, int increment, int startBound, int endBound) throws IOException, InterruptedException {
        String csvFile = "upload_download_time_vs_file_size.csv";
        try (PrintWriter writer = new PrintWriter(new FileWriter(csvFile))) {
            writer.println("Iteration,FileSize(Bytes),UploadTime(ms),DownloadTime(ms)");

            // For this test, we'll use a fixed number of users
            int userCount = 10;
            createUsers(userCount);

            for (int fileSize = startBound; fileSize <= endBound; fileSize += (increment > 0 ? increment : fileSize)) {
                for (int i = 1; i <= iterations; i++) {
                    // Perform upload and download tests
                    long uploadTime = performConcurrentUploads(fileSize);
                    long downloadTime = performConcurrentDownloads();

                    // Log the result
                    writer.printf("%d,%d,%d,%d%n", i, fileSize, uploadTime, downloadTime);
                    writer.flush();
                }
            }

            users.clear();
        }
    }

    // Test user authentication.
    public void testAuthentication(){
        try {
            boolean validLogin = testValidUserLogin();
            System.out.println("Valid login: " + validLogin);

            boolean invalidLogin = testInvalidUserLogin();
            System.out.println("Invalid login: " + invalidLogin);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

    }


    // Test user file isolation.
    public void testUserIsolation()  {
        try {
            boolean userIsolation = testUserFileIsolation();
            System.out.println("User isolation: " + userIsolation);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void createUsers(int count) throws IOException, InterruptedException {
        for (int i = 0; i < count; i++) {
            String username = "user" + System.currentTimeMillis() + i;
            String password = "pass" + i;
            User user = new User(username, password);

            // Register and login user
            if (user.register(SERVER_URL + "/register", httpClient) && user.login(SERVER_URL + "/login", httpClient)) {
                users.add(user);
            }
        }
    }

    private long performConcurrentUploads(int fileSize) throws InterruptedException, IOException {
        executorService = Executors.newFixedThreadPool(users.size());
        List<Callable<Long>> uploadTasks = new ArrayList<>();

        for (User user : users) {
            uploadTasks.add(() -> {
                // Generate a random file of the specified size
                File file = FileGenerator.generateRandomFile(fileSize, "file" + System.currentTimeMillis());
                String fileName = file.getName();

                // Measure upload time
                long startTime = System.currentTimeMillis();
                boolean success = user.uploadFile(SERVER_URL + "/upload", file);
                long endTime = System.currentTimeMillis();

                if (!success) {
                    System.err.println("Upload failed for user: " + user.getUsername());
                }

                // Clean up
                file.delete();
                user.setUploadedFileName(fileName);

                return endTime - startTime;
            });
        }

        // Execute upload tasks concurrently
        List<Future<Long>> futures = executorService.invokeAll(uploadTasks);

        // Collect times
        long totalTime = 0;
        for (Future<Long> future : futures) {
            try {
                totalTime += future.get();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }

        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.MINUTES);

        return totalTime; // Total time for all users
    }

    private long performConcurrentDownloads() throws InterruptedException, IOException {
        executorService = Executors.newFixedThreadPool(users.size());
        List<Callable<Long>> downloadTasks = new ArrayList<>();

        for (User user : users) {
            downloadTasks.add(() -> {
                String fileName = user.getUploadedFileName();

                // Measure download time
                long startTime = System.currentTimeMillis();
                boolean success = user.downloadFile(SERVER_URL + "/download", fileName);
                long endTime = System.currentTimeMillis();

                if (!success) {
                    System.err.println("Download failed for user: " + user.getUsername());
                }

                // Optionally delete downloaded file
                File downloadedFile = new File("Downloads/" + fileName);
                if (downloadedFile.exists()) {
                    downloadedFile.delete();
                }

                return endTime - startTime;
            });
        }

        // Execute download tasks concurrently
        List<Future<Long>> futures = executorService.invokeAll(downloadTasks);

        // Collect times
        long totalTime = 0;
        for (Future<Long> future : futures) {
            try {
                totalTime += future.get();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }

        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.MINUTES);

        return totalTime; // Total time for all users
    }

    private boolean testValidUserLogin() throws IOException, InterruptedException {
        User validUser = new User("validUser" + System.currentTimeMillis(), "validPass");
        validUser.register(SERVER_URL + "/register", httpClient);
        return validUser.login(SERVER_URL + "/login", httpClient);
    }

    private boolean testInvalidUserLogin() throws IOException, InterruptedException {
        User invalidUser = new User("invalidUser" + System.currentTimeMillis(), "wrongPass");
        return invalidUser.login(SERVER_URL + "/login", httpClient);
    }

    private boolean testUserFileIsolation() throws IOException, InterruptedException {
        // User A uploads a file
        User userA = new User("userA" + System.currentTimeMillis(), "passA");
        userA.register(SERVER_URL + "/register", httpClient);
        userA.login(SERVER_URL + "/login", httpClient);
        File fileA = FileGenerator.generateRandomFile(1024, "fileA");
        userA.uploadFile(SERVER_URL + "/upload", fileA);
        String fileName = fileA.getName();
        fileA.delete();

        // User B tries to download User A's file
        User userB = new User("userB" + System.currentTimeMillis(), "passB");
        userB.register(SERVER_URL + "/register", httpClient);
        userB.login(SERVER_URL + "/login", httpClient);

        boolean downloadSuccess = userB.downloadFile(SERVER_URL + "/download", fileName);

        // Clean up
        File downloadedFile = new File("Downloads/" + fileName);
        if (downloadedFile.exists()) {
            downloadedFile.delete();
        }

        return !downloadSuccess; // Test passes if User B cannot download User A's file
    }

    private void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }
}