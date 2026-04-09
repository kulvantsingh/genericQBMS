package com.questionbank.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sub_questions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_question_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_sub_questions_question"))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Question parentQuestion;

    /** Display / evaluation order within the parent comprehensive question. */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(nullable = false, length = 20)
    @Convert(converter = QuestionTypeConverter.class)
    private QuestionType type;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(nullable = false)
    @Builder.Default
    private Integer points = 1;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    // ── Child collections ─────────────────────────────────────────────────

    @OneToMany(mappedBy = "subQuestion", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("optionOrder ASC")
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<SubQuestionOption> options = new ArrayList<>();

    @OneToMany(mappedBy = "subQuestion", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("answerOrder ASC NULLS FIRST")
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<SubQuestionAnswer> answers = new ArrayList<>();

    @OneToMany(mappedBy = "subQuestion", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("pairOrder ASC")
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<SubQuestionMatchPair> matchPairs = new ArrayList<>();
}
