package com.questionbank.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class QuestionAnswerDto {

    private Integer order;

    @NotBlank(message = "Answer type is required")
    private String type;

    @NotBlank(message = "Answer value is required")
    private String value;
}
