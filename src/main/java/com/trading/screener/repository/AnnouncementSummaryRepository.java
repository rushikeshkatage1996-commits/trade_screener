package com.trading.screener.repository;

import com.trading.screener.entity.AnnouncementSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnnouncementSummaryRepository extends JpaRepository<AnnouncementSummary, Long> {
    
    boolean existsByDocumentId(String documentId);
}