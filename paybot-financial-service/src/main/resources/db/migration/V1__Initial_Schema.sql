-- PayBot Initial Schema
-- Flyway migration V1

-- Bills table (central entity)
CREATE TABLE bills (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    biller_name VARCHAR(255) NOT NULL,
    bill_type VARCHAR(255) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    due_date DATE NOT NULL,
    billing_period_start DATE NOT NULL,
    billing_period_end DATE NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    account_number VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Payments table (references bills)
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    bill_id BIGINT NOT NULL REFERENCES bills(id),
    amount_paid DECIMAL(10, 2) NOT NULL,
    payment_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmation_number VARCHAR(255),
    payment_method VARCHAR(255) NOT NULL
);

-- Scheduled payments table (references bills)
CREATE TABLE scheduled_payments (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    bill_id BIGINT NOT NULL REFERENCES bills(id),
    scheduled_date TIMESTAMP NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    confirmation_number VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    executed_at TIMESTAMP,
    failure_reason VARCHAR(500)
);

-- Indexes for common queries
CREATE INDEX idx_bills_user_id ON bills(user_id);
CREATE INDEX idx_bills_status ON bills(status);
CREATE INDEX idx_bills_user_status ON bills(user_id, status);
CREATE INDEX idx_payments_bill_id ON payments(bill_id);
CREATE INDEX idx_scheduled_payments_user_id ON scheduled_payments(user_id);
CREATE INDEX idx_scheduled_payments_status ON scheduled_payments(status);
CREATE INDEX idx_scheduled_payments_scheduled_date ON scheduled_payments(scheduled_date);
