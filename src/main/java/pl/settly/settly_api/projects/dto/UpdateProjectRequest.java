package pl.settly.settly_api.projects.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import pl.settly.settly_api.projects.model.ProjectStatus;

public record UpdateProjectRequest(
    @Size(max = 255) String name,
    @Size(max = 500) String description,
    ProjectStatus status,
    @Pattern(regexp = "^[A-Za-z]{3}$", message = "Currency must be a 3-letter code")
        String defaultCurrency,
    @DecimalMin(value = "0.00000001", message = "Exchange rate must be greater than 0")
        @Digits(integer = 10, fraction = 8, message = "Exchange rate is too precise")
        BigDecimal defaultRateToBase,
    LocalDate startDate,
    LocalDate endDate,
    /**
     * Removes the trip's dates. A PATCH reads an absent field as "leave this alone", so without an
     * explicit flag a span could be corrected but never taken off a project that is not a trip
     * after all. A blank string plays this role for {@code defaultCurrency}; dates have no blank.
     */
    Boolean clearDateSpan) {

  @AssertTrue(message = "End date cannot be before the start date")
  public boolean isDateSpanOrdered() {
    return startDate == null || endDate == null || !endDate.isBefore(startDate);
  }

  @AssertTrue(message = "Cannot clear and set the date span in one request")
  public boolean isDateSpanChangeUnambiguous() {
    return !Boolean.TRUE.equals(clearDateSpan) || (startDate == null && endDate == null);
  }
}
