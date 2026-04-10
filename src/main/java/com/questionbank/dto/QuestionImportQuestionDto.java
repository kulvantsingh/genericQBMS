package com.questionbank.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuestionImportQuestionDto extends QuestionRequest {

    private Integer sourceIndex;

    private List<String> warnings = new ArrayList<>();
}
