package com.questionbank.controller;

import com.questionbank.dto.ApiResponse;
import com.questionbank.dto.QuestionImportCommitRequest;
import com.questionbank.dto.QuestionImportCommitResponse;
import com.questionbank.dto.QuestionImportParseResponse;
import com.questionbank.service.QuestionImportCommitService;
import com.questionbank.service.QuestionImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/questions/import")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class QuestionImportController {

    private final QuestionImportService questionImportService;
    private final QuestionImportCommitService questionImportCommitService;

    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<QuestionImportParseResponse>> parse(@RequestPart("file") MultipartFile file) {
        QuestionImportParseResponse response = questionImportService.parse(file);
        return ResponseEntity.ok(ApiResponse.ok("Document parsed successfully", response));
    }

    @PostMapping("/commit")
    public ResponseEntity<ApiResponse<QuestionImportCommitResponse>> commit(
        @RequestBody QuestionImportCommitRequest request
    ) {
        QuestionImportCommitResponse response = questionImportCommitService.commit(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Questions imported successfully", response));
    }
}
