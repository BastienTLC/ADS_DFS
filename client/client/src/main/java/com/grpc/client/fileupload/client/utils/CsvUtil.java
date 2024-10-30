package com.grpc.client.fileupload.client.utils;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class CsvUtil {

    public static void writeResultsToCsv(List<Map<String, Object>> results) throws IOException {
        // Generate current timestamp for filename
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String filename = "results_" + timestamp + ".csv";

        // Check if results are not empty to create headers
        if (results.isEmpty()) {
            System.out.println("No data to write to CSV.");
            return;
        }

        // Get headers from the first result map
        Map<String, Object> firstResult = results.get(0);
        String[] headers = firstResult.keySet().toArray(new String[0]);

        // Write data to CSV
        try (FileWriter writer = new FileWriter(Paths.get(filename).toFile());
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(headers))) {

            for (Map<String, Object> result : results) {
                csvPrinter.printRecord(result.values());
            }

            System.out.println("CSV file created: " + filename);
        } catch (IOException e) {
            System.err.println("Error while writing CSV file: " + e.getMessage());
            throw e;
        }
    }

}

