package pl.settly.settly_api.expenses.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.settly.settly_api.expenses.model.Expense;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {}
