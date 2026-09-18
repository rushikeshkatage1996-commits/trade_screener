package com.trading.screener.dto;

import lombok.Data;

@Data
public class AnnouncementRequestDto {
    private String fileName;
    private String fromDate; // Format: dd-MM-yyyy
    private String toDate;   // Format: dd-MM-yyyy
}