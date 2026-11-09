-- Incremental addition only. Preserve old workflow tables for rollback; migrate separately.
ALTER TABLE public.orders ADD COLUMN IF NOT EXISTS last_workflow_id varchar(36);
