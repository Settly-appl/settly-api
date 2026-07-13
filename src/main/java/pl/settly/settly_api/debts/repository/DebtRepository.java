package pl.settly.settly_api.debts.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.settly.settly_api.debts.model.Debt;

public interface DebtRepository extends JpaRepository<Debt, UUID> {

  @Query(
      "SELECT d FROM Debt d WHERE d.fromUser.id = :userId OR d.toUser.id = :userId"
          + " ORDER BY d.createdAt DESC")
  List<Debt> findAllForUser(@Param("userId") UUID userId);

  /**
   * Unlinks settlements from a project being deleted. debts.project_id has a FK, so without this
   * the delete would fail outright for any project that has ever been settled up — and the payment
   * record itself must survive regardless.
   */
  @Modifying
  @Query("UPDATE Debt d SET d.project = null WHERE d.project.id = :projectId")
  void detachFromProject(@Param("projectId") UUID projectId);
}
