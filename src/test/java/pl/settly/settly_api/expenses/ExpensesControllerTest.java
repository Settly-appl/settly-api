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
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.settly.settly_api.auth.config.KeycloakJwtAuthenticationConverter;
import pl.settly.settly_api.auth.config.SecurityConfig;
import pl.settly.settly_api.auth.user.filter.UserSyncFilter;
import pl.settly.settly_api.auth.user.mapper.KeycloakUserInfoMapper;
import pl.settly.settly_api.auth.user.service.UserService;
import pl.settly.settly_api.expenses.controller.ExpenseController;
import pl.settly.settly_api.expenses.dto.CreateExpenseItemRequest;
import pl.settly.settly_api.expenses.dto.CreateExpenseRequest;
import pl.settly.settly_api.expenses.dto.ExpenseItemResponse;
import pl.settly.settly_api.expenses.dto.ExpenseItemSplitUserResponse;
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
    // Odblokowanie filtra synchronizacji użytkownika dla testów WebMvc
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
            "FOOD",
            "PLN",
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
            "SHOPPING", // Dodana kategoria
            "PLN", // Dodana waluta
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
    // Tworzymy Pageable, który odpowiada temu, co przyjdzie z kontrolera (domyślne lub z
    // parametrów)
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

    // Tworzymy mockową odpowiedź typu Page zamiast PagedResponse
    Page<ExpenseResponse> pagedResponse =
        new PageImpl<>(
            List.of(responseExpense), pageable, 1 // total elements
            );

    // Mockujemy serwis z nową sygnaturą (Pageable zamiast wielu int/String)
    given(expenseService.searchExpenses(any(Pageable.class), any(), eq(UUID.fromString(USER_ID))))
        .willReturn(pagedResponse);

    // Act & Assert
    mockMvc
        .perform(
            get("/expenses")
                .param("page", "0") // Nowa nazwa: page zamiast pageNumber
                .param("size", "10") // Nowa nazwa: size zamiast pageSize
                .param("sort", "createdAt,desc") // Nowy format sortowania
                .with(user(USER_ID)))
        .andExpect(status().isOk())
        // W Page dane są w polu "content", a nie "result"
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].id").value(EXPENSE_ID))
        // Metadane w Page mają inne nazwy niż w Twoim starym PagedResponse
        .andExpect(jsonPath("$.number").value(0)) // numer bieżącej strony
        .andExpect(jsonPath("$.totalPages").value(1)) // suma stron
        .andExpect(jsonPath("$.totalElements").value(1)); // suma wszystkich rekordów

    // Verify: Sprawdzamy, czy serwis został wywołany z jakimkolwiek obiektem Pageable
    verify(expenseService).searchExpenses(any(Pageable.class), any(), eq(UUID.fromString(USER_ID)));
  }

  // endregion

  // region addItem

  @Test
  void should_return_201_when_item_added() throws Exception {
    String itemId = "44444444-4444-4444-4444-444444444444";
    ExpenseItemResponse itemResponse =
        new ExpenseItemResponse(
            UUID.fromString(itemId),
            UUID.fromString(EXPENSE_ID),
            "Milk",
            BigDecimal.valueOf(6.00),
            BigDecimal.ONE,
            "groceries");

    given(
            expenseService.addItem(
                eq(UUID.fromString(EXPENSE_ID)),
                eq(UUID.fromString(USER_ID)),
                any(CreateExpenseItemRequest.class)))
        .willReturn(itemResponse);

    mockMvc
        .perform(
            post("/expenses/{expenseId}/items", EXPENSE_ID)
                .with(user(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "name": "Milk",
                        "price": 6.00,
                        "quantity": 1,
                        "category": "groceries"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(itemId))
        .andExpect(jsonPath("$.expenseId").value(EXPENSE_ID))
        .andExpect(jsonPath("$.name").value("Milk"))
        .andExpect(jsonPath("$.price").value(6.00));
  }

  @Test
  void should_return_401_when_adding_item_without_auth() throws Exception {
    mockMvc
        .perform(
            post("/expenses/{expenseId}/items", EXPENSE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "name": "Milk",
                        "price": 6.00,
                        "quantity": 1
                    }
                    """))
        .andExpect(status().isUnauthorized());
  }

  // endregion

  // region getItems

  @Test
  void should_return_200_when_getting_items() throws Exception {
    String itemId = "44444444-4444-4444-4444-444444444444";
    ExpenseItemResponse itemResponse =
        new ExpenseItemResponse(
            UUID.fromString(itemId),
            UUID.fromString(EXPENSE_ID),
            "Milk",
            BigDecimal.valueOf(6.00),
            BigDecimal.ONE,
            null);

    given(expenseService.getItems(UUID.fromString(EXPENSE_ID), UUID.fromString(USER_ID)))
        .willReturn(List.of(itemResponse));

    mockMvc
        .perform(get("/expenses/{expenseId}/items", EXPENSE_ID).with(user(USER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(itemId))
        .andExpect(jsonPath("$[0].name").value("Milk"));
  }

  @Test
  void should_return_401_when_getting_items_without_auth() throws Exception {
    mockMvc
        .perform(get("/expenses/{expenseId}/items", EXPENSE_ID))
        .andExpect(status().isUnauthorized());
  }

  // endregion

  // region getItemSplitUsers

  @Test
  void should_return_200_when_getting_item_split_users() throws Exception {
    String itemId = "44444444-4444-4444-4444-444444444444";
    ExpenseItemSplitUserResponse splitUserResponse =
        new ExpenseItemSplitUserResponse(
            UUID.fromString(USER_ID), "test_user", "Test User", "https://avatar.example/test.png");

    given(expenseService.getItemSplitUsers(UUID.fromString(itemId), UUID.fromString(USER_ID)))
        .willReturn(List.of(splitUserResponse));

    mockMvc
        .perform(get("/expenses/items/{itemId}/users", itemId).with(user(USER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(USER_ID))
        .andExpect(jsonPath("$[0].username").value("test_user"))
        .andExpect(jsonPath("$[0].displayName").value("Test User"));
  }

  @Test
  void should_return_401_when_getting_item_split_users_without_auth() throws Exception {
    String itemId = "44444444-4444-4444-4444-444444444444";

    mockMvc
        .perform(get("/expenses/items/{itemId}/users", itemId))
        .andExpect(status().isUnauthorized());
  }

  // endregion

  // region deleteItem

  @Test
  void should_return_204_when_item_deleted() throws Exception {
    String itemId = "44444444-4444-4444-4444-444444444444";

    mockMvc
        .perform(
            delete("/expenses/{expenseId}/items/{itemId}", EXPENSE_ID, itemId).with(user(USER_ID)))
        .andExpect(status().isNoContent());

    verify(expenseService)
        .deleteItem(UUID.fromString(EXPENSE_ID), UUID.fromString(itemId), UUID.fromString(USER_ID));
  }

  @Test
  void should_return_401_when_deleting_item_without_auth() throws Exception {
    String itemId = "44444444-4444-4444-4444-444444444444";

    mockMvc
        .perform(delete("/expenses/{expenseId}/items/{itemId}", EXPENSE_ID, itemId))
        .andExpect(status().isUnauthorized());
  }

  // endregion
}
