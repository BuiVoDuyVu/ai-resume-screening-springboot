package com.example.cvmatching;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Override
    public String convertToDatabaseColumn(List<String> value) {
        return MAPPER.writeValueAsString(value == null ? List.of() : value);
    }

    @Override
    public List<String> convertToEntityAttribute(String value) {
        if (value == null) {
            return List.of();
        }
        var array = MAPPER.readTree(value);
        if (!array.isArray()) {
            throw new IllegalStateException("Danh sách kỹ năng đã lưu không hợp lệ.");
        }
        List<String> skills = new ArrayList<>(array.size());
        for (int index = 0; index < array.size(); index++) {
            if (!array.get(index).isString()) {
                throw new IllegalStateException("Kỹ năng đã lưu không hợp lệ.");
            }
            skills.add(array.get(index).asString());
        }
        return skills;
    }
}
