package com.questionbank.service;

import com.questionbank.dto.QuestionImportParseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class QuestionImportService {

    private final QuestionImportTextExtractorService textExtractorService;
    private final QuestionImportParserService parserService;

    public QuestionImportParseResponse parse(MultipartFile file) {
        String extractedText = textExtractorService.extractText(file);
        return parserService.parse(extractedText);
    }
}
