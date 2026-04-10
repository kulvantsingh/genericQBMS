package com.questionbank.dto;

import com.questionbank.model.QuestionType;
import jakarta.validation.Valid;
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

    private String instruction;

    @Valid
    private List<QuestionOptionDto> options;

    @Valid
    private List<QuestionAnswerDto> answers;

    @Valid
    private List<QuestionMatchPairDto> pairs;

    @Valid
    private List<SubQuestionDto> subQuestions;

    @Pattern(regexp = "Easy|Medium|Hard", message = "Difficulty must be Easy, Medium, or Hard")
    private String difficulty = "Medium";

    @Valid
    private SubjectDto subject;

    @Min(value = 1, message = "Points must be at least 1")
    @Max(value = 100, message = "Points cannot exceed 100")
    private Integer points = 1;

    private String explanation;

    @Valid
    private BookDto book;

    @Size(max = 100)
    private String etgNumber;

    @Size(max = 50)
    private String pageNumber;

    @Size(max = 50)
    private String questionNumber;
}
