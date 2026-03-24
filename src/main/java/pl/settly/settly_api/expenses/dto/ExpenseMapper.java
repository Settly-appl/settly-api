package pl.settly.settly_api.expenses.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import pl.settly.settly_api.expenses.model.Expense;

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
}
