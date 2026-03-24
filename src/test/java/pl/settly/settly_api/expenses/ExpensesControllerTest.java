package pl.settly.settly_api.expenses;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.FilterChain;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.settly.settly_api.auth.config.KeycloakJwtAuthenticationConverter;
import pl.settly.settly_api.auth.config.SecurityConfig;
import pl.settly.settly_api.auth.user.filter.UserSyncFilter;
import pl.settly.settly_api.auth.user.mapper.KeycloakUserInfoMapper;
import pl.settly.settly_api.auth.user.service.UserService;
import pl.settly.settly_api.common.search.PagedResponse;
import pl.settly.settly_api.expenses.controller.ExpenseController;
import pl.settly.settly_api.expenses.dto.CreateExpenseRequest;
import pl.settly.settly_api.expenses.dto.ExpenseResponse;
import pl.settly.settly_api.expenses.service.ExpenseService;

@WebMvcTest(ExpenseController.class)
@Import(SecurityConfig.class)
class ExpensesControllerTest {

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String EXPENSE_ID = "22222222-2222-2222-2222-222222222222";
  private static final String PROJECT_ID = "33333333-3333-3333-3333-333333333333";

  @Autowired MockMvc mockMvc;

  @MockitoBean ExpenseService expenseService;
  @MockitoBean KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;
  @MockitoBean UserSyncFilter userSyncFilter;
  @MockitoBean UserService userService;
  @MockitoBean KeycloakUserInfoMapper keycloakUserInfoMapper;

  private ExpenseResponse responseExpense;
  private ExpenseResponse updatedExpense;

  @BeforeEach
  void setUp() throws Exception {
    doAnswer(
            inv -> {
              inv.getArgument(2, FilterChain.class)
                  .doFilter(inv.getArgument(0), inv.getArgument(1));
              return null;
            })
        .when(userSyncFilter)
        .doFilter(any(), any(), any());

    responseExpense =
        new ExpenseResponse(
            UUID.fromString(EXPENSE_ID),
            UUID.fromString(USER_ID),
            UUID.fromString(PROJECT_ID),
            "Test Shop",
            "Test Note",
            BigDecimal.valueOf(100.00),
            false,
            LocalDate.now(),
            Instant.now());

    updatedExpense =
        new ExpenseResponse(
            UUID.fromString(EXPENSE_ID),
            UUID.fromString(USER_ID),
            UUID.fromString(PROJECT_ID),
            "Updated Shop",
            "Updated Note",
            BigDecimal.valueOf(150.00),
            false,
            LocalDate.now(),
            Instant.now());
  }

  // region createExpense

  @Test
  void should_return_201_when_expense_created() throws Exception {
    given(
            expenseService.createExpense(
                any(CreateExpenseRequest.class), eq(UUID.fromString(USER_ID))))
        .willReturn(responseExpense);

    mockMvc
        .perform(
            post("/expenses")
                .with(user(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                    "shop": "Test Shop",
                                    "note": "Test Note",
                                    "totalAmount": 100.00,
                                    "date": "%s",
                                    "projectId": "%s"
                                }
                                """
                        .formatted(LocalDate.now(), PROJECT_ID)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(EXPENSE_ID))
        .andExpect(jsonPath("$.userId").value(USER_ID))
        .andExpect(jsonPath("$.projectId").value(PROJECT_ID))
        .andExpect(jsonPath("$.shop").value("Test Shop"))
        .andExpect(jsonPath("$.note").value("Test Note"))
        .andExpect(jsonPath("$.totalAmount").value(100.00))
        .andExpect(jsonPath("$.isScanned").value(false));

    verify(expenseService)
        .createExpense(any(CreateExpenseRequest.class), eq(UUID.fromString(USER_ID)));
  }

  @Test
  void should_return_401_when_creating_expense_without_auth() throws Exception {
    mockMvc
        .perform(
            post("/expenses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                    "shop": "Test Shop",
                                    "note": "Test Note",
                                    "totalAmount": 100.00,
                                    "date": "%s",
                                    "projectId": "%s"
                                }
                                """
                        .formatted(LocalDate.now(), PROJECT_ID)))
        .andExpect(status().isUnauthorized());
  }

  // endregion

  // region getExpense

  @Test
  void should_return_200_when_getting_expense() throws Exception {
    given(expenseService.getExpense(UUID.fromString(EXPENSE_ID), UUID.fromString(USER_ID)))
        .willReturn(responseExpense);

    mockMvc
        .perform(get("/expenses/{expenseId}", EXPENSE_ID).with(user(USER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(EXPENSE_ID))
        .andExpect(jsonPath("$.userId").value(USER_ID))
        .andExpect(jsonPath("$.projectId").value(PROJECT_ID))
        .andExpect(jsonPath("$.shop").value("Test Shop"))
        .andExpect(jsonPath("$.note").value("Test Note"))
        .andExpect(jsonPath("$.totalAmount").value(100.00))
        .andExpect(jsonPath("$.isScanned").value(false));

    verify(expenseService).getExpense(UUID.fromString(EXPENSE_ID), UUID.fromString(USER_ID));
  }

  @Test
  void should_return_401_when_getting_expense_without_auth() throws Exception {
    mockMvc.perform(get("/expenses/{expenseId}", EXPENSE_ID)).andExpect(status().isUnauthorized());
  }

  // endregion

  // region updateExpense

  @Test
  void should_return_200_when_expense_updated() throws Exception {
    given(
            expenseService.updateExpense(
                eq(UUID.fromString(EXPENSE_ID)),
                eq(UUID.fromString(USER_ID)),
                any(CreateExpenseRequest.class)))
        .willReturn(updatedExpense);

    mockMvc
        .perform(
            put("/expenses/{expenseId}", EXPENSE_ID)
                .with(user(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                    "shop": "Updated Shop",
                                    "note": "Updated Note",
                                    "totalAmount": 150.00,
                                    "date": "%s",
                                    "projectId": "%s"
                                }
                                """
                        .formatted(LocalDate.now(), PROJECT_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(EXPENSE_ID))
        .andExpect(jsonPath("$.userId").value(USER_ID))
        .andExpect(jsonPath("$.projectId").value(PROJECT_ID))
        .andExpect(jsonPath("$.shop").value("Updated Shop"))
        .andExpect(jsonPath("$.note").value("Updated Note"))
        .andExpect(jsonPath("$.totalAmount").value(150.00))
        .andExpect(jsonPath("$.isScanned").value(false));

    verify(expenseService)
        .updateExpense(
            eq(UUID.fromString(EXPENSE_ID)),
            eq(UUID.fromString(USER_ID)),
            any(CreateExpenseRequest.class));
  }

  @Test
  void should_return_401_when_updating_expense_without_auth() throws Exception {
    mockMvc
        .perform(
            put("/expenses/{expenseId}", EXPENSE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                    "shop": "Updated Shop",
                                    "note": "Updated Note",
                                    "totalAmount": 150.00,
                                    "date": "%s",
                                    "projectId": "%s"
                                }
                                """
                        .formatted(LocalDate.now(), PROJECT_ID)))
        .andExpect(status().isUnauthorized());
  }

  // endregion

  // region deleteExpense

  @Test
  void should_return_204_when_expense_deleted() throws Exception {
    mockMvc
        .perform(delete("/expenses/{expenseId}", EXPENSE_ID).with(user(USER_ID)))
        .andExpect(status().isNoContent());

    verify(expenseService).deleteExpense(UUID.fromString(EXPENSE_ID), UUID.fromString(USER_ID));
  }

  @Test
  void should_return_401_when_deleting_expense_without_auth() throws Exception {
    mockMvc
        .perform(delete("/expenses/{expenseId}", EXPENSE_ID))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void should_return_200_when_searching_expenses() throws Exception {
    // Arrange
    var pagedResponse = new PagedResponse<>(List.of(responseExpense), 0, 1);

    // Using any() for optional params can make tests less brittle to minor controller changes
    given(expenseService.searchExpenses(eq(0), eq(10), any(), any(), eq(UUID.fromString(USER_ID))))
        .willReturn(pagedResponse);

    // Act & Assert
    mockMvc
        .perform(
            get("/expenses").param("pageNumber", "0").param("pageSize", "10").with(user(USER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result").isArray())
        .andExpect(jsonPath("$.result[0].id").value(EXPENSE_ID))
        .andExpect(jsonPath("$.pageNumber").value(0))
        .andExpect(jsonPath("$.numberOfPages").value(1));

    // Optional: Verify the service was actually hit
    verify(expenseService).searchExpenses(0, 10, "createdAt", "asc", UUID.fromString(USER_ID));
  }

  // endregion
}
