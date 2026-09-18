package com.trading.screener.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class StockDataDto {
    private Integer srNo;
    private String stockName;
    private String symbol;
    private BigDecimal closePrice;
    private BigDecimal percentChange;
    private Long volume;
}