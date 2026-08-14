CREATE TABLE activity (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    version            INT,
    type               VARCHAR(20)  NOT NULL,
    subject            VARCHAR(200) NOT NULL,
    notes              TEXT,
    occurred_at        DATETIME(6)  NOT NULL,
    due_at             DATETIME(6)  NULL,
    completed          BIT(1)       NOT NULL DEFAULT 0,
    account_id         BIGINT       NOT NULL,
    contact_id         BIGINT       NULL,
    created_by         VARCHAR(255),
    created_date       DATETIME(6)  NOT NULL,
    last_modified_by   VARCHAR(255),
    last_modified_date DATETIME(6)  NOT NULL,
    deleted_at         DATETIME(6)  NULL,
    PRIMARY KEY (id),
    -- timeline access path: filter by account, then order by date.
    -- Column order matters: MySQL reads a composite index left-to-right.
    INDEX idx_activity_account_occurred (account_id, occurred_at),
    CONSTRAINT fk_activity_account FOREIGN KEY (account_id) REFERENCES account (id),
    CONSTRAINT fk_activity_contact FOREIGN KEY (contact_id) REFERENCES contact (id)
);
