package com.questionbank.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.questionbank.model.ComprehensiveSubQuestion;
import com.questionbank.model.MatchPair;
import com.questionbank.model.QuestionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class QuestionRequest {

    @NotNull(message = "Question type is required")
    private QuestionType type;

    @NotBlank(message = "Question text is required")
    private String question;

    private List<String> options;

    private JsonNode correctAnswer;

    private List<MatchPair> pairs;

    private List<ComprehensiveSubQuestion> subQuestions;

    @Pattern(regexp = "Easy|Medium|Hard", message = "Difficulty must be Easy, Medium, or Hard")
    private String difficulty = "Medium";

    @Size(max = 100)
    private String subject = "General Knowledge";

    @Min(value = 1, message = "Points must be at least 1")
    @Max(value = 100, message = "Points cannot exceed 100")
    private Integer points = 1;

    private String explanation;

    private String tags;

    @Size(max = 255)
    private String bookName;

    @Size(max = 255)
    private String bookEdition;

    @Size(max = 50)
    private String isbn;

    @Size(max = 100)
    private String etgNumber;

    @Size(max = 50)
    private String pageNumber;

    @Size(max = 50)
    private String questionNumber;
}
