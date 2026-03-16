package com.questionbank.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class QuestionTypeConverter implements AttributeConverter<QuestionType, String> {

    @Override
    public String convertToDatabaseColumn(QuestionType attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public QuestionType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : QuestionType.fromValue(dbData);
    }
}
