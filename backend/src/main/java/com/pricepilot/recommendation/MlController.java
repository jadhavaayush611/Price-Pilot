package com.pricepilot.recommendation;

import com.pricepilot.product.dto.ProductResponseDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ml")
@CrossOrigin(origins = "*")
public class MlController {

    private final MlService mlService;

    public MlController(MlService mlService) {
        this.mlService = mlService;
    }

    @PostMapping("/train")
    public ResponseEntity<Map<String, Object>> train(@RequestBody Map<String, Object> request) {
        String datasetVersion = (String) request.getOrDefault("datasetVersion", "1.0.0");
        int k = ((Number) request.getOrDefault("k", 10)).intValue();
        try {
            Map<String, Object> report = mlService.train(datasetVersion, k);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/evaluate")
    public ResponseEntity<Map<String, Object>> evaluate(@RequestBody Map<String, Object> request) {
        String algorithm = (String) request.get("algorithm");
        int k = ((Number) request.getOrDefault("k", 10)).intValue();
        try {
            Map<String, Object> report = mlService.evaluate(algorithm, k);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/predict/{algorithm}")
    public ResponseEntity<List<ProductResponseDTO>> predict(
            @PathVariable String algorithm,
            @RequestParam UUID userId,
            @RequestParam(defaultValue = "10") int limit) {
        List<ProductResponseDTO> predictions = mlService.predict(algorithm, userId, limit);
        return ResponseEntity.ok(predictions);
    }

    @GetMapping("/metadata/{algorithm}")
    public ResponseEntity<Map<String, Object>> getMetadata(@PathVariable String algorithm) {
        try {
            Map<String, Object> metadata = mlService.getModelMetadata(algorithm);
            return ResponseEntity.ok(metadata);
        } catch (IOException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/report")
    public ResponseEntity<Map<String, Object>> getReport() {
        try {
            Map<String, Object> report = mlService.getTrainingReport();
            return ResponseEntity.ok(report);
        } catch (IOException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }
}
