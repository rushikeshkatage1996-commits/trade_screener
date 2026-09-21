package com.trading.screener.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "announcement_summaries")
public class AnnouncementSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "symbol")
    private String symbol;
    
    @Column(name = "document_id", length = 1000)
    private String documentId;

    @Column(name = "total_chunks")
    private int totalChunks;

    @Column(name = "summary_text", columnDefinition = "LONGTEXT")
    private String summaryText;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public int getTotalChunks() { return totalChunks; }
    public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }

    public String getSummaryText() { return summaryText; }
    public void setSummaryText(String summaryText) { this.summaryText = summaryText; }

}