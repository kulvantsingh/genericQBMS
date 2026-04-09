package com.questionbank.dto;

import com.questionbank.model.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SubQuestionDto {

    private Long id;
    private Integer order;

    @NotNull(message = "Sub-question type is required")
    private QuestionType type;

    @NotBlank(message = "Sub-question text is required")
    private String question;

    @Min(value = 1, message = "Sub-question points must be at least 1")
    private Integer points = 1;

    private String explanation;

    @Valid
    private List<QuestionOptionDto> options;

    @Valid
    private List<QuestionAnswerDto> answers;

    @Valid
    private List<QuestionMatchPairDto> pairs;
}
