package com.trading.screener.controller;

import com.trading.screener.entity.AnnouncementSummary;
import com.trading.screener.service.StockDataService;

import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.trading.screener.service.SummaryRetrievalService;
import com.trading.screener.repository.AnnouncementSummaryRepository;

@RestController
@RequestMapping("/screener")
@CrossOrigin(origins = "*")
public class ScreenerController {
    
    @Autowired
    private StockDataService stockDataService;

    @Autowired
    private SummaryRetrievalService summaryRetrievalService;

    @Autowired
    private AnnouncementSummaryRepository summaryRepository;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadExcelFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Please upload a valid Excel file.");
        }

        try {
            stockDataService.processAndSaveExcel(file);
            return ResponseEntity.ok("File processed and data saved successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing file: " + e.getMessage());
        }
    }

@PostMapping(value = "/announcements/summaries/export", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = "text/csv; charset=UTF-8")
public ResponseEntity<byte[]> exportSummariesToCsv(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "fileName", required = false) String customFileName) {
    try {
        // 1. Determine the base filename from the form data, or fallback to the file's original name
        String baseName = (customFileName != null && !customFileName.trim().isEmpty()) 
                ? customFileName.trim() 
                : file.getOriginalFilename();
        
        if (baseName == null || baseName.trim().isEmpty()) {
            baseName = "Stock_Data";
        }
        
        // Strip out Excel extensions and append .csv
        if (baseName.toLowerCase().endsWith(".xlsx")) {
            baseName = baseName.substring(0, baseName.length() - 5);
        } else if (baseName.toLowerCase().endsWith(".xls")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }
        
        String exportFileName = "Summary_" + baseName + ".csv";

        // 2. Fetch matching summaries from the DB
        List<AnnouncementSummary> summaries = summaryRetrievalService.getSummariesFromExcel(file);

        // 3. Build the CSV content in memory using StringBuilder (No Apache POI)
        StringBuilder csvBuilder = new StringBuilder();
        
        // Add Headers
        csvBuilder.append("Symbol,Document ID,Summary\n");

        // Add Data Rows
        for (AnnouncementSummary summary : summaries) {
            csvBuilder.append(escapeCsv(summary.getSymbol())).append(",");
            csvBuilder.append(escapeCsv(summary.getDocumentId())).append(",");
            
            String text = summary.getSummaryText();
            csvBuilder.append(escapeCsv(text != null ? text : "")).append("\n");
        }

        // 4. Set headers to force a download with the quoted filename
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=\"" + exportFileName + "\"");

        return ResponseEntity.ok()
                .headers(headers)
                .body(csvBuilder.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                
    } catch (Exception e) {
        e.printStackTrace(); // Prints the exact error lines in your server logs
        return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                .body(("API Error: " + e.getMessage()).getBytes());
    }
}

    /**
     * 4. Direct lookup: Fetch all summaries for a single stock symbol.
     */
@GetMapping(value = "/announcements/summaries/{symbol}", produces = "text/csv; charset=UTF-8")
public ResponseEntity<byte[]> exportSummariesBySymbolToCsv(@PathVariable("symbol") String symbol) {
    try {
        String cleanSymbol = symbol.trim().toUpperCase();
        
        // 1. Fetch matching summaries from the DB
        List<AnnouncementSummary> summaries = summaryRepository.findBySymbolIn(List.of(cleanSymbol));

        // 2. Build the CSV content in memory using StringBuilder (No Apache POI)
        StringBuilder csvBuilder = new StringBuilder();
        
        // Add Headers
        csvBuilder.append("Symbol,Document ID,Summary\n");

        // Add Data Rows (No truncation applied)
        for (AnnouncementSummary summary : summaries) {
            csvBuilder.append(escapeCsv(summary.getSymbol())).append(",");
            csvBuilder.append(escapeCsv(summary.getDocumentId())).append(",");
            
            String text = summary.getSummaryText();
            csvBuilder.append(escapeCsv(text != null ? text : "")).append("\n");
        }

        // 3. Set headers to force a CSV download with the dynamic filename
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=\"Summary_" + cleanSymbol + ".csv\"");

        return ResponseEntity.ok()
                .headers(headers)
                .body(csvBuilder.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));

    } catch (Exception e) {
        e.printStackTrace(); // Prints the exact error lines in your server logs
        return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                .body(("API Error: " + e.getMessage()).getBytes());
    }
}

/**
 * Helper method to safely escape commas, quotes, and newlines in CSV data.
 * You only need to define this once in your ScreenerController.
 */
private String escapeCsv(String data) {
    if (data == null) {
        return "";
    }
    // Escape internal double quotes by doubling them ("")
    String escapedData = data.replace("\"", "\"\"");
    
    // If the string contains a comma, quote, or newline, it must be wrapped in quotes
    if (escapedData.contains(",") || escapedData.contains("\"") || escapedData.contains("\n")) {
        return "\"" + escapedData + "\"";
    }
    return escapedData;
}
}