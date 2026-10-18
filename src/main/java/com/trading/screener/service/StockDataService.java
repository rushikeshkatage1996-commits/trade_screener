package com.trading.screener.service;

import com.trading.screener.dto.StockDataDto;
import com.trading.screener.entity.AnnouncementSummary;
import com.trading.screener.entity.StockData;
import com.trading.screener.repository.AnnouncementSummaryRepository;
import com.trading.screener.repository.StockDataRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class StockDataService {

    @Autowired
    private StockDataRepository repository;

    @Autowired
    private AnnouncementSummaryRepository summaryRepository;

    public void processAndSaveExcel(MultipartFile file) throws Exception {
        String fileName = file.getOriginalFilename();

        if (fileName != null && repository.existsByUploadedFileName(fileName)) {
            throw new Exception("File '" + fileName + "' has already been uploaded and processed.");
        }

        List<StockDataDto> dtoList = new ArrayList<>();
        DataFormatter dataFormatter = new DataFormatter();

        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rows = sheet.iterator();

            int rowNumber = 0;
            while (rows.hasNext()) {
                Row currentRow = rows.next();

                if (rowNumber == 0) {
                    rowNumber++;
                    continue;
                }

                Cell firstCell = currentRow.getCell(0);
                if (firstCell == null || firstCell.getCellType() == CellType.BLANK) {
                    break;
                }

                StockDataDto dto = new StockDataDto();

                // 1. Sr No
                String srNoStr = cleanNumericString(dataFormatter.formatCellValue(currentRow.getCell(0)));
                dto.setSrNo((int) Double.parseDouble(srNoStr));

                // 2. Strings
                dto.setStockName(dataFormatter.formatCellValue(currentRow.getCell(1)).trim());
                dto.setSymbol(dataFormatter.formatCellValue(currentRow.getCell(2)).trim());

                // 3. Close Price
                String closePriceStr = cleanNumericString(dataFormatter.formatCellValue(currentRow.getCell(3)));
                dto.setClosePrice(new BigDecimal(closePriceStr));

                // 4. Percent Change (Now protected from commas)
                String percentStr = cleanNumericString(dataFormatter.formatCellValue(currentRow.getCell(4)));
                dto.setPercentChange(new BigDecimal(percentStr));

                // 5. Volume (Parsed safely through Double to handle POI's ".0" trailing decimals)
                String volumeStr = cleanNumericString(dataFormatter.formatCellValue(currentRow.getCell(5)));
                dto.setVolume((long) Double.parseDouble(volumeStr));

                dtoList.add(dto);
            }
        }

        List<StockData> entitiesToSave = new ArrayList<>();
        for (StockDataDto dto : dtoList) {
            StockData newStock = mapToEntity(dto);
            newStock.setLastUpdated(LocalDateTime.now());
            newStock.setUploadedFileName(fileName);
            entitiesToSave.add(newStock);
        }

        repository.saveAll(entitiesToSave);
    }

    // --- NEW HELPER METHOD ---
    private String cleanNumericString(String value) {
        if (value == null || value.trim().isEmpty() || value.equals("-")) {
            return "0";
        }
        // Removes commas, percent signs, and spaces. Leaves decimals intact.
        return value.replace(",", "").replace("%", "").trim();
    }

    private StockData mapToEntity(StockDataDto dto) {
        StockData entity = new StockData();
        entity.setSrNo(dto.getSrNo());
        entity.setStockName(dto.getStockName());
        entity.setSymbol(dto.getSymbol());
        entity.setClosePrice(dto.getClosePrice());
        entity.setPercentChange(dto.getPercentChange());
        entity.setVolume(dto.getVolume());
        return entity;
    }

    public ResponseEntity<byte[]> exportDataByFilename(String filename) {
        // 1. Fetch stock data from DB based on the uploadedFileName
        List<StockData> stockDataList = repository.findByUploadedFileName(filename);

        if (stockDataList.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // 2. Extract distinct stock symbols from the fetched stock data
        List<String> symbols = stockDataList.stream()
                .map(StockData::getSymbol)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // 3. Fetch announcement summaries for all these symbols
        List<AnnouncementSummary> summaries = summaryRepository.findBySymbolIn(symbols);

        // 4. Generate CSV from the fetched announcement summaries
        byte[] csvBytes = generateCsvBytes(summaries);

        // 5. Fix the filename to ensure it only ends with .csv
        String cleanFilename = filename;
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0) {
            // This drops the ".xlsx" or any other extension from the original string
            cleanFilename = filename.substring(0, lastDotIndex);
        }
        String exportFilename = cleanFilename + ".csv";

        // 6. Return as a downloadable attachment
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + exportFilename + "\"");
        headers.setContentType(MediaType.parseMediaType("text/csv"));

        return ResponseEntity.ok()
                .headers(headers)
                .body(csvBytes);
    }

    private byte[] generateCsvBytes(List<AnnouncementSummary> summaries) {
        StringBuilder csvBuilder = new StringBuilder();

        // Add headers matching announcement_summaries table structure
        csvBuilder.append("Id,Symbol,Document_Id,Total_Chunks,Summary_Text\n");

        for (AnnouncementSummary summary : summaries) {
            csvBuilder.append(summary.getId() != null ? summary.getId() : "").append(",")
                      .append(escapeSpecialCharacters(summary.getSymbol())).append(",")
                      .append(escapeSpecialCharacters(summary.getDocumentId())).append(",")
                      .append(summary.getTotalChunks()).append(",")
                      .append(escapeSpecialCharacters(summary.getSummaryText()))
                      .append("\n");
        }

        return csvBuilder.toString().getBytes(StandardCharsets.UTF_8);
    }

    // Helper method to ensure text strings containing commas or quotes don't break the CSV format
    private String escapeSpecialCharacters(String data) {
        if (data == null) {
            return "";
        }
        String escapedData = data.replaceAll("\\R", " "); // Remove newlines
        if (escapedData.contains(",") || escapedData.contains("\"") || escapedData.contains("'")) {
            escapedData = escapedData.replace("\"", "\"\""); // Escape internal quotes
            escapedData = "\"" + escapedData + "\"";         // Wrap string in quotes
        }
        return escapedData;
    }
}