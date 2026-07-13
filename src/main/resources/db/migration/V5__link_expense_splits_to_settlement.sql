-- Records which settle-up (a `debts` row) settled a given split.
--
-- Balances are derived live from unsettled splits, while `debts` is the payment
-- audit trail. Without this link the two can contradict each other: if a bulk
-- settle-up marked three splits paid and one of them is later unsettled on its
-- own, the balance would claim the money is owed again even though it was
-- actually handed over. Knowing which settlement covered a split lets us refuse
-- that partial undo and offer to reverse the whole settle-up instead.
--
-- Splits settled individually (not via a settle-up) leave this NULL and stay
-- freely unsettleable.
ALTER TABLE expense_splits
    ADD COLUMN settled_by_debt_id UUID;

ALTER TABLE expense_splits
    ADD CONSTRAINT fk_expense_splits_settled_by_debt
        FOREIGN KEY (settled_by_debt_id) REFERENCES debts (id) ON DELETE SET NULL;

CREATE INDEX idx_expense_splits_settled_by_debt
    ON expense_splits (settled_by_debt_id);
