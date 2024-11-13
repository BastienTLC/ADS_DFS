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
            testClient.testUploadDownloadTimeVsConcurrentUsers(5, 10, 10, 50, 3, 5);
            //testClient.testUploadDownloadTimeVsFileSize(5, 1, 102400, 102400, 3, 11);
            //testClient.testDownloadTimeVsConcurrentUsers(5, 200, 200, 1000, 3, 5);
            //testClient.testDownloadTimeVsFileSize(5, 1024, 1024, 1024, 5, 5);
            //testClient.testAuthentication();
            //testClient.testUserIsolation();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            testClient.shutdown();
        }
    }


    // Test the evolution of upload/download time as a function of the number of concurrent users.
    public void testUploadDownloadTimeVsConcurrentUsers(int iterations, int increment, int startBound, int endBound, int nbHttpInstance, int nbNode) throws IOException, InterruptedException {
        String csvFile = "upload_download_time_vs_concurrent_users.csv";
        try (PrintWriter writer = new PrintWriter(new FileWriter(csvFile))) {
            writer.println("Iteration,ConcurrentUsers,UploadTime(ms),DownloadTime(ms), UniqueUploadsMessageTime(ms/user), UniqueDownloadsMessageTime(ms/user), NbHttpInstance, NbNode");
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
                    writer.printf("%d,%d,%d,%d,%d,%d,%d,%d%n", i, userCount, uploadTime, downloadTime, uniqueUploadsMessageTime, uniqueDownloadsMessageTime, nbHttpInstance, nbNode);
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
    public void testUploadDownloadTimeVsFileSize(int iterations, int increment, int startBound, int endBound, int nbHttpServ, int nbNode) throws IOException, InterruptedException {
        String csvFile = "upload_download_time_vs_file_size.csv";
        try (PrintWriter writer = new PrintWriter(new FileWriter(csvFile))) {
            writer.println("Iteration,FileSize(Bytes),UploadTime(ms),DownloadTime(ms), UniqueUploadsMessageTime(ms/user), UniqueDownloadsMessageTime(ms/user), NbHttpServ, NbNode");

            // For this test, we'll use a fixed number of users
            int userCount = 30;
            System.out.println("Creating " + userCount + " users...");
            createUsers(userCount);

            for (int fileSize = startBound; fileSize <= endBound; fileSize += (increment > 0 ? increment : fileSize)) {
                System.out.println("Performing upload and download tests for file size: " + fileSize + " bytes...");
                for (int i = 1; i <= iterations; i++) {
                    // Perform upload and download tests
                    System.out.println("Performing upload and download tests for file size: " + fileSize + " bytes...");
                    long uploadTime = performConcurrentUploads(fileSize);
                    long downloadTime = performConcurrentDownloads();
                    long uniqueUploadsMessageTime = uploadTime / userCount;
                    long uniqueDownloadsMessageTime = downloadTime / userCount;

                    // Log the result
                    writer.printf("%d,%d,%d,%d,%d,%d,%d,%d%n", i, fileSize, uploadTime, downloadTime, uniqueUploadsMessageTime, uniqueDownloadsMessageTime, nbHttpServ, nbNode);
                    writer.flush();
                }
                System.out.println("Finished tests for file size: " + fileSize + " bytes.");
                System.out.println("--------------------------------------------------");
            }
            System.out.println("All tests completed.");


            users.clear();
        }
    }

    public void testDownloadTimeVsConcurrentUsers(int iterations, int increment, int startBound, int endBound, int nbHttpInstance, int nbNode) throws IOException, InterruptedException {
        String csvFile = "download_time_vs_concurrent_users.csv";
        try (PrintWriter writer = new PrintWriter(new FileWriter(csvFile))) {
            writer.println("Iteration,ConcurrentUsers,DownloadTime(ms),UniqueDownloadTime(ms/user),NbHttpInstance,NbNode");

            // Step 1: Create 'endBound' number of users and upload one file for each (done once)
            System.out.println("Creating " + endBound + " users and uploading files...");
            createUsers(endBound); // This creates users and registers them
            System.out.println("Uploading files for each user sequentially...");
            performInitialUploadsForUsers(1024);
            System.out.println("Finished uploading files for each user.");

            // Step 2: Perform download tests by varying the number of concurrent users
            for (int userCount = startBound; userCount <= endBound; userCount += increment) {
                System.out.println("Performing download tests with " + userCount + " concurrent users...");
                for (int i = 1; i <= iterations; i++) {
                    long downloadTime = performConcurrentDownloadsForSubset(userCount);
                    long uniqueDownloadTime = downloadTime / userCount;

                    // Log the result
                    writer.printf("%d,%d,%d,%d,%d,%d%n", i, userCount, downloadTime, uniqueDownloadTime, nbHttpInstance, nbNode);
                    writer.flush();
                }
                System.out.println("Finished download tests for " + userCount + " users.");
                System.out.println("--------------------------------------------------");
            }
            System.out.println("All download tests completed.");
            users.clear();
        }
    }

    public void testDownloadTimeVsFileSize(int iterations, int increment, int startBound, int endBound, int nbHttpInstance, int nbNode) throws IOException, InterruptedException {
        String csvFile = "download_time_vs_file_size.csv";
        try (PrintWriter writer = new PrintWriter(new FileWriter(csvFile))) {
            writer.println("Iteration,FileSize(Bytes),DownloadTime(ms),UniqueDownloadTime(ms/user),NbHttpInstance,NbNode");

            // Step 1: Create a fixed number of users
            int userCount = 200;
            System.out.println("Creating " + userCount + " users...");
            createUsers(userCount);
            System.out.println("Users created and registered.");

            // Step 2: For each file size, upload files for each user and perform download tests
            for (int fileSize = startBound; fileSize <= endBound; fileSize += increment) {
                System.out.println("Uploading files of size " + fileSize + " bytes for each user...");
                performInitialUploadsForUsers(fileSize);
                System.out.println("Finished uploading files of size " + fileSize + " bytes for each user.");

                // Step 3: Perform download tests for the current file size
                for (int i = 1; i <= iterations; i++) {
                    System.out.println("Performing download test iteration " + i + " for file size " + fileSize + " bytes...");
                    long downloadTime = performConcurrentDownloads();
                    long uniqueDownloadTime = downloadTime / userCount;

                    // Log the result
                    writer.printf("%d,%d,%d,%d,%d,%d%n", i, fileSize, downloadTime, uniqueDownloadTime, nbHttpInstance, nbNode);
                    writer.flush();
                }

                System.out.println("Finished download tests for file size " + fileSize + " bytes.");
                System.out.println("--------------------------------------------------");
            }

            System.out.println("All download tests completed.");
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

    private void performInitialUploadsForUsers(int fileSize) throws IOException, InterruptedException {
        System.out.println("Starting sequential uploads for all users...");
        for (User user : users) {
            // Generate a random file of the specified size
            File file = FileGenerator.generateRandomFile(fileSize, "file" + System.currentTimeMillis());
            String fileName = file.getName();

            // Upload the file
            boolean success = user.uploadFile(SERVER_URL + "/upload", file);
            if (!success) {
                System.err.println("Initial upload failed for user: " + user.getUsername());
            }
            file.delete();
            user.setUploadedFileName(fileName);
        }
        System.out.println("All uploads are completed.");
    }

    private long performConcurrentDownloadsForSubset(int userCount) throws InterruptedException, IOException {
        List<User> subsetUsers = users.subList(0, userCount);
        executorService = Executors.newFixedThreadPool(userCount);
        List<Callable<Long>> downloadTasks = new ArrayList<>();

        for (User user : subsetUsers) {
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
        executorService.awaitTermination(10, TimeUnit.MINUTES);

        return totalTime; // Total time for all users in the subset
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