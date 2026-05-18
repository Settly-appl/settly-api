package pl.settly.settly_api.ai.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;
import pl.settly.settly_api.ai.dto.DataForSingleExpenseFromReceiptResponse;
import pl.settly.settly_api.ai.dto.ItemsFromReceiptWithCategoryResponse;

@Service
public class AiGeminiService {

  private static final Logger log = LoggerFactory.getLogger(AiGeminiService.class);

  private static final String ITEMS_PROMPT =
      """
         Przeanalizuj to zdjęcie paragonu, przypisz wydatek do odpowiedniej kategorii oraz wyciągnij listę zakupionych pozycji (nazwy i ceny).

         WYTYCZNE DOTYCZĄCE STRUKTURY DANYCH:
         Oczekuję odpowiedzi zawierającej dwie sekcje:
         1. "category" (Kategoria wydatku): Jedna ogólna kategoria dla całego paragonu.
         2. "items" (Lista pozycji): Lista wszystkich produktów z ich nazwami i ostatecznymi cenami.

         ZASADY DOTYCZĄCE KATEGORII (category):
         Przeanalizuj produkty na paragonie i przypisz CAŁY wydatek do jednej, najbardziej pasującej kategorii z poniższej listy. Zwróć dokładnie jej identyfikator:
         - 'shopping' (dla zakupów ogólnych, ubrań, chemii itp.)
         - 'food' (dla artykułów spożywczych, restauracji, kawiarni, fast foodów)
         - 'transport' (dla paliwa, biletów komunikacji, taksówek)
         - 'entertainment' (dla kin, biletów na wydarzenia, gier)
         - 'health' (dla aptek, lekarzy, leków)
         - 'others' (jeśli wydatek nie pasuje do żadnej z powyższych)

         BARDZO WAŻNE ZASADY DOTYCZĄCE CENY PRODUKTÓW (price):
         1. Interesuje mnie WYŁĄCZNIE ostateczna kwota po prawej stronie, którą klient faktycznie zapłacił za dany produkt.
         2. Jeśli pod produktem występuje linijka "OPUST" lub "ZNIŻKA", zignoruj cenę początkową (np. 23,08) oraz wartość zniżki (np. -21,96). Wyciągnij ostateczną, najniższą kwotę zapisaną pod nimi (np. 1,12).
         3. Cena musi być czystą liczbą zmiennoprzecinkową (użyj kropki jako separatora dziesiętnego, np. 1.12 zamiast 1,12).

         Przykład interpretacji ceny:
         Produkt "Mielone Z łop 500g" ma cenę początkową 23,08, opust -21,96, a końcowa cena po prawej stronie pod opustem to 1,12. Prawidłowy wynik dla tego produktu to: price: 1.12.
         """;

  private static final String SINGLE_EXPENSE_PROMPT =
      """
      Przeanalizuj to zdjęcie paragonu i wyciągnij z niego podsumowanie wydatku, dopasowując dane do poniższych wytycznych:

      1. "currency" (Waluta wydatku):
         Wybierz i zwróć WYŁĄCZNIE jeden z poniższych kodów walut:
         - PLN
         - EUR
         - USD
         - GBP
         Jeśli waluty nie ma na liście, spróbuj dopasować najbardziej prawdopodobną lub zwróć PLN jako domyślną.

      2. "category" (Kategoria wydatku):
         Przeanalizuj produkty na paragonie i przypisz CAŁY wydatek do jednej, najbardziej pasującej kategorii z poniższej listy. Zwróć dokładnie jej identyfikator (np. 'food'):
         - 'shopping' (dla zakupów ogólnych, ubrań, chemii itp.)
         - 'food' (dla artykułów spożywczych, restauracji, kawiarni, fast foodów)
         - 'transport' (dla paliwa, biletów komunikacji, taksówek)
         - 'entertainment' (dla kin, biletów na wydarzenia, gier)
         - 'health' (dla aptek, lekarzy, leków)
         - 'others' (jeśli wydatek nie pasuje do żadnej z powyższych)

      3. "totalAmount" (Cena):
         Wyciągnij łączną, ostateczną kwotę do zapłaty z całego paragonu (Suma / RAZEM).

      BARDZO WAŻNE ZASADY DOTYCZĄCE KWOTY (totalAmount):
      - Interesuje mnie WYŁĄCZNIE ostateczna kwota końcowa, którą klient faktycznie zapłacił.
      - Wartość musi być czystą liczbą zmiennoprzecinkową (użyj kropki jako separatora dziesiętnego, np. 45.20 zamiast 45,20).
      - Zignoruj wszelkie sumy cząstkowe przed rabatami – liczy się tylko ostateczny koszt transakcji.
      """;

  private final ChatClient chatClient;

  public AiGeminiService(ChatClient.Builder chatClientBuilder) {
    this.chatClient = chatClientBuilder.build();
  }

  public ItemsFromReceiptWithCategoryResponse getItemsAndCategoryFromReceipt(
      MultipartFile receipt) {
    if (receipt.isEmpty()) {
      return new ItemsFromReceiptWithCategoryResponse("others", List.of());
    }

    try {
      ItemsFromReceiptWithCategoryResponse response =
          chatClient
              .prompt()
              .user(u -> u.text(ITEMS_PROMPT).media(toMedia(receipt)))
              .call()
              .entity(ItemsFromReceiptWithCategoryResponse.class);

      return response != null
          ? response
          : new ItemsFromReceiptWithCategoryResponse("others", List.of());
    } catch (Exception e) {
      log.error("Błąd podczas przetwarzania paragonu przez Gemini", e);
      return new ItemsFromReceiptWithCategoryResponse("others", List.of());
    }
  }

  public DataForSingleExpenseFromReceiptResponse getDataForSingleExpenseFromReceipt(
      MultipartFile receipt) {
    if (receipt.isEmpty()) return null;

    try {
      return chatClient
          .prompt()
          .user(u -> u.text(SINGLE_EXPENSE_PROMPT).media(toMedia(receipt)))
          .call()
          .entity(DataForSingleExpenseFromReceiptResponse.class);
    } catch (Exception e) {
      log.error("Błąd podczas przetwarzania paragonu przez Gemini", e);
      return null;
    }
  }

  private Media toMedia(MultipartFile file) {
    MimeType mimeType = MimeTypeUtils.parseMimeType(resolveContentType(file));
    return new Media(mimeType, file.getResource());
  }

  private String resolveContentType(MultipartFile file) {
    String contentType = file.getContentType();
    if (contentType != null && !contentType.equals("application/octet-stream")) {
      return contentType;
    }
    String fileName = file.getOriginalFilename();
    if (fileName != null && fileName.toLowerCase().endsWith(".png")) {
      return "image/png";
    }
    return "image/jpeg";
  }
}
