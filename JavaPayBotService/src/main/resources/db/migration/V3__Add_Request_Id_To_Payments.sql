-- Add request_id column to payments table for idempotency tracking
ALTER TABLE payments ADD COLUMN request_id VARCHAR(255);
CREATE INDEX idx_payments_request_id ON payments(request_id);
