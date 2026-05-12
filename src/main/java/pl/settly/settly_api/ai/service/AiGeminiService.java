package pl.settly.settly_api.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import pl.settly.settly_api.ai.dto.*;

@Service
public class AiGeminiService {

  private static final Logger log = LoggerFactory.getLogger(AiGeminiService.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final RestClient restClient = RestClient.create();
  private final String apiKey;

  public AiGeminiService(@Value("${gemini.api.key}") String apiKey) {
    this.apiKey = apiKey;
  }

  public List<ReceiptItem> getItemsFromReceipt(MultipartFile receipt) {
    if (receipt.isEmpty()) return List.of();

    try {
      String base64Image = Base64.getEncoder().encodeToString(receipt.getBytes());

      String prompt =
          """
                Przeanalizuj to zdjęcie paragonu i wyciągnij z niego listę zakupionych pozycji (nazwy i ceny).

                BARDZO WAŻNE ZASADY DOTYCZĄCE CENY (price):
                1. Interesuje mnie WYŁĄCZNIE ostateczna kwota po prawej stronie, którą klient faktycznie zapłacił za dany produkt.
                2. Jeśli pod produktem występuje linijka "OPUST" lub "ZNIŻKA", zignoruj cenę początkową (np. 23,08) oraz wartość zniżki (np. -21,96). Wyciągnij ostateczną, najniższą kwotę zapisaną pod nimi (np. 1,12).
                3. Cena musi być czystą liczbą zmiennoprzecinkową (użyj kropki jako separatora dziesiętnego, np. 1.12 zamiast 1,12).

                Przykład dla tego paragonu:
                Produkt "Mielone Z łop 500g" ma cenę początkową 23,08, opust -21,96, a końcowa cena po prawej stronie pod opustem to 1,12. Prawidłowy wynik to: price: 1.12.
                """;

      GeminiSchema itemSchema =
          new GeminiSchema(
              "OBJECT",
              Map.of(
                  "name", new GeminiSchema("STRING", "Nazwa produktu"),
                  "price", new GeminiSchema("NUMBER", "Cena")),
              List.of("name", "price"));

      String contentType = receipt.getContentType();

      if (contentType == null || contentType.equals("application/octet-stream")) {
        String fileName = receipt.getOriginalFilename();
        if (fileName != null && fileName.toLowerCase().endsWith(".png")) {
          contentType = "image/png";
        } else {
          contentType = "image/jpeg";
        }
      }

      GeminiRequest requestBody =
          new GeminiRequest(
              List.of(
                  new Content(
                      List.of(
                          new Part(prompt), new Part(new InlineData(contentType, base64Image))))),
              new GenerationConfig(
                  "application/json", new GeminiSchema("ARRAY", itemSchema, null, null)));

      String responseBodyStr =
          restClient
              .post()
              .uri(
                  "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key="
                      + apiKey)
              .body(requestBody)
              .retrieve()
              .body(String.class);

      if (responseBodyStr == null) return List.of();

      JsonNode response = objectMapper.readTree(responseBodyStr);

      String rawJson =
          response
              .path("candidates")
              .path(0)
              .path("content")
              .path("parts")
              .path(0)
              .path("text")
              .asText("");
      if (rawJson.isBlank()) return List.of();

      return objectMapper.readValue(rawJson.trim(), new TypeReference<List<ReceiptItem>>() {});

    } catch (Exception e) {
      log.error("Błąd podczas przetwarzania paragonu przez Gemini", e);
      return List.of();
    }
  }
}
