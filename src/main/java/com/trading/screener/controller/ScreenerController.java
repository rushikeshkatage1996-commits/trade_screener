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

@PostMapping(value = "/announcements/summaries/export", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
public ResponseEntity<byte[]> exportSummariesToExcel(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "fileName", required = false) String customFileName) {
    try {
        // 1. Determine the base filename from the form data, or fallback to the file's original name
        String baseName = (customFileName != null && !customFileName.trim().isEmpty()) 
                ? customFileName.trim() 
                : file.getOriginalFilename();
        
        if (baseName == null || baseName.trim().isEmpty()) {
            baseName = "Stock_Data.xlsx";
        }
        
        // Ensure it has the correct extension just in case it was missed in the form
        if (!baseName.toLowerCase().endsWith(".xlsx")) {
            baseName += ".xlsx";
        }
        
        String exportFileName = "Summary_" + baseName;

        // 2. Fetch matching summaries from the DB
        List<AnnouncementSummary> summaries = summaryRetrievalService.getSummariesFromExcel(file);

        // 3. Create the Excel file in memory
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
             
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Summaries");
            
            // Create Headers
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Symbol");
            headerRow.createCell(1).setCellValue("Document ID");
            headerRow.createCell(2).setCellValue("Summary");

            // Populate Data
            int rowIdx = 1;
            for (AnnouncementSummary summary : summaries) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(summary.getSymbol());
                row.createCell(1).setCellValue(summary.getDocumentId());
                row.createCell(2).setCellValue(summary.getSummaryText());
            }

            workbook.write(out);

            // 4. Set headers to force a download with the quoted filename
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.add("Content-Disposition", "attachment; filename=\"" + exportFileName + "\"");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(out.toByteArray());
        }
    } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
    }
}

    /**
     * 4. Direct lookup: Fetch all summaries for a single stock symbol.
     */
@PostMapping(value = "/announcements/summaries/export-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = "text/csv")
public ResponseEntity<byte[]> exportSummariesToCsv( ... ) {
    // ... same file logic ...
    String exportFileName = "Summary_" + baseName.replace(".xlsx", ".csv");
    // ... fetch summaries ...

    StringBuilder csv = new StringBuilder();
    // Header
    csv.append("Symbol,Document ID,Summary\n");

    for (AnnouncementSummary summary : summaries) {
        csv.append(escapeCsv(summary.getSymbol())).append(",");
        csv.append(escapeCsv(summary.getDocumentId())).append(",");
        csv.append(escapeCsv(summary.getSummaryText())).append("\n");
    }

    HttpHeaders headers = new HttpHeaders();
    headers.add("Content-Disposition", "attachment; filename=\"" + exportFileName + "\"");

    return ResponseEntity.ok()
            .headers(headers)
            .body(csv.toString().getBytes(StandardCharsets.UTF_8));
}

private String escapeCsv(String data) {
    if (data == null) return "";
    // If data contains comma, quote, or newline, it must be quoted.
    // Quotes inside the data must be escaped by doubling them ("").
    String escapedData = data.replace("\"", "\"\"");
    if (escapedData.contains(",") || escapedData.contains("\"") || escapedData.contains("\n")) {
        return "\"" + escapedData + "\"";
    }
    return escapedData;
}
}

}