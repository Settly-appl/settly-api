package pl.settly.settly_api.projects.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import pl.settly.settly_api.projects.model.ProjectStatus;

/**
 * A project as seen by a member.
 *
 * <p>{@code expenseCount} / {@code totalAmount} are filled in by the service (MapStruct leaves them
 * alone) from a single grouped query, so the project list can show what a project has cost without
 * a per-project lookup.
 *
 * <p>{@code totalAmount} is in {@code totalCurrency} — the viewer's base currency — because a trip
 * whose expenses are part in pounds and part in zloty has no single native total. {@code
 * defaultCurrency} / {@code defaultRateToBase} are what new expenses in the project inherit.
 */
public record ProjectResponse(
    UUID id,
    String name,
    String description,
    UUID ownerId,
    ProjectStatus status,
    long memberCount,
    long expenseCount,
    BigDecimal totalAmount,
    String totalCurrency,
    String defaultCurrency,
    BigDecimal defaultRateToBase,
    Instant createdAt,
    Instant updatedAt) {

  public ProjectResponse withExpenses(
      long expenseCount, BigDecimal totalAmount, String totalCurrency) {
    return new ProjectResponse(
        id,
        name,
        description,
        ownerId,
        status,
        memberCount,
        expenseCount,
        totalAmount == null ? BigDecimal.ZERO : totalAmount,
        totalCurrency,
        defaultCurrency,
        defaultRateToBase,
        createdAt,
        updatedAt);
  }
}
