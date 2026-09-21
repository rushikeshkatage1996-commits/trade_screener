package com.trading.screener.service;

import com.trading.screener.entity.AnnouncementSummary;
import com.trading.screener.repository.AnnouncementSummaryRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
public class SummaryRetrievalService {

    @Autowired
    private AnnouncementSummaryRepository summaryRepository;

    public List<AnnouncementSummary> getSummariesFromExcel(MultipartFile file) throws Exception {
        List<String> symbols = new ArrayList<>();
        DataFormatter dataFormatter = new DataFormatter();

        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rows = sheet.iterator();

            int symbolColumnIndex = -1;

            // 1. Read the Header Row to find the "Symbol" column index dynamically
            if (rows.hasNext()) {
                Row headerRow = rows.next();
                for (Cell cell : headerRow) {
                    if (dataFormatter.formatCellValue(cell).trim().equalsIgnoreCase("Symbol")) {
                        symbolColumnIndex = cell.getColumnIndex();
                        break;
                    }
                }
            }

            if (symbolColumnIndex == -1) {
                throw new IllegalArgumentException("Could not find a 'Symbol' header in the uploaded Excel file.");
            }

            // 2. Extract all stock symbols into a List
            while (rows.hasNext()) {
                Row currentRow = rows.next();
                Cell symbolCell = currentRow.getCell(symbolColumnIndex);
                
                if (symbolCell != null && symbolCell.getCellType() != CellType.BLANK) {
                    String symbol = dataFormatter.formatCellValue(symbolCell).trim();
                    if (!symbol.isEmpty()) {
                        symbols.add(symbol);
                    }
                }
            }
        }

        // 3. Return an empty list if no symbols were found in the file
        if (symbols.isEmpty()) {
            return new ArrayList<>(); 
        }

        // 4. Query MySQL: SELECT * FROM announcement_summaries WHERE symbol IN ('SKYGOLD', 'LAURUSLABS'...)
        return summaryRepository.findBySymbolIn(symbols);
    }
}