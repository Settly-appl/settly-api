package pl.settly.settly_api.expenses;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.FilterChain;
import java.math.BigDecimal;
import java.time.Instant;
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
import pl.settly.settly_api.expenses.controller.ExpenseSplitController;
import pl.settly.settly_api.expenses.dto.CreateExpenseSplitRequest;
import pl.settly.settly_api.expenses.dto.ExpenseSplitResponse;
import pl.settly.settly_api.expenses.model.ExpenseSplitType;
import pl.settly.settly_api.expenses.service.ExpenseSplitService;

@WebMvcTest(ExpenseSplitController.class)
@Import(SecurityConfig.class)
class ExpenseSplitControllerTest {

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String EXPENSE_ID = "22222222-2222-2222-2222-222222222222";
  private static final String FRIEND_ID = "33333333-3333-3333-3333-333333333333";
  private static final String SPLIT_ID = "44444444-4444-4444-4444-444444444444";

  @Autowired MockMvc mockMvc;

  @MockitoBean ExpenseSplitService expenseSplitService;
  @MockitoBean KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;
  @MockitoBean UserSyncFilter userSyncFilter;
  @MockitoBean UserService userService;
  @MockitoBean KeycloakUserInfoMapper keycloakUserInfoMapper;

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
  }

  private ExpenseSplitResponse createSplitResponse(String splitType) {
    return new ExpenseSplitResponse(
        UUID.fromString(SPLIT_ID),
        UUID.fromString(EXPENSE_ID),
        UUID.fromString(USER_ID),
        ExpenseSplitType.valueOf(splitType),
        BigDecimal.valueOf(50.00),
        false,
        null);
  }

  // region createSplit

  @Test
  void should_return_201_when_equal_split_created() throws Exception {
    ExpenseSplitResponse response = createSplitResponse("EQUAL");

    given(
            expenseSplitService.createSplit(
                eq(UUID.fromString(EXPENSE_ID)),
                any(CreateExpenseSplitRequest.class),
                eq(UUID.fromString(USER_ID))))
        .willReturn(List.of(response));

    mockMvc
        .perform(
            post("/expenses/{expenseId}/splits", EXPENSE_ID)
                .with(user(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "expenseSplitType": "EQUAL",
                        "participants": [{"friendId": "%s"}]
                    }
                    """
                        .formatted(FRIEND_ID)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$[0].splitType").value("EQUAL"))
        .andExpect(jsonPath("$[0].amount").value(50.00));
  }

  @Test
  void should_return_201_when_custom_split_created() throws Exception {
    ExpenseSplitResponse response = createSplitResponse("CUSTOM");

    given(
            expenseSplitService.createSplit(
                eq(UUID.fromString(EXPENSE_ID)),
                any(CreateExpenseSplitRequest.class),
                eq(UUID.fromString(USER_ID))))
        .willReturn(List.of(response));

    mockMvc
        .perform(
            post("/expenses/{expenseId}/splits", EXPENSE_ID)
                .with(user(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "expenseSplitType": "CUSTOM",
                        "participants": [{"friendId": "%s", "amount": 30.00}]
                    }
                    """
                        .formatted(FRIEND_ID)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$[0].splitType").value("CUSTOM"));
  }

  @Test
  void should_return_201_when_by_item_split_created() throws Exception {
    ExpenseSplitResponse response = createSplitResponse("BY_ITEM");
    String itemId = "55555555-5555-5555-5555-555555555555";

    given(
            expenseSplitService.createSplit(
                eq(UUID.fromString(EXPENSE_ID)),
                any(CreateExpenseSplitRequest.class),
                eq(UUID.fromString(USER_ID))))
        .willReturn(List.of(response));

    mockMvc
        .perform(
            post("/expenses/{expenseId}/splits", EXPENSE_ID)
                .with(user(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "expenseSplitType": "BY_ITEM",
                        "participants": [{"friendId": "%s"}],
                        "itemAssignments": [
                            {"expenseItemId": "%s", "userIds": ["%s", "%s"]}
                        ]
                    }
                    """
                        .formatted(FRIEND_ID, itemId, USER_ID, FRIEND_ID)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$[0].splitType").value("BY_ITEM"));
  }

  @Test
  void should_return_401_when_creating_split_without_auth() throws Exception {
    mockMvc
        .perform(
            post("/expenses/{expenseId}/splits", EXPENSE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "expenseSplitType": "EQUAL",
                        "participants": [{"friendId": "%s"}]
                    }
                    """
                        .formatted(FRIEND_ID)))
        .andExpect(status().isUnauthorized());
  }

  // endregion

  // region getSplitsForExpense

  @Test
  void should_return_200_when_getting_splits() throws Exception {
    ExpenseSplitResponse response = createSplitResponse("EQUAL");

    given(
            expenseSplitService.getSplitsForExpense(
                UUID.fromString(EXPENSE_ID), UUID.fromString(USER_ID)))
        .willReturn(List.of(response));

    mockMvc
        .perform(get("/expenses/{expenseId}/splits", EXPENSE_ID).with(user(USER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].splitType").value("EQUAL"))
        .andExpect(jsonPath("$[0].expenseId").value(EXPENSE_ID));
  }

  @Test
  void should_return_401_when_getting_splits_without_auth() throws Exception {
    mockMvc
        .perform(get("/expenses/{expenseId}/splits", EXPENSE_ID))
        .andExpect(status().isUnauthorized());
  }

  // endregion

  // region deleteAllSplits

  @Test
  void should_return_204_when_splits_deleted() throws Exception {
    mockMvc
        .perform(delete("/expenses/{expenseId}/splits", EXPENSE_ID).with(user(USER_ID)))
        .andExpect(status().isNoContent());

    verify(expenseSplitService)
        .deleteAllSplits(UUID.fromString(EXPENSE_ID), UUID.fromString(USER_ID));
  }

  @Test
  void should_return_401_when_deleting_splits_without_auth() throws Exception {
    mockMvc
        .perform(delete("/expenses/{expenseId}/splits", EXPENSE_ID))
        .andExpect(status().isUnauthorized());
  }

  // endregion

  // region settleSplit

  @Test
  void should_return_200_when_split_settled() throws Exception {
    ExpenseSplitResponse response =
        new ExpenseSplitResponse(
            UUID.fromString(SPLIT_ID),
            UUID.fromString(EXPENSE_ID),
            UUID.fromString(FRIEND_ID),
            ExpenseSplitType.EQUAL,
            BigDecimal.valueOf(50.00),
            true,
            Instant.now());

    given(
            expenseSplitService.settleSplit(
                UUID.fromString(EXPENSE_ID), UUID.fromString(SPLIT_ID), UUID.fromString(USER_ID)))
        .willReturn(response);

    mockMvc
        .perform(
            patch("/expenses/{expenseId}/splits/{splitId}/settle", EXPENSE_ID, SPLIT_ID)
                .with(user(USER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.settled").value(true))
        .andExpect(jsonPath("$.settledAt").isNotEmpty());
  }

  @Test
  void should_return_401_when_settling_split_without_auth() throws Exception {
    mockMvc
        .perform(patch("/expenses/{expenseId}/splits/{splitId}/settle", EXPENSE_ID, SPLIT_ID))
        .andExpect(status().isUnauthorized());
  }

  // endregion

  // region getUnsettledSplits

  @Test
  void should_return_200_with_unsettled_splits() throws Exception {
    ExpenseSplitResponse response = createSplitResponse("EQUAL");

    given(expenseSplitService.getUnsettledSplits(UUID.fromString(USER_ID)))
        .willReturn(List.of(response));

    mockMvc
        .perform(get("/expenses/splits/unsettled").with(user(USER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].splitType").value("EQUAL"));
  }

  @Test
  void should_return_401_when_getting_unsettled_without_auth() throws Exception {
    mockMvc.perform(get("/expenses/splits/unsettled")).andExpect(status().isUnauthorized());
  }

  // endregion
}
