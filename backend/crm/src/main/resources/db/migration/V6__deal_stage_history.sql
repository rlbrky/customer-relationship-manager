-- Append-only log: no version, no auditing columns, no deleted_at.
-- A row is written when a deal changes stage, and never touched again.
CREATE TABLE deal_stage_history (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    deal_id    BIGINT      NOT NULL,
    -- NULL on the row written at creation: the deal came from nowhere
    from_stage VARCHAR(20) NULL,
    to_stage   VARCHAR(20) NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    changed_by VARCHAR(255),
    PRIMARY KEY (id),
    INDEX idx_dsh_deal_changed (deal_id, changed_at),
    CONSTRAINT fk_dsh_deal FOREIGN KEY (deal_id) REFERENCES deal (id)
);
