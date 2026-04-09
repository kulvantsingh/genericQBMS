package com.questionbank.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sub_question_options")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubQuestionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sub_question_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_sub_question_options_sub_question"))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private SubQuestion subQuestion;

    @Column(name = "option_order", nullable = false)
    private Integer optionOrder;

    @Column(name = "option_text", nullable = false, columnDefinition = "TEXT")
    private String optionText;
}
