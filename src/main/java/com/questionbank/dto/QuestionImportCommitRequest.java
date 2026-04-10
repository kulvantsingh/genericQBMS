package com.questionbank.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuestionImportCommitRequest {

    private List<QuestionImportQuestionDto> questions = new ArrayList<>();
}
