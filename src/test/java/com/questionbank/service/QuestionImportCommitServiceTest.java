package com.questionbank.service;

import com.questionbank.dto.QuestionAnswerDto;
import com.questionbank.dto.QuestionImportCommitRequest;
import com.questionbank.dto.QuestionImportQuestionDto;
import com.questionbank.dto.QuestionOptionDto;
import com.questionbank.dto.SubjectDto;
import com.questionbank.exception.QuestionImportValidationException;
import com.questionbank.model.QuestionType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class QuestionImportCommitServiceTest {

    private final QuestionService questionService = mock(QuestionService.class);
    private final QuestionImportCommitService commitService = new QuestionImportCommitService(
        new QuestionImportValidationService(buildValidator()),
        questionService
    );

    @Test
    void commit_rejectsInvalidQuestionAndDoesNotInsertAnything() {
        QuestionImportCommitRequest request = new QuestionImportCommitRequest();
        request.setQuestions(List.of(invalidMcqQuestion()));

        QuestionImportValidationException exception = assertThrows(
            QuestionImportValidationException.class,
            () -> commitService.commit(request)
        );

        assertEquals("Import validation failed", exception.getMessage());
        assertTrue(exception.getErrors().stream()
            .anyMatch(error -> "options".equals(error.getField())));
        verifyNoInteractions(questionService);
    }

    private QuestionImportQuestionDto invalidMcqQuestion() {
        QuestionImportQuestionDto question = new QuestionImportQuestionDto();
        question.setSourceIndex(1);
        question.setType(QuestionType.MCQ);
        question.setQuestion("<p>What is 2 + 2?</p>");
        question.setDifficulty("Easy");
        question.setPoints(1);

        SubjectDto subject = new SubjectDto();
        subject.setName("Mathematics");
        question.setSubject(subject);

        QuestionOptionDto option = new QuestionOptionDto();
        option.setOrder(1);
        option.setText("4");
        question.setOptions(List.of(option));

        QuestionAnswerDto answer = new QuestionAnswerDto();
        answer.setOrder(1);
        answer.setType("index");
        answer.setValue("0");
        question.setAnswers(List.of(answer));
        return question;
    }

    private Validator buildValidator() {
        return Validation.buildDefaultValidatorFactory().getValidator();
    }
}
