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
    Instant createdAt,
    Instant updatedAt) {

  public ProjectResponse withExpenses(long expenseCount, BigDecimal totalAmount) {
    return new ProjectResponse(
        id,
        name,
        description,
        ownerId,
        status,
        memberCount,
        expenseCount,
        totalAmount == null ? BigDecimal.ZERO : totalAmount,
        createdAt,
        updatedAt);
  }
}
