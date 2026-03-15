-- Add UNIQUE constraint on payments.request_id for idempotency enforcement at DB level
-- Partial index: only enforce uniqueness where request_id is not null
CREATE UNIQUE INDEX uq_payments_request_id ON payments(request_id) WHERE request_id IS NOT NULL;

-- Drop the non-unique index since the unique index covers the same queries
DROP INDEX IF EXISTS idx_payments_request_id;
