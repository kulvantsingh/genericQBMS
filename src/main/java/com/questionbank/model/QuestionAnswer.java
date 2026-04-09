package com.questionbank.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Stores the correct answer for a question in a normalized form.
 *
 * <ul>
 *   <li>MCQ            → answer_type="index",   answer_value="2"</li>
 *   <li>TRUE_FALSE      → answer_type="boolean", answer_value="true"</li>
 *   <li>MULTI_CORRECT   → one row per correct index, answer_type="index"</li>
 *   <li>ARRANGE_SEQUENCE→ one row per position,  answer_type="index",
 *                          answer_value="0","2","1",… (ordered by answer_order)</li>
 * </ul>
 * MATCH_PAIR questions use {@link QuestionMatchPair} instead.
 */
@Entity
@Table(name = "question_answers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_question_answers_question"))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Question question;

    /**
     * Discriminator: "index" | "boolean" | "text"
     */
    @Column(name = "answer_type", nullable = false, length = 20)
    private String answerType;

    @Column(name = "answer_value", nullable = false, columnDefinition = "TEXT")
    private String answerValue;

    /**
     * Position within an ordered answer set (ARRANGE_SEQUENCE, MULTI_CORRECT).
     * Null for single-answer types.
     */
    @Column(name = "answer_order")
    private Integer answerOrder;
}
