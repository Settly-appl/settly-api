package pl.settly.settly_api.ai.dto;

public record Part(String text, InlineData inlineData) {
  public Part(String text) {
    this(text, null);
  }

  public Part(InlineData inlineData) {
    this(null, inlineData);
  }
}
