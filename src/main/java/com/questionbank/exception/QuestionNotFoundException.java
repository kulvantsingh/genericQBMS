package com.questionbank.exception;

public class QuestionNotFoundException extends RuntimeException {
    public QuestionNotFoundException(Long id) {
        super("Question not found with id: " + id);
    }
}
