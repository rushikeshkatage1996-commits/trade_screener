package com.trading.screener.repository;

import com.trading.screener.entity.AnnouncementSummary;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnnouncementSummaryRepository extends JpaRepository<AnnouncementSummary, Long> {
    
    boolean existsByDocumentId(String documentId);

    // NEW: Fetch all summaries where the symbol is in the provided list
    List<AnnouncementSummary> findBySymbolIn(List<String> symbols);
}