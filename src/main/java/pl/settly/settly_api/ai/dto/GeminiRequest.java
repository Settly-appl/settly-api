package pl.settly.settly_api.ai.dto;

import java.util.List;

public record GeminiRequest(List<Content> contents, GenerationConfig generationConfig) {}
