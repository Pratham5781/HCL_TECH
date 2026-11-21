CREATE TABLE operator_customer (
  operator_id BIGINT NOT NULL,
  customer_id BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (operator_id, customer_id),
  CONSTRAINT fk_operator_customer_operator FOREIGN KEY (operator_id) REFERENCES operator_profile(user_id),
  CONSTRAINT fk_operator_customer_customer FOREIGN KEY (customer_id) REFERENCES customer_profile(user_id),
  INDEX idx_operator_customer_customer (customer_id)
);
