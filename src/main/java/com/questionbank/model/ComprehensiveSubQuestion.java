package com.questionbank.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComprehensiveSubQuestion {
    private QuestionType type;
    private String question;
    private List<String> options;
    private JsonNode correctAnswer;
    private List<MatchPair> pairs;
    private Integer points;
    private String explanation;
}
