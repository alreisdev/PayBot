ALTER TABLE scheduled_payments ADD COLUMN request_id VARCHAR(255);
CREATE UNIQUE INDEX uq_scheduled_payments_request_id ON scheduled_payments(request_id) WHERE request_id IS NOT NULL;
