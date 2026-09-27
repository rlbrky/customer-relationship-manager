-- One row per send attempt. Never soft-deleted and never audited: the row IS the
-- record of what happened, so there is no email_message_aud table and no deleted_at.
CREATE TABLE email_message (
                               id                  BIGINT       NOT NULL AUTO_INCREMENT,
                               version             INT,
    -- a copy of contact.email at send time, not a pointer to it
                               to_address          VARCHAR(254) NOT NULL,
                               subject             VARCHAR(200) NOT NULL,
                               body                TEXT         NOT NULL,
                               status              VARCHAR(20)  NOT NULL,
    -- exactly one of these two is set, depending on status
                               provider_message_id VARCHAR(100) NULL,
                               error_message       VARCHAR(500) NULL,
                               contact_id          BIGINT       NOT NULL,
    -- denormalised from contact.account: every visibility check starts at the account
                               account_id          BIGINT       NOT NULL,
                               created_by          VARCHAR(255),
                               created_date        DATETIME(6)  NOT NULL,
                               last_modified_by    VARCHAR(255),
                               last_modified_date  DATETIME(6)  NOT NULL,
                               PRIMARY KEY (id),
    -- access path: this contact's emails, newest first. Left-to-right, as in V4.
                               INDEX idx_email_message_contact_created (contact_id, created_date),
                               CONSTRAINT fk_email_message_contact FOREIGN KEY (contact_id) REFERENCES contact (id),
                               CONSTRAINT fk_email_message_account FOREIGN KEY (account_id) REFERENCES account (id)
);