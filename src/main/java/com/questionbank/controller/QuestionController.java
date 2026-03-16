package com.questionbank.controller;

import com.questionbank.dto.ApiResponse;
import com.questionbank.dto.QuestionRequest;
import com.questionbank.dto.QuestionResponse;
import com.questionbank.dto.StatsResponse;
import com.questionbank.service.QuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/questions")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")   // Adjust for production
public class QuestionController {

    private final QuestionService questionService;

    // ── POST /api/v1/questions ───────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<ApiResponse<QuestionResponse>> create(
            @Valid @RequestBody QuestionRequest request) {
        QuestionResponse created = questionService.create(request);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Question created successfully", created));
    }

    // ── GET /api/v1/questions ────────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<ApiResponse<List<QuestionResponse>>> getAll(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String search) {
        List<QuestionResponse> questions = questionService.getAll(type, difficulty, subject, search);
        return ResponseEntity.ok(ApiResponse.ok(questions));
    }

    // ── GET /api/v1/questions/{id} ───────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<QuestionResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(questionService.getById(id)));
    }

    // ── PUT /api/v1/questions/{id} ───────────────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<QuestionResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody QuestionRequest request) {
        QuestionResponse updated = questionService.update(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Question updated successfully", updated));
    }

    // ── PATCH /api/v1/questions/{id} ─────────────────────────────────────────
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<QuestionResponse>> patch(
            @PathVariable Long id,
            @RequestBody QuestionRequest request) {
        QuestionResponse patched = questionService.patch(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Question patched successfully", patched));
    }

    // ── DELETE /api/v1/questions/{id} ────────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        questionService.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true)
            .message("Question deleted successfully")
            .build());
    }

    // ── GET /api/v1/questions/stats ──────────────────────────────────────────
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<StatsResponse>> getStats() {
        return ResponseEntity.ok(ApiResponse.ok(questionService.getStats()));
    }
}
