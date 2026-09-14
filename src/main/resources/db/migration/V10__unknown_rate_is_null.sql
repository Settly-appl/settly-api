-- An unknown exchange rate must look unknown, not like 1.
--
-- V9's backfill wrote `base_currency = currency, rate_to_base = 1` over EVERY
-- existing row. For the PLN rows that was right. For rows already in GBP/EUR/USD
-- -- the app has had a currency picker far longer than it has had conversion --
-- it recorded "one pound is worth one zloty" as though it were a fact. Balances
-- then added those pounds straight to zloty and understated every old foreign
-- debt by the size of the real rate, which is exactly the class of error the
-- conversion work existed to remove.
--
-- The rate those purchases were actually made at is not recoverable from the
-- data: it is a fact about someone's bank, not about this database. So instead
-- of guessing one, these rows are marked as having NO rate. NULL propagates
-- honestly -- SQL's SUM skips it, so an unconverted share is left OUT of a
-- balance rather than silently counted wrong -- and the API reports how many
-- expenses are in that state so the app can ask the user to supply the rate.

-- NULL is now a meaningful value in both columns: "not converted yet".
ALTER TABLE public.expenses
    ALTER COLUMN rate_to_base DROP NOT NULL,
    ALTER COLUMN rate_to_base DROP DEFAULT;

ALTER TABLE public.expense_splits
    ALTER COLUMN base_amount DROP NOT NULL,
    ALTER COLUMN base_amount DROP DEFAULT;

-- Rows V9 mis-valued: the expense is in a currency other than its owner's base,
-- yet carries the backfill's rate of 1. An expense genuinely created since V9
-- has a rate the user typed, and one in the base currency has rate 1 legitimately
-- -- neither matches this predicate.
UPDATE public.expenses e
SET base_currency = u.base_currency,
    rate_to_base  = NULL,
    base_amount   = NULL
FROM public.users u
WHERE e.user_id = u.id
  AND e.currency <> u.base_currency
  AND e.rate_to_base = 1;

-- A share of an unconverted expense has no base value either.
UPDATE public.expense_splits s
SET base_amount = NULL
FROM public.expenses e
WHERE s.expense_id = e.id
  AND e.rate_to_base IS NULL;

-- Finding the expenses still awaiting a rate is a per-user screen in the app.
CREATE INDEX IF NOT EXISTS expenses_unconverted_idx
    ON public.expenses (user_id)
    WHERE rate_to_base IS NULL;
