package pl.settly.settly_api.expenses.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.settly.settly_api.expenses.model.Expense;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {
  Optional<Expense> findByIdAndUser_Id(UUID id, UUID userId);

  Page<Expense> findByUser_Id(UUID userId, Pageable pageable);
}
