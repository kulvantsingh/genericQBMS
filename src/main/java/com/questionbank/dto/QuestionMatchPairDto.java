package com.questionbank.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class QuestionMatchPairDto {

    private Integer order;

    @NotBlank(message = "Left side is required")
    private String left;

    @NotBlank(message = "Right side is required")
    private String right;
}
