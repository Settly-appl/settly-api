-- public.users definition

-- Drop table

-- DROP TABLE public.users;

CREATE TABLE public.users (
	id uuid NOT NULL,
	email varchar(255) NOT NULL,
	username varchar(50) NOT NULL,
display_name varchar(50) NULL,
	avatar_url varchar(500) NULL,
	last_login timestamp NULL,
	created_at timestamp DEFAULT now() NOT NULL,
	CONSTRAINT users_pk PRIMARY KEY (id),
	CONSTRAINT users_unique UNIQUE (email),
	CONSTRAINT users_unique_1 UNIQUE (username)
);


-- public.device_tokens definition

-- Drop table

-- DROP TABLE public.device_tokens;

CREATE TABLE public.device_tokens (
	id uuid NOT NULL,
	user_id uuid NULL,
	"token" varchar(500) NOT NULL,
	created_at timestamp DEFAULT now() NOT NULL,
	CONSTRAINT device_tokens_pk PRIMARY KEY (id),
	CONSTRAINT device_tokens_users_fk FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE
);


-- public.friendships definition

-- Drop table

-- DROP TABLE public.friendships;

CREATE TABLE public.friendships (
	id uuid NOT NULL,
	requester_id uuid NULL,
	receiver_id uuid NULL,
	status varchar(20) NOT NULL,
	created_at timestamp NOT NULL,
	updated_at timestamp NOT NULL,
	CONSTRAINT friendships_pk PRIMARY KEY (id),
	CONSTRAINT friendships_users_fk FOREIGN KEY (requester_id) REFERENCES public.users(id),
	CONSTRAINT friendships_users_fk_1 FOREIGN KEY (receiver_id) REFERENCES public.users(id)
);


-- public.user_identity_providers definition

-- Drop table

-- DROP TABLE public.user_identity_providers;

CREATE TABLE public.user_identity_providers (
	id uuid NOT NULL,
	user_id uuid NOT NULL,
	email varchar(255) NOT NULL,
	provider varchar(20) NOT NULL,
	provider_id varchar(255) NOT NULL,
	created_at timestamp DEFAULT now() NOT NULL,
	CONSTRAINT user_identity_providers_pk PRIMARY KEY (id),
	CONSTRAINT user_identity_providers_users_fk FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE
);


-- public.expenses definition

-- Drop table

-- DROP TABLE public.expenses;

CREATE TABLE public.expenses (
	id uuid NOT NULL,
	user_id uuid NULL,
	project_id uuid NULL,
	shop varchar(255) NULL,
	note varchar(500) NULL,
	total_amount numeric(10, 2) NULL,
	scanned bool DEFAULT false NULL,
	"date" timestamp NOT NULL,
	created_at timestamp NOT NULL,
	CONSTRAINT expenses_pk PRIMARY KEY (id),
	CONSTRAINT expenses_users_fk FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE
);


-- public.expense_items definition

-- Drop table

-- DROP TABLE public.expense_items;

CREATE TABLE public.expense_items (
	id uuid NOT NULL,
	expense_id uuid NULL,
	"name" varchar(255) NOT NULL,
	price numeric(10, 2) NOT NULL,
	quantity numeric(10, 3) DEFAULT 1 NOT NULL,
	category varchar(50) NULL,
	CONSTRAINT expense_items_pk PRIMARY KEY (id),
	CONSTRAINT expense_items_expenses_fk FOREIGN KEY (expense_id) REFERENCES public.expenses(id)
);


-- public.expense_splits definition

-- Drop table

-- DROP TABLE public.expense_splits;

CREATE TABLE public.expense_splits (
	id uuid NOT NULL,
	expense_id uuid NULL,
	user_id uuid NULL,
	split_type varchar(20) DEFAULT '"equal"'::character varying NOT NULL,
	amount numeric(10, 2) NOT NULL,
	settled bool DEFAULT false NOT NULL,
	settled_at timestamp NULL,
	CONSTRAINT newtable_pk PRIMARY KEY (id),
	CONSTRAINT newtable_expenses_fk FOREIGN KEY (expense_id) REFERENCES public.expenses(id),
	CONSTRAINT newtable_users_fk FOREIGN KEY (user_id) REFERENCES public.users(id)
);


-- public.expense_item_splits definition

-- Drop table

-- DROP TABLE public.expense_item_splits;

CREATE TABLE public.expense_item_splits (
	id uuid NOT NULL,
	expense_item_id uuid NULL,
	user_id uuid NULL,
	CONSTRAINT expense_item_splits_pk PRIMARY KEY (id),
	CONSTRAINT expense_item_splits_expense_items_fk FOREIGN KEY (expense_item_id) REFERENCES public.expense_items(id),
	CONSTRAINT expense_item_splits_users_fk FOREIGN KEY (user_id) REFERENCES public.users(id)
);


-- public.projects definition

-- Drop table

-- DROP TABLE public.projects;

CREATE TABLE public.projects (
	id uuid NOT NULL,
	"name" varchar(255) NOT NULL,
	description varchar(500) NULL,
	owner_id uuid NULL,
	status varchar(20) DEFAULT '"active"'::character varying NULL,
	created_at timestamp DEFAULT now() NOT NULL,
	updated_at timestamp NULL,
	CONSTRAINT projects_pk PRIMARY KEY (id),
	CONSTRAINT projects_users_fk FOREIGN KEY (owner_id) REFERENCES public.users(id)
);


-- public.project_members definition

-- Drop table

-- DROP TABLE public.project_members;

CREATE TABLE public.project_members (
	id uuid NOT NULL,
	project_id uuid NULL,
	user_id uuid NULL,
	joined_at timestamp DEFAULT now() NULL,
	CONSTRAINT project_members_pk PRIMARY KEY (id),
	CONSTRAINT project_members_projects_fk FOREIGN KEY (project_id) REFERENCES public.projects(id),
	CONSTRAINT project_members_users_fk FOREIGN KEY (user_id) REFERENCES public.users(id)
);


-- public.debts definition

-- Drop table

-- DROP TABLE public.debts;

CREATE TABLE public.debts (
	id uuid NOT NULL,
	project_id uuid NULL,
	from_user uuid NULL,
	to_user uuid NULL,
	amount numeric(10, 2) NOT NULL,
	settled bool DEFAULT false NULL,
	settled_at timestamp NULL,
	created_at timestamp DEFAULT now() NOT NULL,
	CONSTRAINT debts_pk PRIMARY KEY (id),
	CONSTRAINT debts_projects_fk FOREIGN KEY (project_id) REFERENCES public.projects(id),
	CONSTRAINT debts_users_fk FOREIGN KEY (from_user) REFERENCES public.users(id),
	CONSTRAINT debts_users_fk_1 FOREIGN KEY (to_user) REFERENCES public.users(id)
);