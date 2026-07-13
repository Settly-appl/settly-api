package pl.settly.settly_api.notifications.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.settly.settly_api.expenses.dto.DebtorSummary;
import pl.settly.settly_api.expenses.repository.ExpenseSplitRepository;

/**
 * Once a day, nudges everyone who still owes money to settle up.
 *
 * <p>Only debtors are reminded — being owed money is not something you can act on, so reminding
 * creditors would just be noise. Users with no device tokens are skipped by {@link
 * NotificationService#sendToUser}, and if FCM is not configured the send is a no-op, so this is
 * safe to leave enabled everywhere.
 */
@Component
public class SettlementReminderJob {

  private static final Logger log = LoggerFactory.getLogger(SettlementReminderJob.class);

  private final ExpenseSplitRepository expenseSplitRepository;
  private final NotificationService notificationService;
  private final boolean enabled;

  public SettlementReminderJob(
      ExpenseSplitRepository expenseSplitRepository,
      NotificationService notificationService,
      @Value("${settly.reminders.settlement.enabled:true}") boolean enabled) {
    this.expenseSplitRepository = expenseSplitRepository;
    this.notificationService = notificationService;
    this.enabled = enabled;
  }

  /** 18:00 Warsaw time — late enough that the day's expenses are in, early enough to act on. */
  @Scheduled(cron = "${settly.reminders.settlement.cron:0 0 18 * * *}", zone = "Europe/Warsaw")
  public void remindDebtors() {
    if (!enabled) {
      return;
    }
    sendReminders();
  }

  /**
   * Sends the reminders right now and reports how many people were nudged.
   *
   * <p>Deliberately ignores the {@code enabled} flag: that switch governs the daily schedule, and
   * an admin explicitly firing the reminder should work even when the schedule is off.
   */
  public int sendReminders() {
    List<DebtorSummary> debtors = expenseSplitRepository.findDebtorsWithUnsettledShares();
    if (debtors.isEmpty()) {
      return 0;
    }

    for (DebtorSummary debtor : debtors) {
      long count = debtor.getUnsettledCount();
      BigDecimal total =
          debtor.getTotal() == null
              ? BigDecimal.ZERO
              : debtor.getTotal().setScale(2, RoundingMode.HALF_UP);

      notificationService.sendToUser(
          debtor.getUserId(),
          "Masz nierozliczone wydatki",
          "Do rozliczenia: " + expensesPlural(count) + " na łączną kwotę " + total + " zł.",
          Map.of("type", "SETTLEMENT_REMINDER"));
    }

    log.info("Sent settle-up reminders to {} user(s)", debtors.size());
    return debtors.size();
  }

  /**
   * Polish counted-noun forms: 1 wydatek, 2-4 wydatki, 5+ wydatków — with the 12-14 exception (12
   * wydatków, not "12 wydatki").
   */
  public static String expensesPlural(long count) {
    long lastTwo = count % 100;
    long last = count % 10;

    if (count == 1) {
      return "1 wydatek";
    }
    if (last >= 2 && last <= 4 && !(lastTwo >= 12 && lastTwo <= 14)) {
      return count + " wydatki";
    }
    return count + " wydatków";
  }
}
