package com.questionbank.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class StatsResponse {
    private long total;
    private Map<String, Long> byType;
    private Map<String, Long> byDifficulty;
    private Map<String, Long> bySubject;
}
