package com.questionbank.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sub_question_match_pairs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubQuestionMatchPair {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sub_question_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_sub_question_match_pairs_sub_question"))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private SubQuestion subQuestion;

    @Column(name = "pair_order", nullable = false)
    private Integer pairOrder;

    @Column(name = "left_side", nullable = false, columnDefinition = "TEXT")
    private String leftSide;

    @Column(name = "right_side", nullable = false, columnDefinition = "TEXT")
    private String rightSide;
}
