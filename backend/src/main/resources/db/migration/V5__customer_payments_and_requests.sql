ALTER TABLE payment
    ADD COLUMN failure_reason VARCHAR(500) NULL AFTER status,
    ADD CONSTRAINT chk_payment_amount CHECK (amount > 0);

ALTER TABLE connection_request
    ADD COLUMN document_path VARCHAR(500) NULL AFTER details,
    ADD COLUMN desired_address VARCHAR(500) NULL AFTER document_path,
    ADD COLUMN customer_notes VARCHAR(1000) NULL AFTER desired_address,
    ADD COLUMN request_status VARCHAR(24) NOT NULL DEFAULT 'PENDING' AFTER status;

UPDATE connection_request SET request_status=status;

CREATE INDEX idx_connection_request_customer_created ON connection_request(customer_id, created_at);
CREATE INDEX idx_payment_status_created ON payment(status, created_at);
