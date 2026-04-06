package com.questionbank.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.questionbank.model.ComprehensiveSubQuestion;
import com.questionbank.model.MatchPair;
import com.questionbank.model.QuestionType;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class QuestionResponse {
    private Long id;
    private QuestionType type;
    private String question;
    private String instruction;
    private List<String> options;
    private JsonNode correctAnswer;
    private List<MatchPair> pairs;
    private List<ComprehensiveSubQuestion> subQuestions;
    private String difficulty;
    private String subject;
    private Integer points;
    private String explanation;
    private String tags;
    private String bookName;
    private String bookEdition;
    private String isbn;
    private String etgNumber;
    private String pageNumber;
    private String questionNumber;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
