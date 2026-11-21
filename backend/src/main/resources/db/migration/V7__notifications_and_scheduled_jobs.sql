ALTER TABLE notification
    ADD COLUMN event_key VARCHAR(190) NULL AFTER body,
    ADD COLUMN email_status VARCHAR(16) NOT NULL DEFAULT 'NOT_REQUESTED' AFTER read_at,
    ADD COLUMN email_sent_at TIMESTAMP(6) NULL AFTER email_status,
    ADD COLUMN email_error VARCHAR(500) NULL AFTER email_sent_at,
    ADD COLUMN email_attempts INT NOT NULL DEFAULT 0 AFTER email_error,
    ADD COLUMN email_next_attempt_at TIMESTAMP(6) NULL AFTER email_attempts,
    ADD UNIQUE KEY uq_notification_event_key(event_key);

CREATE TABLE bill_late_fee_entry (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    bill_id BIGINT NOT NULL,
    fee_date DATE NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_bill_late_fee_entry_bill FOREIGN KEY (bill_id) REFERENCES bill(id),
    CONSTRAINT uq_bill_late_fee_date UNIQUE (bill_id, fee_date),
    INDEX idx_late_fee_date(fee_date)
);
