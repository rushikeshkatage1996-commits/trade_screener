package com.trading.screener.controller;

import com.trading.screener.dto.AnnouncementRequestDto;
import com.trading.screener.service.AnnouncementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/screener")
@CrossOrigin(origins = "*")
public class AnnouncementController {

    @Autowired
    private AnnouncementService announcementService;

    @PostMapping("/process-announcements")
    public ResponseEntity<String> processAnnouncements(@RequestBody AnnouncementRequestDto request) {
        try {
            // Processing sequentially can take time. For production, consider annotating 
            // the service method with @Async to return an HTTP 202 Accepted immediately.
            announcementService.processAnnouncements(
                    request.getFileName(), 
                    request.getFromDate(), 
                    request.getToDate()
            );
            return ResponseEntity.ok("Successfully initiated announcement processing and Kafka publishing.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }
}