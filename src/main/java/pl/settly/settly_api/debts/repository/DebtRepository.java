package pl.settly.settly_api.debts.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.settly.settly_api.debts.model.Debt;

public interface DebtRepository extends JpaRepository<Debt, UUID> {

  @Query(
      "SELECT d FROM Debt d WHERE d.fromUser.id = :userId OR d.toUser.id = :userId"
          + " ORDER BY d.createdAt DESC")
  List<Debt> findAllForUser(@Param("userId") UUID userId);
}
