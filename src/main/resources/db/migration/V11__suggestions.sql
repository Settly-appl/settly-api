-- Suggestions: a free-text box in the profile for "this should work differently".
--
-- user_id is nullable and clears on account deletion rather than cascading: the
-- feedback outlives the person who wrote it, and a hard FK would otherwise make
-- deleting an account impossible once they had ever sent one.
CREATE TABLE public.suggestions (
    id         uuid          NOT NULL,
    user_id    uuid          NULL,
    content    varchar(2000) NOT NULL,
    created_at timestamp     DEFAULT now() NOT NULL,
    CONSTRAINT suggestions_pk PRIMARY KEY (id),
    CONSTRAINT suggestions_users_fk FOREIGN KEY (user_id)
        REFERENCES public.users(id) ON DELETE SET NULL
);

-- The admin list is "newest first", and nothing else reads this table.
CREATE INDEX suggestions_created_at_idx ON public.suggestions (created_at DESC);
