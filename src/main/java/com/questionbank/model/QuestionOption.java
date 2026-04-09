package com.questionbank.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "question_options")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_question_options_question"))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Question question;

    /**
     * Zero-based position of this option within the question.
     * Preserved so MCQ / multi-correct answer indices remain stable.
     */
    @Column(name = "option_order", nullable = false)
    private Integer optionOrder;

    @Column(name = "option_text", nullable = false, columnDefinition = "TEXT")
    private String optionText;
}
