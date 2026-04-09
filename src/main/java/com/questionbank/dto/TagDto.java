package com.questionbank.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TagDto {

    private Long id;

    @Size(max = 100)
    private String name;
}
