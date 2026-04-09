package com.questionbank.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class QuestionOptionDto {

    private Integer order;

    @NotBlank(message = "Option text is required")
    private String text;
}
