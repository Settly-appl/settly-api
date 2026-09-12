package pl.settly.settly_api.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Turns an amount in whatever currency it was spent in into the user's base currency.
 *
 * <p>The rate is the user's own: what they actually got when they bought the foreign cash, not a
 * market rate we look up. It is supplied per expense (or inherited from the trip's project) and
 * snapshotted on the expense, so a rate typed today never restates what last month cost.
 *
 * <p>Direction, since it is the easiest thing in the feature to get backwards: {@code rateToBase} is
 * how many base units <em>one unit of the foreign currency</em> is worth. 1 GBP = 4.85 PLN is
 * {@code 4.85}, and base = foreign x rate.
 */
@Service
public class CurrencyConversionService {

  /** Kept in step with the app's currency picker and the receipt-scan prompt. */
  public static final Set<String> SUPPORTED = Set.of("PLN", "EUR", "USD", "GBP");

  public static final String DEFAULT_CURRENCY = "PLN";

  /** Money is reported to the grosz; rates carry more precision than that. */
  private static final int MONEY_SCALE = 2;

  /** What an expense is worth once converted, ready to be stamped onto it. */
  public record Conversion(
      String currency, String baseCurrency, BigDecimal rateToBase, BigDecimal baseAmount) {}

  /** Normalizes a currency code and rejects anything the app cannot actually display. */
  public String normalize(String code, String fallback) {
    if (code == null || code.isBlank()) {
      return fallback;
    }
    String normalized = code.trim().toUpperCase();
    if (!SUPPORTED.contains(normalized)) {
      throw new IllegalArgumentException(
          "Unsupported currency: " + code + ". Supported: " + String.join(", ", sortedSupported()));
    }
    return normalized;
  }

  private List<String> sortedSupported() {
    return SUPPORTED.stream().sorted().toList();
  }

  /** Converts one amount, rounded to the grosz. */
  public BigDecimal toBase(BigDecimal amount, BigDecimal rateToBase) {
    if (amount == null) {
      return null;
    }
    return amount.multiply(rateToBase).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  /**
   * Works out which currency an expense is in and at what rate, from the expense's own request, the
   * project it belongs to, and the user's base.
   *
   * <p>A foreign expense with no rate anywhere is refused rather than defaulted to 1: quietly
   * booking GBP 10 as PLN 10 is the failure this whole feature exists to prevent, and it is
   * invisible once it is in the database.
   */
  public Conversion resolve(
      String requestedCurrency,
      BigDecimal requestedRate,
      BigDecimal totalAmount,
      String projectCurrency,
      BigDecimal projectRate,
      String userBaseCurrency) {

    String baseCurrency = normalize(userBaseCurrency, DEFAULT_CURRENCY);
    String currency =
        normalize(
            firstPresent(requestedCurrency, projectCurrency),
            baseCurrency);

    // In the base currency the rate is 1 by definition; an inherited or stale rate
    // sent alongside it would only be a way to get the arithmetic wrong.
    if (currency.equals(baseCurrency)) {
      return new Conversion(
          currency, baseCurrency, BigDecimal.ONE, toBase(totalAmount, BigDecimal.ONE));
    }

    BigDecimal rate = requestedRate;
    if (rate == null && currency.equals(normalizeQuietly(projectCurrency))) {
      rate = projectRate;
    }
    if (rate == null) {
      throw new IllegalArgumentException(
          "Exchange rate is required for an expense in "
              + currency
              + " when your base currency is "
              + baseCurrency
              + ". Give the rate you bought "
              + currency
              + " at, or set one on the project.");
    }
    if (rate.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Exchange rate must be greater than 0");
    }

    return new Conversion(currency, baseCurrency, rate, toBase(totalAmount, rate));
  }

  /**
   * Converts each share of one expense so that the shares add up to exactly the expense's converted
   * total. Rounding each share on its own can drift a grosz or two from the total; the difference
   * goes to the share at {@code remainderIndex} - the payer, matching how the splitter already
   * hands out the odd grosz.
   *
   * <p>With no converted total to reconcile against (an itemised expense carries no header amount),
   * each share is simply converted on its own.
   */
  public List<BigDecimal> apportionToBase(
      List<BigDecimal> amounts, BigDecimal rateToBase, BigDecimal baseTotal, int remainderIndex) {

    List<BigDecimal> converted = new ArrayList<>(amounts.size());
    for (BigDecimal amount : amounts) {
      converted.add(toBase(amount, rateToBase));
    }

    if (baseTotal == null || converted.isEmpty() || remainderIndex < 0) {
      return converted;
    }

    BigDecimal sum = converted.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal drift = baseTotal.subtract(sum);
    if (drift.compareTo(BigDecimal.ZERO) != 0) {
      converted.set(remainderIndex, converted.get(remainderIndex).add(drift));
    }
    return converted;
  }

  private String firstPresent(String first, String second) {
    if (first != null && !first.isBlank()) {
      return first;
    }
    return second;
  }

  /** Project defaults are already validated on write; never let one reject a read path. */
  private String normalizeQuietly(String code) {
    if (code == null || code.isBlank()) {
      return null;
    }
    return code.trim().toUpperCase();
  }
}
