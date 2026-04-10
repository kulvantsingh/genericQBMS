package com.questionbank.service;

import com.questionbank.dto.QuestionImportCommitRequest;
import com.questionbank.dto.QuestionImportCommitResponse;
import com.questionbank.dto.QuestionImportIssueDto;
import com.questionbank.dto.QuestionImportQuestionDto;
import com.questionbank.dto.QuestionResponse;
import com.questionbank.exception.QuestionImportValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QuestionImportCommitService {

    private final QuestionImportValidationService validationService;
    private final QuestionService questionService;

    @Transactional
    public QuestionImportCommitResponse commit(QuestionImportCommitRequest request) {
        List<QuestionImportQuestionDto> questions = request == null ? List.of() : request.getQuestions();
        List<QuestionImportIssueDto> errors = validationService.validate(questions);
        if (!errors.isEmpty()) {
            throw new QuestionImportValidationException("Import validation failed", errors);
        }

        List<QuestionResponse> imported = new ArrayList<>();
        for (QuestionImportQuestionDto question : questions) {
            imported.add(questionService.create(question));
        }

        return QuestionImportCommitResponse.builder()
            .importedCount(imported.size())
            .questions(imported)
            .build();
    }
}
