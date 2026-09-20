package pl.settly.settly_api.notifications.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.settly.settly_api.auth.user.model.User;
import pl.settly.settly_api.auth.user.repository.UserRepository;
import pl.settly.settly_api.common.money.CurrencyConversionService;
import pl.settly.settly_api.expenses.dto.PairDebtSummary;
import pl.settly.settly_api.expenses.repository.ExpenseSplitRepository;

/**
 * Once a day, nudges everyone who has to send money to settle up.
 *
 * <p>Only net debtors are reminded. Holding an unsettled share is not the same as owing somebody:
 * if Anna owes you 200 and you owe her 50, nothing is yours to pay — she is the one who transfers,
 * and a nudge to you is noise about a debt that settling up would cancel. So the two directions of
 * every relationship are netted first, and a reminder names only the people you come out behind
 * with, for the amount you would actually hand over.
 *
 * <p>Being owed money stays unreported for the same reason it always was: there is nothing the
 * creditor can do about it.
 *
 * <p>Users with no device tokens are skipped by {@link NotificationService#sendToUser}, and if FCM
 * is not configured the send is a no-op, so this is safe to leave enabled everywhere.
 */
@Component
public class SettlementReminderJob {

  private static final Logger log = LoggerFactory.getLogger(SettlementReminderJob.class);

  private final ExpenseSplitRepository expenseSplitRepository;
  private final NotificationService notificationService;
  private final UserRepository userRepository;
  private final boolean enabled;

  public SettlementReminderJob(
      ExpenseSplitRepository expenseSplitRepository,
      NotificationService notificationService,
      UserRepository userRepository,
      @Value("${settly.reminders.settlement.enabled:true}") boolean enabled) {
    this.expenseSplitRepository = expenseSplitRepository;
    this.notificationService = notificationService;
    this.userRepository = userRepository;
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
    List<PairDebtSummary> pairs = expenseSplitRepository.sumUnsettledByPair();
    if (pairs.isEmpty()) {
      return 0;
    }

    // Indexed both ways round, so each row can find what the other side owes back.
    Map<List<UUID>, PairDebtSummary> byPair = new HashMap<>();
    for (PairDebtSummary pair : pairs) {
      byPair.put(List.of(pair.getDebtorId(), pair.getCreditorId()), pair);
    }

    Map<UUID, BigDecimal> owedByUser = new HashMap<>();
    Map<UUID, Long> countByUser = new HashMap<>();

    for (PairDebtSummary pair : pairs) {
      long count = pair.getUndeclaredCount();
      if (count == 0) {
        // Every share toward this person is already declared paid: the ball is in
        // their court to confirm, and that is precisely what a declaration buys.
        continue;
      }

      PairDebtSummary back =
          byPair.get(List.of(pair.getCreditorId(), pair.getDebtorId()));
      BigDecimal owedBack = back == null ? BigDecimal.ZERO : nullToZero(back.getTotal());
      BigDecimal net = nullToZero(pair.getTotal()).subtract(owedBack);
      if (net.compareTo(BigDecimal.ZERO) <= 0) {
        // They owe at least as much back — settling up would move money the other way.
        continue;
      }

      owedByUser.merge(pair.getDebtorId(), net, BigDecimal::add);
      countByUser.merge(pair.getDebtorId(), count, Long::sum);
    }

    for (Map.Entry<UUID, BigDecimal> entry : owedByUser.entrySet()) {
      UUID userId = entry.getKey();
      BigDecimal total = entry.getValue().setScale(2, RoundingMode.HALF_UP);
      long count = countByUser.getOrDefault(userId, 0L);

      // The total is already in the debtor's own base currency: a share is converted into
      // the expense owner's base, and a split between people with different bases is refused.
      String currency =
          userRepository
              .findById(userId)
              .map(User::getBaseCurrency)
              .orElse(CurrencyConversionService.DEFAULT_CURRENCY);

      notificationService.sendToUser(
          userId,
          "Masz nierozliczone wydatki",
          "Do rozliczenia: "
              + expensesPlural(count)
              + " na łączną kwotę "
              + total
              + " "
              + currencySymbol(currency)
              + ".",
          Map.of("type", "SETTLEMENT_REMINDER"));
    }

    log.info("Sent settle-up reminders to {} user(s)", owedByUser.size());
    return owedByUser.size();
  }

  /**
   * A share with no exchange rate carries no base amount and stays out of the sum, exactly as it
   * stays out of a balance.
   */
  private static BigDecimal nullToZero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
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

  /** What Poles actually write after an amount; anything else reads better as its code. */
  private static String currencySymbol(String currency) {
    return "PLN".equals(currency) ? "zł" : currency;
  }
}
