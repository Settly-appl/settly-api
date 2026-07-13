-- Each participant's share of a single product.
--
-- Until now expense_item_splits only recorded WHO was on an item; the share was
-- recomputed as an equal division and only the per-user total survived (on
-- expense_splits.amount). That makes an unequal share of one product
-- unrepresentable — "you had the steak, I had the salad" could not be recorded.
--
-- Storing the amount also lets the API report a real per-item breakdown per
-- person instead of the client having to guess it.
ALTER TABLE expense_item_splits
    ADD COLUMN amount NUMERIC(10, 2);

-- Backfill existing rows with the equal division they implicitly had, so old
-- expenses keep their (correct) numbers: item total / number of assignees.
UPDATE expense_item_splits eis
SET amount = ROUND(
        (ei.price * ei.quantity) / GREATEST(
            (SELECT COUNT(*) FROM expense_item_splits x WHERE x.expense_item_id = eis.expense_item_id),
            1
        ), 2)
FROM expense_items ei
WHERE ei.id = eis.expense_item_id
  AND eis.amount IS NULL;
