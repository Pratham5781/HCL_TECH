ALTER TABLE ticket
    ADD COLUMN escalated BOOLEAN NOT NULL DEFAULT FALSE AFTER assigned_to,
    ADD COLUMN closed_at TIMESTAMP(6) NULL AFTER resolved_at,
    ADD COLUMN sla_minutes INT NOT NULL DEFAULT 1440 AFTER due_at;

UPDATE ticket SET closed_at=resolved_at WHERE status='CLOSED' AND resolved_at IS NOT NULL;

CREATE INDEX idx_ticket_service_status_priority ON ticket(service_id, status, priority, due_at);
CREATE INDEX idx_ticket_assignee_status ON ticket(assigned_to, status, created_at);
