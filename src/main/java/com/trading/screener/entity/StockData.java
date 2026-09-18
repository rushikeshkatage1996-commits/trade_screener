package com.trading.screener.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Stock_Data")
@Data // Generates getters and setters
public class StockData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Stock_Id")
    private Integer stockId;

    @Column(name = "Sr_No")
    private Integer srNo;

    @Column(name = "Stock_Name")
    private String stockName;

    @Column(name = "Symbol")
    private String symbol;

    @Column(name = "Close_Price")
    private BigDecimal closePrice;

    @Column(name = "Percent_Change")
    private BigDecimal percentChange;

    @Column(name = "Volume")
    private Long volume;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Column(name = "uploaded_file_name")
    private String uploadedFileName;
}