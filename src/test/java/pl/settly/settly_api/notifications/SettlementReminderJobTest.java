package pl.settly.settly_api.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.settly.settly_api.expenses.dto.DebtorSummary;
import pl.settly.settly_api.expenses.repository.ExpenseSplitRepository;
import pl.settly.settly_api.notifications.service.NotificationService;
import pl.settly.settly_api.notifications.service.SettlementReminderJob;

@ExtendWith(MockitoExtension.class)
class SettlementReminderJobTest {

  @Mock ExpenseSplitRepository expenseSplitRepository;
  @Mock NotificationService notificationService;

  private SettlementReminderJob job(boolean enabled) {
    return new SettlementReminderJob(expenseSplitRepository, notificationService, enabled);
  }

  private DebtorSummary debtor(UUID userId, long count, String total) {
    return new DebtorSummary() {
      @Override
      public UUID getUserId() {
        return userId;
      }

      @Override
      public long getUnsettledCount() {
        return count;
      }

      @Override
      public BigDecimal getTotal() {
        return new BigDecimal(total);
      }
    };
  }

  @Test
  void should_remind_each_debtor_with_their_count_and_total() {
    UUID debtorId = UUID.randomUUID();
    given(expenseSplitRepository.findDebtorsWithUnsettledShares())
        .willReturn(List.of(debtor(debtorId, 3, "42.5")));

    job(true).remindDebtors();

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(notificationService)
        .sendToUser(eq(debtorId), eq("Masz nierozliczone wydatki"), body.capture(), any());

    assertThat(body.getValue()).contains("3 wydatki").contains("42.50");
  }

  @Test
  void should_send_nothing_when_nobody_owes() {
    given(expenseSplitRepository.findDebtorsWithUnsettledShares()).willReturn(List.of());

    job(true).remindDebtors();

    verify(notificationService, never()).sendToUser(any(), any(), any(), any());
  }

  @Test
  void should_do_nothing_when_disabled() {
    job(false).remindDebtors();

    verify(notificationService, never()).sendToUser(any(), any(), any(), any());
  }

  @Test
  void should_deep_link_to_the_reminder() {
    UUID debtorId = UUID.randomUUID();
    given(expenseSplitRepository.findDebtorsWithUnsettledShares())
        .willReturn(List.of(debtor(debtorId, 1, "10.00")));

    job(true).remindDebtors();

    ArgumentCaptor<Map<String, String>> data = ArgumentCaptor.forClass(Map.class);
    verify(notificationService).sendToUser(eq(debtorId), any(), any(), data.capture());

    assertThat(data.getValue()).containsEntry("type", "SETTLEMENT_REMINDER");
  }

  /**
   * Polish counted nouns: 1 wydatek, 2-4 wydatki, 5+ wydatków — and 12-14 are wydatków, not
   * wydatki.
   */
  @Test
  void should_use_polish_plural_forms() {
    assertThat(SettlementReminderJob.expensesPlural(1)).isEqualTo("1 wydatek");
    assertThat(SettlementReminderJob.expensesPlural(2)).isEqualTo("2 wydatki");
    assertThat(SettlementReminderJob.expensesPlural(4)).isEqualTo("4 wydatki");
    assertThat(SettlementReminderJob.expensesPlural(5)).isEqualTo("5 wydatków");
    assertThat(SettlementReminderJob.expensesPlural(12)).isEqualTo("12 wydatków");
    assertThat(SettlementReminderJob.expensesPlural(14)).isEqualTo("14 wydatków");
    assertThat(SettlementReminderJob.expensesPlural(22)).isEqualTo("22 wydatki");
    assertThat(SettlementReminderJob.expensesPlural(25)).isEqualTo("25 wydatków");
  }
}
