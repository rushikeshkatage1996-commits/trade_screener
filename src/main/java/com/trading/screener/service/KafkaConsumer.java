package com.trading.screener.service;

import com.trading.screener.entity.AnnouncementSummary;
import com.trading.screener.repository.AnnouncementSummaryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class KafkaConsumer {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private AnnouncementSummaryRepository summaryRepository;

    @Value("${ollama.model:deepseek-coder-v2}")
    private String aiModel;
    
    @Value("${ollama.temperature:0.7}")
    private double temperature;
    
    @Value("${ollama.top-k:40}")
    private int topK;
    
    @Value("${ollama.top-p:0.95}")
    private double topP;

    @Value("#{'${ollama.urls:http://localhost:11434}'.split(',')}")
    private List<String> rawOllamaUrls;

    private List<String> ollamaUrls = new ArrayList<>();

    // Map 1: Tracks the generated summaries per chunk
    private final Map<String, Map<Integer, String>> documentSummaries = new ConcurrentHashMap<>();
    
    // Map 2: Tracks the original raw text per chunk
    private final Map<String, Map<Integer, String>> documentOriginalTexts = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        for (String url : rawOllamaUrls) {
            String baseUrl = url.trim().replaceAll("/+$", ""); 
            ollamaUrls.add(baseUrl + "/api/generate");
        }
    }

    @KafkaListener(
        topics = "${kafka.topic.pdf-chunks}", 
        groupId = "ollama-processor-group", 
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "3" 
    )
    public void consume(String message, 
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        try {
            JsonNode payload = objectMapper.readTree(message);
            String symbol = payload.path("symbol").asText();
            String documentId = payload.path("documentId").asText();
            int chunkNumber = payload.path("chunkNumber").asInt();
            int totalChunks = payload.path("totalChunks").asInt();
            String chunkText = payload.path("text").asText();
            
            int instanceIndex = partition % ollamaUrls.size();
            String targetOllamaUrl = ollamaUrls.get(instanceIndex);

            String threadName = Thread.currentThread().getName();
            System.out.println("=========================================");
            System.out.println(threadName + " | Partition " + partition);
            System.out.println("PROCESSING: " + symbol + " (Chunk " + chunkNumber + "/" + totalChunks + ")");
            
            // Replace your existing prompt string with this new one:
            String prompt = "Summarize the following text concisely, highlighting key points and important information. " +
                "Provide the summary as pure plain text in paragraph format. " +
                "Do NOT use any Markdown, bullet points, asterisks, hashes, or special formatting:\n\n" + 
                chunkText;
            Map<String, Object> ollamaPayload = new HashMap<>();
            ollamaPayload.put("model", aiModel);
            ollamaPayload.put("prompt", prompt);
            ollamaPayload.put("stream", false);
            
            Map<String, Object> options = new HashMap<>();
            options.put("temperature", temperature);
            options.put("top_k", topK);
            options.put("top_p", topP);
            options.put("num_ctx", 8192);
            ollamaPayload.put("options", options);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("ngrok-skip-browser-warning", "true"); 
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(ollamaPayload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(targetOllamaUrl, request, String.class);
            JsonNode rootNode = objectMapper.readTree(response.getBody());
            String summary = rootNode.path("response").asText();
            
            System.out.println("SUMMARY GENERATED for " + symbol + " (Chunk " + chunkNumber + ")");
            
            // --- TRACK BOTH STRINGS IN MEMORY ---
            documentSummaries.putIfAbsent(documentId, new ConcurrentHashMap<>());
            documentOriginalTexts.putIfAbsent(documentId, new ConcurrentHashMap<>());
            
            documentSummaries.get(documentId).put(chunkNumber, summary);
            documentOriginalTexts.get(documentId).put(chunkNumber, chunkText);
            
            // --- STITCH BOTH WHEN FINISHED ---
            if (documentSummaries.get(documentId).size() == totalChunks) {
                System.out.println("All chunks complete for " + symbol + ". Saving to database...");
                
                StringBuilder fullSummaryBuilder = new StringBuilder();
                StringBuilder fullOriginalTextBuilder = new StringBuilder();
                
                for (int i = 1; i <= totalChunks; i++) {
                    fullSummaryBuilder.append(documentSummaries.get(documentId).get(i)).append("\n\n");
                    fullOriginalTextBuilder.append(documentOriginalTexts.get(documentId).get(i)).append("\n");
                }
                
                AnnouncementSummary entity = new AnnouncementSummary();
                entity.setSymbol(symbol);
                entity.setDocumentId(documentId);
                entity.setTotalChunks(totalChunks);
                entity.setSummaryText(fullSummaryBuilder.toString().trim());

                summaryRepository.save(entity);
                System.out.println("SAVED TO DB successfully!");
                
                // Clean up memory
                documentSummaries.remove(documentId);
                documentOriginalTexts.remove(documentId);
            }
            
        } catch (Exception e) {
            System.out.println("Failed to process message on Partition " + partition + ": " + e.getMessage());
        }
    }
}