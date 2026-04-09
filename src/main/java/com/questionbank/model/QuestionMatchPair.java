package com.questionbank.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "question_match_pairs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionMatchPair {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_question_match_pairs_question"))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Question question;

    @Column(name = "pair_order", nullable = false)
    private Integer pairOrder;

    @Column(name = "left_side", nullable = false, columnDefinition = "TEXT")
    private String leftSide;

    @Column(name = "right_side", nullable = false, columnDefinition = "TEXT")
    private String rightSide;
}
