package pl.settly.settly_api.projects.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import pl.settly.settly_api.projects.model.ProjectStatus;

public record UpdateProjectRequest(
    @Size(max = 255) String name,
    @Size(max = 500) String description,
    ProjectStatus status,
    @Pattern(regexp = "^[A-Za-z]{3}$", message = "Currency must be a 3-letter code")
        String defaultCurrency,
    @DecimalMin(value = "0.00000001", message = "Exchange rate must be greater than 0")
        @Digits(integer = 10, fraction = 8, message = "Exchange rate is too precise")
        BigDecimal defaultRateToBase) {}
