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

    /**
     * 3. Upload an Excel file, extract symbols dynamically from the 'Symbol' column,
     * and return matching summaries from MySQL in a single database call.
     * Content-Type: multipart/form-data
     */
    @PostMapping(value = "/announcements/summaries/by-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> getSummariesByExcel(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Collections.singletonMap("error", "File is empty"));
            }

            List<AnnouncementSummary> summaries = summaryRetrievalService.getSummariesFromExcel(file);
            return ResponseEntity.ok(summaries);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to retrieve summaries: " + e.getMessage()));
        }
    }

    /**
     * 4. Direct lookup: Fetch all summaries for a single stock symbol.
     */
@GetMapping(value = "/announcements/summaries/{symbol}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
public ResponseEntity<byte[]> exportSummariesBySymbol(@PathVariable("symbol") String symbol) {
    try {
        String cleanSymbol = symbol.trim().toUpperCase();
        
        // 1. Fetch matching summaries from the DB
        List<AnnouncementSummary> summaries = summaryRepository.findBySymbolIn(List.of(cleanSymbol));

        // 2. Create the Excel file in memory
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
             
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet(cleanSymbol + " Summaries");
            
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

            // 3. Set headers to force a download with the dynamic filename
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.add("Content-Disposition", "attachment; filename=\"Summary_" + cleanSymbol + ".xlsx\"");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(out.toByteArray());
        }
    } catch (Exception e) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).body(null);
    }
}

}