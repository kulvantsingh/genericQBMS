package com.questionbank.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionImportParseResponse {

    @Builder.Default
    private List<QuestionImportQuestionDto> questions = new ArrayList<>();

    @Builder.Default
    private List<QuestionImportIssueDto> errors = new ArrayList<>();
}
