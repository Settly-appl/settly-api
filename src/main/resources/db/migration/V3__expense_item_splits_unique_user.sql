ALTER TABLE public.expense_item_splits
    ALTER COLUMN expense_item_id SET NOT NULL,
    ALTER COLUMN user_id SET NOT NULL,
    ADD CONSTRAINT expense_item_splits_item_user_uk UNIQUE (expense_item_id, user_id);
