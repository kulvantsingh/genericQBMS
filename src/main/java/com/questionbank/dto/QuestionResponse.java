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
    private List<String> options;
    private JsonNode correctAnswer;
    private List<MatchPair> pairs;
    private List<ComprehensiveSubQuestion> subQuestions;
    private String difficulty;
    private String subject;
    private Integer points;
    private String explanation;
    private String tags;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
