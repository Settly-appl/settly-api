package pl.settly.settly_api.ai.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.settly.settly_api.ai.dto.DataForSingleExpenseFromReceiptResponse;
import pl.settly.settly_api.ai.dto.ItemsFromReceiptWithCategoryResponse;
import pl.settly.settly_api.ai.service.AiGeminiService;

@RestController
@RequestMapping("/ai")
public class AiController {

  private final AiGeminiService aiGeminiService;

  public AiController(AiGeminiService aiGeminiService) {
    this.aiGeminiService = aiGeminiService;
  }

  @PostMapping
  public ResponseEntity<ItemsFromReceiptWithCategoryResponse> getItemsFromReceipt(
      @RequestParam MultipartFile receipt) {
    ItemsFromReceiptWithCategoryResponse response =
        aiGeminiService.getItemsAndCategoryFromReceipt(receipt);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/singleExpense")
  public ResponseEntity<DataForSingleExpenseFromReceiptResponse> getDataForSingleExpenseFromReceipt(
      @RequestParam MultipartFile receipt) {
    DataForSingleExpenseFromReceiptResponse response =
        aiGeminiService.getDataForSingleExpenseFromReceipt(receipt);

    return ResponseEntity.ok(response);
  }
}
