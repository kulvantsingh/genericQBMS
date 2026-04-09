package com.questionbank.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

public class SubjectDtoDeserializer extends JsonDeserializer<SubjectDto> {

    @Override
    public SubjectDto deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        TreeNode tree = parser.getCodec().readTree(parser);
        if (tree == null) {
            return null;
        }

        SubjectDto dto = new SubjectDto();
        if (tree.isValueNode()) {
            JsonNode valueNode = (JsonNode) tree;
            if (valueNode.isTextual()) {
                dto.setName(valueNode.asText());
                return dto;
            }
            if (valueNode.isNumber()) {
                dto.setId(valueNode.asLong());
                return dto;
            }
        }

        if (tree instanceof JsonNode jsonNode) {
            JsonNode idNode = jsonNode.get("id");
            JsonNode nameNode = jsonNode.get("name");

            if (idNode != null && idNode.isNumber()) {
                dto.setId(idNode.asLong());
            }
            if (nameNode != null && !nameNode.isNull()) {
                dto.setName(nameNode.asText());
            }
        }

        return dto;
    }
}
