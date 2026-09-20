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
import pl.settly.settly_api.expenses.dto.PairDebtSummary;
import pl.settly.settly_api.expenses.repository.ExpenseSplitRepository;
import pl.settly.settly_api.notifications.service.NotificationService;
import pl.settly.settly_api.notifications.service.SettlementReminderJob;

@ExtendWith(MockitoExtension.class)
class SettlementReminderJobTest {

  @Mock ExpenseSplitRepository expenseSplitRepository;
  @Mock NotificationService notificationService;
  @Mock pl.settly.settly_api.auth.user.repository.UserRepository userRepository;

  private SettlementReminderJob job(boolean enabled) {
    return new SettlementReminderJob(
        expenseSplitRepository, notificationService, userRepository, enabled);
  }

  /**
   * One direction of a relationship: what {@code debtorId} owes {@code creditorId}, over {@code
   * count} shares they have not claimed to have paid.
   */
  private PairDebtSummary owes(UUID debtorId, UUID creditorId, long count, String total) {
    return new PairDebtSummary() {
      @Override
      public UUID getDebtorId() {
        return debtorId;
      }

      @Override
      public UUID getCreditorId() {
        return creditorId;
      }

      @Override
      public BigDecimal getTotal() {
        return new BigDecimal(total);
      }

      @Override
      public long getUndeclaredCount() {
        return count;
      }
    };
  }

  @Test
  void should_remind_each_debtor_with_their_count_and_total() {
    UUID debtorId = UUID.randomUUID();
    UUID creditorId = UUID.randomUUID();
    given(expenseSplitRepository.sumUnsettledByPair())
        .willReturn(List.of(owes(debtorId, creditorId, 3, "42.5")));

    job(true).remindDebtors();

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(notificationService)
        .sendToUser(eq(debtorId), eq("Masz nierozliczone wydatki"), body.capture(), any());

    assertThat(body.getValue()).contains("3 wydatki").contains("42.50").contains("zł");
  }

  @Test
  void should_send_nothing_when_nobody_owes() {
    given(expenseSplitRepository.sumUnsettledByPair()).willReturn(List.of());

    job(true).remindDebtors();

    verify(notificationService, never()).sendToUser(any(), any(), any(), any());
  }

  @Test
  void should_do_nothing_when_disabled() {
    job(false).remindDebtors();

    verify(notificationService, never()).sendToUser(any(), any(), any(), any());
  }

  @Test
  void should_still_send_when_an_admin_forces_it_even_if_the_schedule_is_disabled() {
    // The flag switches off the daily schedule; an admin explicitly firing the
    // reminder should still work, otherwise the button would silently do nothing.
    UUID debtorId = UUID.randomUUID();
    UUID creditorId = UUID.randomUUID();
    given(expenseSplitRepository.sumUnsettledByPair())
        .willReturn(List.of(owes(debtorId, creditorId, 2, "20.00")));

    int reminded = job(false).sendReminders();

    assertThat(reminded).isEqualTo(1);
    verify(notificationService).sendToUser(eq(debtorId), any(), any(), any());
  }

  @Test
  void should_report_zero_when_nobody_owes_so_it_is_not_mistaken_for_a_failure() {
    given(expenseSplitRepository.sumUnsettledByPair()).willReturn(List.of());

    assertThat(job(true).sendReminders()).isZero();
  }

  @Test
  void should_deep_link_to_the_reminder() {
    UUID debtorId = UUID.randomUUID();
    UUID creditorId = UUID.randomUUID();
    given(expenseSplitRepository.sumUnsettledByPair())
        .willReturn(List.of(owes(debtorId, creditorId, 1, "10.00")));

    job(true).remindDebtors();

    ArgumentCaptor<Map<String, String>> data = ArgumentCaptor.forClass(Map.class);
    verify(notificationService).sendToUser(eq(debtorId), any(), any(), data.capture());

    assertThat(data.getValue()).containsEntry("type", "SETTLEMENT_REMINDER");
  }

  @Test
  void should_not_nag_someone_the_other_side_owes_more_to() {
    // The whole point: holding an unsettled share is not owing money. Anna owes
    // 200, you owe her 50 — settling up moves money to you, so a nudge to pay
    // would be about a debt that settling cancels.
    UUID me = UUID.randomUUID();
    UUID anna = UUID.randomUUID();
    given(expenseSplitRepository.sumUnsettledByPair())
        .willReturn(List.of(owes(me, anna, 2, "50.00"), owes(anna, me, 4, "200.00")));

    job(true).remindDebtors();

    verify(notificationService, never()).sendToUser(eq(me), any(), any(), any());
    verify(notificationService).sendToUser(eq(anna), any(), any(), any());
  }

  @Test
  void should_remind_for_the_net_amount_not_the_gross_one() {
    UUID me = UUID.randomUUID();
    UUID anna = UUID.randomUUID();
    given(expenseSplitRepository.sumUnsettledByPair())
        .willReturn(List.of(owes(me, anna, 3, "90.00"), owes(anna, me, 1, "40.00")));

    job(true).remindDebtors();

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(notificationService).sendToUser(eq(me), any(), body.capture(), any());
    // 90 owed minus 40 owed back: what actually changes hands on settle-up.
    assertThat(body.getValue()).contains("50.00");
    verify(notificationService, never()).sendToUser(eq(anna), any(), any(), any());
  }

  @Test
  void should_net_each_relationship_on_its_own() {
    // Being owed by one person does not pay off another: cross-netting would hide
    // a debt that nobody is going to cancel.
    UUID me = UUID.randomUUID();
    UUID anna = UUID.randomUUID();
    UUID piotr = UUID.randomUUID();
    given(expenseSplitRepository.sumUnsettledByPair())
        .willReturn(List.of(owes(me, piotr, 1, "30.00"), owes(anna, me, 1, "500.00")));

    job(true).remindDebtors();

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(notificationService).sendToUser(eq(me), any(), body.capture(), any());
    assertThat(body.getValue()).contains("30.00");
  }

  @Test
  void should_stay_quiet_when_every_share_is_already_declared_paid() {
    UUID me = UUID.randomUUID();
    UUID anna = UUID.randomUUID();
    given(expenseSplitRepository.sumUnsettledByPair())
        .willReturn(List.of(owes(me, anna, 0, "80.00")));

    job(true).remindDebtors();

    verify(notificationService, never()).sendToUser(any(), any(), any(), any());
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
