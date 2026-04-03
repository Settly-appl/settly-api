package pl.settly.settly_api.expenses.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import pl.settly.settly_api.expenses.model.Expense;
import pl.settly.settly_api.expenses.model.ExpenseSplit;

@Mapper(componentModel = "spring")
public interface ExpenseMapper {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "user", ignore = true)
  @Mapping(target = "project", ignore = true)
  @Mapping(target = "isScanned", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  Expense toExpense(CreateExpenseRequest request);

  @Mapping(source = "user.id", target = "userId")
  @Mapping(source = "project.id", target = "projectId")
  @Mapping(source = "date", target = "date")
  ExpenseResponse toExpenseResponse(Expense expense);

  @Mapping(source = "expense.id", target = "expenseId")
  @Mapping(source = "user.id", target = "userId")
  @Mapping(source = "expenseSplitType", target = "splitType")
  ExpenseSplitResponse toExpenseSplitResponse(ExpenseSplit expenseSplit);
}
