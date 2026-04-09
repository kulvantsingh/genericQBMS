package com.questionbank.dto;

import com.questionbank.model.QuestionType;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class QuestionResponse {
    private Long id;
    private QuestionType type;
    private String question;
    private String instruction;
    private List<QuestionOptionDto> options;
    private List<QuestionAnswerDto> answers;
    private List<QuestionMatchPairDto> pairs;
    private List<SubQuestionDto> subQuestions;
    private String difficulty;
    private SubjectDto subject;
    private Integer points;
    private String explanation;
    private List<TagDto> tags;
    private BookDto book;
    private String etgNumber;
    private String pageNumber;
    private String questionNumber;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
