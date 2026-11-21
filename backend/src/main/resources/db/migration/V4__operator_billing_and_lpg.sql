ALTER TABLE meter_reading
  ADD COLUMN consumption DECIMAL(14,3) NULL AFTER current_reading,
  ADD COLUMN meter_maximum DECIMAL(14,3) NULL AFTER consumption,
  ADD COLUMN meter_reset BOOLEAN NOT NULL DEFAULT FALSE AFTER rollover;
UPDATE meter_reading SET consumption=GREATEST(current_reading-previous_reading,0) WHERE consumption IS NULL;
ALTER TABLE meter_reading MODIFY consumption DECIMAL(14,3) NOT NULL;
CREATE INDEX idx_reading_cycle_connection ON meter_reading(cycle,connection_id);
CREATE TABLE lpg_booking (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  booking_number VARCHAR(40) NOT NULL UNIQUE,
  connection_id BIGINT NOT NULL,
  cylinder_count INT NOT NULL,
  unit_price DECIMAL(12,2) NOT NULL,
  subsidy DECIMAL(12,2) NOT NULL DEFAULT 0,
  delivery_charge DECIMAL(12,2) NOT NULL DEFAULT 0,
  status VARCHAR(24) NOT NULL DEFAULT 'BOOKED',
  bill_id BIGINT NULL,
  booked_by BIGINT NOT NULL,
  booked_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  delivered_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_lpg_booking_connection FOREIGN KEY (connection_id) REFERENCES connection(id),
  CONSTRAINT fk_lpg_booking_bill FOREIGN KEY (bill_id) REFERENCES bill(id),
  CONSTRAINT fk_lpg_booking_operator FOREIGN KEY (booked_by) REFERENCES users(id),
  CONSTRAINT chk_lpg_cylinder_count CHECK (cylinder_count BETWEEN 1 AND 20),
  INDEX idx_lpg_booking_status(status,booked_at),
  INDEX idx_lpg_booking_connection(connection_id)
);

