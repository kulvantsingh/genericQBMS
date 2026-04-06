package com.questionbank.model;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Table(name = "questions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    @Convert(converter = QuestionTypeConverter.class)
    private QuestionType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(columnDefinition = "TEXT")
    private String instruction;

    // JSONB: stores List<String> for MCQ / MULTI_CORRECT
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<String> options;

    // JSONB: stores Integer (mcq), Boolean (true_false), List<Integer> (multi_correct), null (match_pair)
    @Type(JsonBinaryType.class)
    @Column(name = "correct_answer", columnDefinition = "jsonb")
    private JsonNode correctAnswer;

    // JSONB: stores List<MatchPair> for MATCH_PAIR questions
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<MatchPair> pairs;

    // JSONB: stores List<ComprehensiveSubQuestion> for COMPREHENSIVE questions
    @Type(JsonBinaryType.class)
    @Column(name = "sub_questions", columnDefinition = "jsonb")
    private List<ComprehensiveSubQuestion> subQuestions;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String difficulty = "Medium";

    @Column(nullable = false, length = 100)
    @Builder.Default
    private String subject = "General Knowledge";

    @Column(nullable = false)
    @Builder.Default
    private Integer points = 1;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column(name = "book_name", length = 255)
    private String bookName;

    @Column(name = "book_edition", length = 255)
    private String bookEdition;

    @Column(length = 50)
    private String isbn;

    @Column(name = "etg_number", length = 100)
    private String etgNumber;

    @Column(name = "page_number", length = 50)
    private String pageNumber;

    @Column(name = "question_number", length = 50)
    private String questionNumber;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
