package com.questionbank.exception;

import com.questionbank.dto.QuestionImportIssueDto;
import lombok.Getter;

import java.util.List;

@Getter
public class QuestionImportValidationException extends RuntimeException {

    private final List<QuestionImportIssueDto> errors;

    public QuestionImportValidationException(String message, List<QuestionImportIssueDto> errors) {
        super(message);
        this.errors = errors;
    }
}
