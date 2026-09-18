package com.trading.screener.repository;

import com.trading.screener.entity.StockData;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockDataRepository extends JpaRepository<StockData, Integer> {
    boolean existsByUploadedFileName(String uploadedFileName);
    List<StockData> findByUploadedFileName(String uploadedFileName);
}