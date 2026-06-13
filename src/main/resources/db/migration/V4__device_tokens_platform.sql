ALTER TABLE public.device_tokens
    ADD COLUMN platform varchar(16) NOT NULL DEFAULT 'ANDROID';
ALTER TABLE public.device_tokens
    ALTER COLUMN platform DROP DEFAULT;

ALTER TABLE public.device_tokens
    ADD COLUMN updated_at timestamp DEFAULT now() NOT NULL;

ALTER TABLE public.device_tokens
    ADD CONSTRAINT device_tokens_token_unique UNIQUE ("token");
