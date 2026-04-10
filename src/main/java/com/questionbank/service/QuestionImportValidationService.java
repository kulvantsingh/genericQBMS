package com.questionbank.service;

import com.questionbank.dto.QuestionAnswerDto;
import com.questionbank.dto.QuestionImportIssueDto;
import com.questionbank.dto.QuestionImportQuestionDto;
import com.questionbank.dto.QuestionMatchPairDto;
import com.questionbank.dto.QuestionOptionDto;
import com.questionbank.dto.SubQuestionDto;
import com.questionbank.model.QuestionType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class QuestionImportValidationService {

    private final Validator validator;

    public List<QuestionImportIssueDto> validate(List<QuestionImportQuestionDto> questions) {
        List<QuestionImportIssueDto> errors = new ArrayList<>();
        if (questions == null || questions.isEmpty()) {
            errors.add(issue(null, "questions", "At least one question is required"));
            return errors;
        }

        for (int i = 0; i < questions.size(); i++) {
            QuestionImportQuestionDto question = questions.get(i);
            Integer sourceIndex = effectiveSourceIndex(question, i);
            if (question == null) {
                errors.add(issue(sourceIndex, "question", "Question payload is required"));
                continue;
            }

            validateBean(question, sourceIndex, errors);
            validateQuestion(question, sourceIndex, errors);
        }
        return errors;
    }

    private void validateBean(QuestionImportQuestionDto question, Integer sourceIndex, List<QuestionImportIssueDto> errors) {
        Set<ConstraintViolation<QuestionImportQuestionDto>> violations = validator.validate(question);
        violations.stream()
            .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
            .forEach(violation -> errors.add(issue(sourceIndex, violation.getPropertyPath().toString(), violation.getMessage())));
    }

    private void validateQuestion(
        QuestionImportQuestionDto question,
        Integer sourceIndex,
        List<QuestionImportIssueDto> errors
    ) {
        if (question.getType() == null) {
            errors.add(issue(sourceIndex, "type", "Question type is required"));
            return;
        }

        switch (question.getType()) {
            case MCQ -> {
                requireOptionCount(question.getOptions(), 2, sourceIndex, "options", "MCQ requires at least 2 options", errors);
                requireSingleIndexAnswer(question.getAnswers(), optionCount(question.getOptions()), sourceIndex, "answers", "MCQ requires exactly 1 index answer", errors);
            }
            case TRUE_FALSE -> requireSingleBooleanAnswer(
                question.getAnswers(), sourceIndex, "answers", "True/False requires exactly 1 boolean answer", errors);
            case MULTI_CORRECT -> {
                requireOptionCount(question.getOptions(), 2, sourceIndex, "options", "Multi-correct requires at least 2 options", errors);
                requireIndexedAnswers(question.getAnswers(), optionCount(question.getOptions()), sourceIndex, "answers",
                    "Multi-correct requires at least 1 index answer", false, false, errors);
            }
            case MATCH_PAIR -> requirePairs(question.getPairs(), sourceIndex, "pairs", errors);
            case ARRANGE_SEQUENCE -> {
                requireOptionCount(question.getOptions(), 2, sourceIndex, "options", "Arrange-sequence requires at least 2 items", errors);
                requireIndexedAnswers(question.getAnswers(), optionCount(question.getOptions()), sourceIndex, "answers",
                    "Arrange-sequence requires ordered index answers", true, true, errors);
                if (question.getOptions() != null && question.getAnswers() != null
                    && question.getOptions().size() != question.getAnswers().size()) {
                    errors.add(issue(sourceIndex, "answers", "Arrange-sequence options and answers must contain the same number of items"));
                }
            }
            case COMPREHENSIVE -> validateComprehensive(question, sourceIndex, errors);
        }
    }

    private void validateComprehensive(
        QuestionImportQuestionDto question,
        Integer sourceIndex,
        List<QuestionImportIssueDto> errors
    ) {
        if (question.getSubQuestions() == null || question.getSubQuestions().isEmpty()) {
            errors.add(issue(sourceIndex, "subQuestions", "Comprehensive question requires at least 1 sub-question"));
            return;
        }

        for (int i = 0; i < question.getSubQuestions().size(); i++) {
            SubQuestionDto subQuestion = question.getSubQuestions().get(i);
            String prefix = "subQuestions[" + i + "]";
            if (subQuestion == null) {
                errors.add(issue(sourceIndex, prefix, "Sub-question is required"));
                continue;
            }
            if (subQuestion.getType() == null) {
                errors.add(issue(sourceIndex, prefix + ".type", "Sub-question type is required"));
                continue;
            }
            if (subQuestion.getType() == QuestionType.COMPREHENSIVE) {
                errors.add(issue(sourceIndex, prefix + ".type", "Sub-question cannot itself be comprehensive"));
                continue;
            }
            if (subQuestion.getPoints() == null || subQuestion.getPoints() < 1) {
                errors.add(issue(sourceIndex, prefix + ".points", "Sub-question points must be at least 1"));
            }

            switch (subQuestion.getType()) {
                case MCQ -> {
                    requireOptionCount(subQuestion.getOptions(), 2, sourceIndex, prefix + ".options",
                        "Sub-question MCQ requires at least 2 options", errors);
                    requireSingleIndexAnswer(subQuestion.getAnswers(), optionCount(subQuestion.getOptions()), sourceIndex,
                        prefix + ".answers", "Sub-question MCQ requires exactly 1 index answer", errors);
                }
                case TRUE_FALSE -> requireSingleBooleanAnswer(subQuestion.getAnswers(), sourceIndex, prefix + ".answers",
                    "Sub-question True/False requires exactly 1 boolean answer", errors);
                case MULTI_CORRECT -> {
                    requireOptionCount(subQuestion.getOptions(), 2, sourceIndex, prefix + ".options",
                        "Sub-question Multi-correct requires at least 2 options", errors);
                    requireIndexedAnswers(subQuestion.getAnswers(), optionCount(subQuestion.getOptions()), sourceIndex,
                        prefix + ".answers", "Sub-question Multi-correct requires at least 1 index answer", false, false, errors);
                }
                case MATCH_PAIR -> requirePairs(subQuestion.getPairs(), sourceIndex, prefix + ".pairs", errors);
                case ARRANGE_SEQUENCE -> {
                    requireOptionCount(subQuestion.getOptions(), 2, sourceIndex, prefix + ".options",
                        "Sub-question Arrange-sequence requires at least 2 items", errors);
                    requireIndexedAnswers(subQuestion.getAnswers(), optionCount(subQuestion.getOptions()), sourceIndex,
                        prefix + ".answers", "Sub-question Arrange-sequence requires ordered index answers", true, true, errors);
                    if (subQuestion.getOptions() != null && subQuestion.getAnswers() != null
                        && subQuestion.getOptions().size() != subQuestion.getAnswers().size()) {
                        errors.add(issue(sourceIndex, prefix + ".answers",
                            "Sub-question Arrange-sequence options and answers must contain the same number of items"));
                    }
                }
                case COMPREHENSIVE -> {
                }
            }
        }
    }

    private void requireOptionCount(
        List<QuestionOptionDto> options,
        int minCount,
        Integer sourceIndex,
        String field,
        String message,
        List<QuestionImportIssueDto> errors
    ) {
        if (options == null || options.size() < minCount) {
            errors.add(issue(sourceIndex, field, message));
        }
    }

    private void requireSingleIndexAnswer(
        List<QuestionAnswerDto> answers,
        int optionCount,
        Integer sourceIndex,
        String field,
        String message,
        List<QuestionImportIssueDto> errors
    ) {
        if (answers == null || answers.size() != 1) {
            errors.add(issue(sourceIndex, field, message));
            return;
        }
        validateIndexAnswer(answers.get(0), optionCount, sourceIndex, field, false, errors);
    }

    private void requireSingleBooleanAnswer(
        List<QuestionAnswerDto> answers,
        Integer sourceIndex,
        String field,
        String message,
        List<QuestionImportIssueDto> errors
    ) {
        if (answers == null || answers.size() != 1) {
            errors.add(issue(sourceIndex, field, message));
            return;
        }

        QuestionAnswerDto answer = answers.get(0);
        if (!"boolean".equals(normalize(answer.getType()))) {
            errors.add(issue(sourceIndex, field, "Answer type must be boolean"));
        }
        String value = normalize(answer.getValue());
        if (!"true".equals(value) && !"false".equals(value)) {
            errors.add(issue(sourceIndex, field, "Answer value must be true or false"));
        }
    }

    private void requireIndexedAnswers(
        List<QuestionAnswerDto> answers,
        int optionCount,
        Integer sourceIndex,
        String field,
        String message,
        boolean requireUniqueCount,
        boolean oneBased,
        List<QuestionImportIssueDto> errors
    ) {
        if (answers == null || answers.isEmpty()) {
            errors.add(issue(sourceIndex, field, message));
            return;
        }

        List<QuestionAnswerDto> orderedAnswers = requireUniqueCount
            ? answers.stream().sorted(Comparator.comparing(this::safeOrder)).toList()
            : answers;

        for (QuestionAnswerDto answer : orderedAnswers) {
            validateIndexAnswer(answer, optionCount, sourceIndex, field, oneBased, errors);
        }
    }

    private void validateIndexAnswer(
        QuestionAnswerDto answer,
        int optionCount,
        Integer sourceIndex,
        String field,
        boolean oneBased,
        List<QuestionImportIssueDto> errors
    ) {
        if (!"index".equals(normalize(answer.getType()))) {
            errors.add(issue(sourceIndex, field, "Answer type must be index"));
            return;
        }

        try {
            int value = Integer.parseInt(answer.getValue());
            if (oneBased ? value < 1 || value > optionCount : value < 0 || value >= optionCount) {
                errors.add(issue(sourceIndex, field, "Answer index is out of range"));
            }
        } catch (NumberFormatException ex) {
            errors.add(issue(sourceIndex, field, oneBased
                ? "Answer value must be a one-based index"
                : "Answer value must be a zero-based index"));
        }
    }

    private void requirePairs(
        List<QuestionMatchPairDto> pairs,
        Integer sourceIndex,
        String field,
        List<QuestionImportIssueDto> errors
    ) {
        if (pairs == null || pairs.size() < 2) {
            errors.add(issue(sourceIndex, field, "Match-pair requires at least 2 pairs"));
            return;
        }

        for (QuestionMatchPairDto pair : pairs) {
            if (pair == null || isBlank(pair.getLeft()) || isBlank(pair.getRight())) {
                errors.add(issue(sourceIndex, field, "Every pair must contain non-empty left and right values"));
                return;
            }
        }
    }

    private int optionCount(List<QuestionOptionDto> options) {
        return options == null ? 0 : options.size();
    }

    private int safeOrder(QuestionAnswerDto answer) {
        return answer == null || answer.getOrder() == null ? Integer.MAX_VALUE : answer.getOrder();
    }

    private Integer effectiveSourceIndex(QuestionImportQuestionDto question, int position) {
        return question != null && question.getSourceIndex() != null ? question.getSourceIndex() : position + 1;
    }

    private QuestionImportIssueDto issue(Integer sourceIndex, String field, String message) {
        return QuestionImportIssueDto.builder()
            .sourceIndex(sourceIndex)
            .field(field)
            .message(message)
            .build();
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
