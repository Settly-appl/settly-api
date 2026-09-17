-- When a trip runs, so expenses added during it can pick it up by themselves.
--
-- Nullable on both ends: plenty of projects are not trips at all (a shared flat,
-- a recurring split) and have no dates, and a trip whose return is not booked
-- yet has a start but no end. Only a project with BOTH dates takes part in the
-- automatic selection -- an open-ended range would swallow every future expense.
ALTER TABLE public.projects
    ADD COLUMN start_date date NULL,
    ADD COLUMN end_date   date NULL;

-- A span that ends before it starts is not a span.
ALTER TABLE public.projects
    ADD CONSTRAINT projects_date_span_ck
        CHECK (start_date IS NULL OR end_date IS NULL OR end_date >= start_date);
