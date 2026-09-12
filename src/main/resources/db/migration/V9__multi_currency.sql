-- Multi-currency support.
--
-- Currency was already captured per expense, but every aggregate above it
-- (balances, project totals, the settle-up reminder) summed raw amounts across
-- currencies. Each expense now also carries the rate the user actually got when
-- they bought the foreign currency, and the resulting amount in their base
-- currency, so those aggregates have one comparable number to sum.
--
-- Convention: rate_to_base is how many base units one unit of `currency` is
-- worth (1 GBP = 4.85 PLN -> 4.85), so base_amount = total_amount * rate_to_base.

ALTER TABLE public.users
    ADD COLUMN base_currency varchar(3) DEFAULT 'PLN' NOT NULL;

ALTER TABLE public.expenses
    ADD COLUMN base_currency varchar(3)    DEFAULT 'PLN' NOT NULL,
    ADD COLUMN rate_to_base  numeric(18,8) DEFAULT 1     NOT NULL,
    ADD COLUMN base_amount   numeric(12,2) NULL;

ALTER TABLE public.expense_splits
    ADD COLUMN base_amount numeric(12,2) DEFAULT 0 NOT NULL;

ALTER TABLE public.debts
    ADD COLUMN currency varchar(3) DEFAULT 'PLN' NOT NULL;

-- A project is where a trip's rate belongs: you buy the pounds once, not per
-- expense. Expenses in the project inherit these and may override them.
ALTER TABLE public.projects
    ADD COLUMN default_currency     varchar(3)    NULL,
    ADD COLUMN default_rate_to_base numeric(18,8) NULL;

-- Backfill: every existing amount was already displayed as if it were the
-- user's base currency (all the aggregate views hardcoded "zl"), so treating
-- each row as already-in-base preserves current behaviour exactly rather than
-- silently restating history. Rows created from here on need a real rate
-- whenever their currency differs from the base - the service refuses
-- otherwise, so no foreign expense can quietly land at rate 1.
UPDATE public.expenses SET base_currency = currency, rate_to_base = 1, base_amount = total_amount;
UPDATE public.expense_splits SET base_amount = amount;

-- The defaults existed only to backfill the NOT NULL columns in one statement. Drop
-- the two where falling back would be wrong money rather than a sensible guess: a
-- silent rate of 1 books GBP as PLN, and a silent share of 0 erases a debt. Every
-- write goes through the service, which always supplies both, so an insert that
-- omits them is a bug and should fail loudly.
ALTER TABLE public.expenses ALTER COLUMN rate_to_base DROP DEFAULT;
ALTER TABLE public.expense_splits ALTER COLUMN base_amount DROP DEFAULT;
