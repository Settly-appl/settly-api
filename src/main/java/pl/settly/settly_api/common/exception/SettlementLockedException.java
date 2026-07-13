package pl.settly.settly_api.common.exception;

/**
 * Thrown when a share cannot be unsettled on its own because a bulk settle-up already cleared it.
 * The money really changed hands, so resurrecting the balance for that share would contradict the
 * recorded payment — the whole settlement has to be reversed instead.
 *
 * <p>Mapped to 409 CONFLICT so clients can recognise this case and offer "undo the settlement"
 * rather than showing a generic failure.
 */
public class SettlementLockedException extends RuntimeException {
  public SettlementLockedException(String message) {
    super(message);
  }
}
