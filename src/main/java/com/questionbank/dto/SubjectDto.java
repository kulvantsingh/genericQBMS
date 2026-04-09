package com.questionbank.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@JsonDeserialize(using = SubjectDtoDeserializer.class)
public class SubjectDto {

    private Long id;

    @Size(max = 100)
    private String name;
}
