package pl.settly.settly_api.ai.dto;

import java.util.List;
import java.util.Map;

public record GeminiSchema(
    String type,
    GeminiSchema items,
    Map<String, GeminiSchema> properties,
    List<String> required,
    String description) {
  public GeminiSchema(String type, Map<String, GeminiSchema> properties, List<String> required) {
    this(type, null, properties, required, null);
  }

  public GeminiSchema(String type, String description) {
    this(type, null, null, null, description);
  }

  public GeminiSchema(
      String type,
      GeminiSchema items,
      Map<String, GeminiSchema> properties,
      List<String> required) {
    this(type, items, properties, required, null);
  }
}
