package pl.settly.settly_api.expenses.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import pl.settly.settly_api.expenses.model.Expense;

@Mapper(componentModel = "spring")
public interface ExpenseMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "projectId", ignore = true)
    @Mapping(target = "isScanned", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Expense toExpense(RequestExpense request);

    @Mapping(source = "userId.id", target = "userId")
    @Mapping(source = "projectId.id", target = "projectId")
    @Mapping(source = "date", target = "date")
    RequestExpenseResponse toExpenseResponse(Expense expense);
}
