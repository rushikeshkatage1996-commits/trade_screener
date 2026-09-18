package com.trading.screener.service;

import com.trading.screener.dto.StockDataDto;
import com.trading.screener.entity.StockData;
import com.trading.screener.repository.StockDataRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
public class StockDataService {

    @Autowired
    private StockDataRepository repository;

    public void processAndSaveExcel(MultipartFile file) throws Exception {
        String fileName = file.getOriginalFilename();

        // 1. Check if the file has already been processed
        if (fileName != null && repository.existsByUploadedFileName(fileName)) {
            throw new Exception("File '" + fileName + "' has already been uploaded and processed.");
        }

        List<StockDataDto> dtoList = new ArrayList<>();
        DataFormatter dataFormatter = new DataFormatter(); 

        // 2. Read the Excel File
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

                String srNoStr = dataFormatter.formatCellValue(currentRow.getCell(0));
                dto.setSrNo((int) Double.parseDouble(srNoStr.trim()));
                dto.setStockName(dataFormatter.formatCellValue(currentRow.getCell(1)).trim());
                dto.setSymbol(dataFormatter.formatCellValue(currentRow.getCell(2)).trim());
                
                String closePriceStr = dataFormatter.formatCellValue(currentRow.getCell(3)).replace(",", "").trim();
                dto.setClosePrice(new BigDecimal(closePriceStr));
                
                String percentStr = dataFormatter.formatCellValue(currentRow.getCell(4)).replace("%", "").trim();
                dto.setPercentChange(new BigDecimal(percentStr));

                String volumeStr = dataFormatter.formatCellValue(currentRow.getCell(5))
                        .replace(",", "")
                        .replace(".", "")
                        .trim();
                dto.setVolume(Long.parseLong(volumeStr));

                dtoList.add(dto);
            }
        }

        // 3. Dump all data into the database
        List<StockData> entitiesToSave = new ArrayList<>();

        for (StockDataDto dto : dtoList) {
            StockData newStock = mapToEntity(dto);
            newStock.setLastUpdated(LocalDateTime.now());
            newStock.setUploadedFileName(fileName); // Save the file name with the record
            entitiesToSave.add(newStock);
        }

        repository.saveAll(entitiesToSave);
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
}