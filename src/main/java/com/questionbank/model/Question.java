package com.questionbank.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Normalized replacement for the original single-table Question entity.
 *
 * What changed:
 *  - subject (String)       → @ManyToOne Subject
 *  - book_name/edition/isbn → @ManyToOne Book
 *  - options  (JSONB)       → @OneToMany QuestionOption
 *  - correctAnswer (JSONB)  → @OneToMany QuestionAnswer
 *  - pairs    (JSONB)       → @OneToMany QuestionMatchPair
 *  - subQuestions (JSONB)   → @OneToMany SubQuestion
 *  - tags     (TEXT CSV)    → @ManyToMany Tag
 */
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

    // ── Type / core text ─────────────────────────────────────────────────────

    @Column(nullable = false, length = 20)
    @Convert(converter = QuestionTypeConverter.class)
    private QuestionType type;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(columnDefinition = "TEXT")
    private String instruction;

    // ── Metadata ─────────────────────────────────────────────────────────────

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String difficulty = "Medium";

    @Column(nullable = false)
    @Builder.Default
    private Integer points = 1;

    @Column(columnDefinition = "TEXT")
    private String explanation;

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

    // ── Normalized FK references ──────────────────────────────────────────────

    /** Replaces the old plain-text subject column. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id",
                foreignKey = @ForeignKey(name = "fk_questions_subject"))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Subject subject;

    /** Replaces book_name / book_edition / isbn columns. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id",
                foreignKey = @ForeignKey(name = "fk_questions_book"))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Book book;

    // ── Options (MCQ, MULTI_CORRECT, ARRANGE_SEQUENCE) ────────────────────────

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("optionOrder ASC")
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<QuestionOption> options = new ArrayList<>();

    // ── Correct answers ───────────────────────────────────────────────────────

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("answerOrder ASC NULLS FIRST")
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<QuestionAnswer> answers = new ArrayList<>();

    // ── Match pairs (MATCH_PAIR) ──────────────────────────────────────────────

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("pairOrder ASC")
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<QuestionMatchPair> matchPairs = new ArrayList<>();

    // ── Sub-questions (COMPREHENSIVE) ─────────────────────────────────────────

    @OneToMany(mappedBy = "parentQuestion", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<SubQuestion> subQuestions = new ArrayList<>();

    // ── Tags (many-to-many) ───────────────────────────────────────────────────

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "question_tags",
        joinColumns        = @JoinColumn(name = "question_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id"),
        foreignKey         = @ForeignKey(name = "fk_question_tags_question"),
        inverseForeignKey  = @ForeignKey(name = "fk_question_tags_tag")
    )
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<Tag> tags = new HashSet<>();

    // ── Convenience helpers ───────────────────────────────────────────────────

    public void addOption(QuestionOption option) {
        option.setQuestion(this);
        options.add(option);
    }

    public void addAnswer(QuestionAnswer answer) {
        answer.setQuestion(this);
        answers.add(answer);
    }

    public void addMatchPair(QuestionMatchPair pair) {
        pair.setQuestion(this);
        matchPairs.add(pair);
    }

    public void addSubQuestion(SubQuestion subQuestion) {
        subQuestion.setParentQuestion(this);
        subQuestions.add(subQuestion);
    }

    public void addTag(Tag tag) {
        tags.add(tag);
        tag.getQuestions().add(this);
    }

    public void removeTag(Tag tag) {
        tags.remove(tag);
        tag.getQuestions().remove(this);
    }
}
