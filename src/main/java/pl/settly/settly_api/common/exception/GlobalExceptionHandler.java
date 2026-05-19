package pl.settly.settly_api.common.exception;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
    ErrorResponse error =
        new ErrorResponse(HttpStatus.NOT_FOUND.value(), ex.getMessage(), LocalDateTime.now());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
    ErrorResponse error =
        new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), LocalDateTime.now());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ValidationErrorResponse> handleValidation(
      MethodArgumentNotValidException ex) {
    Map<String, String> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .collect(
                Collectors.toMap(
                    FieldError::getField,
                    f -> f.getDefaultMessage() != null ? f.getDefaultMessage() : "invalid",
                    (first, second) -> first));

    ValidationErrorResponse error =
        new ValidationErrorResponse(
            HttpStatus.BAD_REQUEST.value(), "Validation failed", fieldErrors, LocalDateTime.now());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  public record ErrorResponse(int status, String message, LocalDateTime timestamp) {}

  public record ValidationErrorResponse(
      int status, String message, Map<String, String> errors, LocalDateTime timestamp) {}
}
