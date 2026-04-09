package com.questionbank.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sub_question_answers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubQuestionAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sub_question_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_sub_question_answers_sub_question"))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private SubQuestion subQuestion;

    /** "index" | "boolean" | "text" */
    @Column(name = "answer_type", nullable = false, length = 20)
    private String answerType;

    @Column(name = "answer_value", nullable = false, columnDefinition = "TEXT")
    private String answerValue;

    @Column(name = "answer_order")
    private Integer answerOrder;
}
