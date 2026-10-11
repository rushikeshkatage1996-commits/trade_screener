package com.trading.screener.controller;

import com.trading.screener.entity.AnnouncementSummary;
import com.trading.screener.entity.StockData;
import com.trading.screener.service.StockDataService;
import com.trading.screener.repository.StockDataRepository;
import com.trading.screener.dto.AnnouncementRequestDto;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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

    @GetMapping(value = "/announcements/summaries/export", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<byte[]> exportSummariesToCsv(@RequestHeader("filename") String filename) {
        return stockDataService.exportDataByFilename(filename);
    }
}