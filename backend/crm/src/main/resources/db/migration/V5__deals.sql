CREATE TABLE deal (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    version             INT,
    title               VARCHAR(200)  NOT NULL,
    -- money is DECIMAL, never FLOAT/DOUBLE: binary floating point cannot
    -- represent 0.1 exactly, and the error compounds when values are summed
    value               DECIMAL(19,2) NULL,
    stage               VARCHAR(20)   NOT NULL,
    -- NULL outcome is the definition of an open deal
    outcome             VARCHAR(10)   NULL,
    -- DATE, not DATETIME: a close date is a calendar day with no time of day
    expected_close_date DATE          NULL,
    closed_at           DATETIME(6)   NULL,
    account_id          BIGINT        NOT NULL,
    created_by          VARCHAR(255),
    created_date        DATETIME(6)   NOT NULL,
    last_modified_by    VARCHAR(255),
    last_modified_date  DATETIME(6)   NOT NULL,
    deleted_at          DATETIME(6)   NULL,
    PRIMARY KEY (id),
    -- account_id leads, so InnoDB reuses this for the FK instead of adding its own
    INDEX idx_deal_account_stage (account_id, stage),
    CONSTRAINT fk_deal_account FOREIGN KEY (account_id) REFERENCES account (id)
);
