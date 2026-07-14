-- A participant's own "I paid" is a claim, not a fact: only the expense owner
-- (the creditor) can confirm money actually arrived. `settled` therefore
-- becomes the owner's word alone, and a participant marking their share paid
-- lands here instead — a declaration shown to the owner as a suggestion to
-- double-check and confirm (or ignore). Declarations do not affect balances;
-- they are cleared when the owner settles the share (resolved) and when a
-- settle-up clears it.
ALTER TABLE expense_splits
    ADD COLUMN declared_paid BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE expense_splits
    ADD COLUMN declared_at TIMESTAMP;
