package com.trading.screener.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.screener.entity.StockData;
import com.trading.screener.repository.StockDataRepository;
import com.trading.screener.repository.AnnouncementSummaryRepository; // <-- Imported
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class AnnouncementService {

    @Autowired
    private StockDataRepository stockDataRepository;

    // 1. Inject the summary repository to check for existing files
    @Autowired
    private AnnouncementSummaryRepository summaryRepository; 

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Value("${kafka.topic.pdf-chunks}")
    private String kafkaTopic;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final int CHUNK_SIZE = 6000;
    
    private final AtomicInteger partitionCounter = new AtomicInteger(0);

    public void processAnnouncements(String fileName, String fromDate, String toDate) {
        List<StockData> stocks = stockDataRepository.findByUploadedFileName(fileName);
        
        if (stocks.isEmpty()) {
            throw new RuntimeException("No records found for file: " + fileName);
        }

        HttpHeaders htmlHeaders = new HttpHeaders();
        htmlHeaders.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        htmlHeaders.set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
        htmlHeaders.set("Accept-Language", "en-US,en;q=0.9");

        HttpHeaders apiHeaders = new HttpHeaders();
        apiHeaders.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        apiHeaders.set("Accept", "application/json, text/javascript, */*; q=0.01");
        apiHeaders.set("Accept-Language", "en-US,en;q=0.9");
        apiHeaders.set("Referer", "https://www.nseindia.com/");

        try {
            ResponseEntity<String> initialResponse = restTemplate.exchange("https://www.nseindia.com", HttpMethod.GET, new HttpEntity<>(htmlHeaders), String.class);
            List<String> setCookies = initialResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
            
            if (setCookies != null && !setCookies.isEmpty()) {
                String cookieHeaderStr = setCookies.stream()
                        .map(cookie -> cookie.split(";")[0])
                        .collect(Collectors.joining("; "));
                apiHeaders.set(HttpHeaders.COOKIE, cookieHeaderStr);
            }
            Thread.sleep(1000);
        } catch (Exception e) {
            System.out.println("Failed to initialize NSE session: " + e.getMessage());
        }

        for (StockData stock : stocks) {
            String symbol = stock.getSymbol();
            String url = String.format(
                    "https://www.nseindia.com/api/corporate-announcements?index=equities&from_date=%s&to_date=%s&symbol=%s&reqXbrl=false",
                    fromDate, toDate, symbol
            );

            try {
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(apiHeaders), String.class);
                JsonNode root = objectMapper.readTree(response.getBody());

                if (root.isArray()) {
                    for (JsonNode announcement : root) {
                        String attachmentUrl = announcement.path("attchmntFile").asText(null);
                        
                        if (attachmentUrl != null && attachmentUrl.startsWith("http")) {
                            
                            // ==========================================
                            // 2. IDEMPOTENCY CHECK: Check DB before processing
                            // ==========================================
                            if (summaryRepository.existsByDocumentId(attachmentUrl)) {
                                System.out.println(" SKIPPING DOWNLOAD: " + symbol + " | Already completely processed in DB -> " + attachmentUrl);
                                continue; // Skip to the next announcement immediately
                            }
                            
                            processAndPublishAttachment(symbol, attachmentUrl, apiHeaders);
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("Failed to fetch announcements for " + symbol + ": " + e.getMessage());
            }
        }
    }

    private void processAndPublishAttachment(String symbol, String pdfUrl, HttpHeaders nseHeaders) {
        try {
            RequestEntity<Void> request = RequestEntity.get(URI.create(pdfUrl)).headers(nseHeaders).build();
            ResponseEntity<byte[]> pdfResponse = restTemplate.exchange(request, byte[].class);
            byte[] pdfBytes = pdfResponse.getBody();

            if (pdfBytes == null || pdfBytes.length == 0) return;

            HttpHeaders pythonHeaders = new HttpHeaders();
            pythonHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

            ByteArrayResource pdfResource = new ByteArrayResource(pdfBytes) {
                @Override
                public String getFilename() {
                    return symbol + ".pdf";
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", pdfResource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, pythonHeaders);
            
            ResponseEntity<String> pythonResponse = restTemplate.postForEntity(
                    "http://pdf-md-service:8085/convert", 
                    requestEntity, 
                    String.class
            );

            if (!pythonResponse.getStatusCode().is2xxSuccessful() || pythonResponse.getBody() == null) {
                System.out.println("Failed to convert PDF to Markdown for " + symbol);
                return;
            }

            JsonNode node = objectMapper.readTree(pythonResponse.getBody());
            String fullText = node.path("markdown").asText();

            if (fullText == null || fullText.trim().isEmpty()) return;

            int targetPartition = partitionCounter.getAndUpdate(p -> (p == 2) ? 0 : p + 1);
            int totalChunks = (int) Math.ceil((double) fullText.length() / CHUNK_SIZE);
            int currentChunk = 1;

            System.out.println(" Route File [" + symbol + "] (" + totalChunks + " chunks) -> Partition " + targetPartition);

            for (int i = 0; i < fullText.length(); i += CHUNK_SIZE) {
                int end = Math.min(fullText.length(), i + CHUNK_SIZE);
                String chunkText = fullText.substring(i, end);
                
                var jsonPayload = objectMapper.createObjectNode();
                jsonPayload.put("symbol", symbol);
                jsonPayload.put("documentId", pdfUrl);
                jsonPayload.put("chunkNumber", currentChunk);
                jsonPayload.put("totalChunks", totalChunks);
                jsonPayload.put("partition", targetPartition);
                jsonPayload.put("text", chunkText);
                
                String messageString = jsonPayload.toString();
                final int chunkNum = currentChunk;

                kafkaTemplate.send(kafkaTopic, targetPartition, symbol, messageString).whenComplete((result, ex) -> {
                    if (ex == null) {
                        System.out.println(" Published chunk " + chunkNum + "/" + totalChunks 
                                + " for " + symbol 
                                + " to [Partition " + result.getRecordMetadata().partition() 
                                + ", Offset " + result.getRecordMetadata().offset() + "]");
                    } else {
                        System.out.println(" Failed chunk " + chunkNum + " for " + symbol + ": " + ex.getMessage());
                    }
                });
                
                currentChunk++;
            }
            
        } catch (Exception e) {
            System.out.println("Failed to process attachment " + pdfUrl + ": " + e.getMessage());
        }
    }
}