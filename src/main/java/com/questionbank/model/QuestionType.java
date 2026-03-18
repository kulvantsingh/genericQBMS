package com.questionbank.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum QuestionType {
    MCQ("mcq"),
    TRUE_FALSE("true_false"),
    MULTI_CORRECT("multi_correct"),
    MATCH_PAIR("match_pair"),
    ARRANGE_SEQUENCE("arrange_sequence"),
    COMPREHENSIVE("comprehensive");

    private final String value;

    QuestionType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static QuestionType fromValue(String value) {
        for (QuestionType type : values()) {
            if (type.value.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown question type: " + value);
    }
}
