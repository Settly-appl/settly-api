package pl.settly.settly_api.ai.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.settly.settly_api.ai.dto.GetItemsFromReceiptResponse;
import pl.settly.settly_api.ai.service.AiGeminiService;

@RestController
@RequestMapping("/ai")
public class AiController {

  private final AiGeminiService aiGeminiService;

  public AiController(AiGeminiService aiGeminiService) {
    this.aiGeminiService = aiGeminiService;
  }

  @PostMapping
  public ResponseEntity<GetItemsFromReceiptResponse> getItemsFromReceipt(
      @RequestParam MultipartFile receipt) {
    GetItemsFromReceiptResponse response =
        new GetItemsFromReceiptResponse(aiGeminiService.getItemsFromReceipt(receipt));
    return ResponseEntity.ok(response);
  }
}
