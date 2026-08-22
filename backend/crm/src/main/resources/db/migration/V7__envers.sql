-- Hibernate Envers audit tables.
--
-- Derived from the exported entity mappings rather than written from memory:
-- ddl-auto=validate means any mismatch stops the application at startup.
-- Column widths mirror the base tables so an audit row can always hold whatever
-- the live row held.
--
-- Note what is ABSENT from every _AUD table: the BaseEntity columns (version,
-- created_by/date, last_modified_by/date). Envers does not audit properties of an
-- un-annotated @MappedSuperclass, which is the right outcome — rev, revtstmp and
-- username already record who changed it and when, more precisely.
--
-- Table names are lower case because Spring Boot applies
-- CamelCaseToUnderscoresNamingStrategy as the PHYSICAL naming strategy, which
-- lowercases Envers' default _AUD suffix. A raw Hibernate schema export does not
-- apply it and will hand you account_AUD; on a case-sensitive MySQL that is a
-- different table and validate fails at startup.
--
-- Nothing here is NOT NULL except the key columns: an audit row records a state,
-- and a state that violated a constraint is still a state worth keeping.

CREATE TABLE revinfo (
    rev      INT    NOT NULL AUTO_INCREMENT,
    revtstmp BIGINT,
    username VARCHAR(255),
    PRIMARY KEY (rev)
);

CREATE TABLE crm_user_aud (
    id         BIGINT NOT NULL,
    rev        INT    NOT NULL,
    revtype    TINYINT,
    username   VARCHAR(20),
    email      VARCHAR(254),
    first_name VARCHAR(50),
    last_name  VARCHAR(50),
    enabled    BIT(1),
    -- password_hash is deliberately absent (@NotAudited): an audit table is a
    -- second copy of the data with looser access and no retention limit, and every
    -- hash the user ever rotated away from would accumulate here forever.
    PRIMARY KEY (rev, id),
    CONSTRAINT fk_crm_user_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev)
);

-- Role itself is not audited (a static lookup), but WHICH roles a user held at
-- each revision is recorded here — the most compliance-relevant fact in the app.
CREATE TABLE user_roles_aud (
    rev     INT    NOT NULL,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    revtype TINYINT,
    PRIMARY KEY (rev, user_id, role_id),
    CONSTRAINT fk_user_roles_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev)
);

CREATE TABLE account_aud (
    id         BIGINT NOT NULL,
    rev        INT    NOT NULL,
    revtype    TINYINT,
    name       VARCHAR(150),
    industry   VARCHAR(100),
    website    VARCHAR(254),
    phone      VARCHAR(20),
    owner_id   BIGINT,
    deleted_at DATETIME(6),
    PRIMARY KEY (rev, id),
    CONSTRAINT fk_account_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev)
);

CREATE TABLE contact_aud (
    id         BIGINT NOT NULL,
    rev        INT    NOT NULL,
    revtype    TINYINT,
    first_name VARCHAR(50),
    last_name  VARCHAR(50),
    email      VARCHAR(254),
    phone      VARCHAR(20),
    job_title  VARCHAR(50),
    account_id BIGINT,
    deleted_at DATETIME(6),
    PRIMARY KEY (rev, id),
    CONSTRAINT fk_contact_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev)
);

CREATE TABLE activity_aud (
    id          BIGINT NOT NULL,
    rev         INT    NOT NULL,
    revtype     TINYINT,
    type        VARCHAR(20),
    subject     VARCHAR(200),
    notes       TEXT,
    occurred_at DATETIME(6),
    due_at      DATETIME(6),
    completed   BIT(1),
    account_id  BIGINT,
    contact_id  BIGINT,
    deleted_at  DATETIME(6),
    PRIMARY KEY (rev, id),
    CONSTRAINT fk_activity_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev)
);

CREATE TABLE deal_aud (
    id                  BIGINT NOT NULL,
    rev                 INT    NOT NULL,
    revtype             TINYINT,
    title               VARCHAR(200),
    value               DECIMAL(19, 2),
    stage               VARCHAR(20),
    outcome             VARCHAR(10),
    expected_close_date DATE,
    closed_at           DATETIME(6),
    account_id          BIGINT,
    deleted_at          DATETIME(6),
    PRIMARY KEY (rev, id),
    CONSTRAINT fk_deal_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev)
);
