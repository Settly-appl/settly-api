ALTER TABLE public.expenses ADD COLUMN currency varchar(3) DEFAULT 'PLN' NOT NULL;
ALTER TABLE public.expenses ADD COLUMN category varchar(50) NULL;