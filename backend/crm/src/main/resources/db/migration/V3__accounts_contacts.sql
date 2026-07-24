CREATE TABLE account (
                         id                 BIGINT       NOT NULL AUTO_INCREMENT,
                         version            INT,
                         name               VARCHAR(150) NOT NULL,
                         industry           VARCHAR(100),
                         website            VARCHAR(254),
                         phone              VARCHAR(20),
                         owner_id           BIGINT       NOT NULL,
                         created_by         VARCHAR(255),
                         created_date       DATETIME(6)  NOT NULL,
                         last_modified_by   VARCHAR(255),
                         last_modified_date DATETIME(6)  NOT NULL,
                         deleted_at         DATETIME(6)  NULL,
                         PRIMARY KEY (id),
                         CONSTRAINT fk_account_owner FOREIGN KEY (owner_id) REFERENCES crm_user (id)
);

CREATE TABLE contact (
                         id                 BIGINT       NOT NULL AUTO_INCREMENT,
                         version            INT,
                         first_name         VARCHAR(50) NOT NULL,
                         last_name          VARCHAR(50) NOT NULL,
                         email              VARCHAR(254),
                         phone              VARCHAR(20),
                         job_title          VARCHAR(50),
                         account_id         BIGINT      NOT NULL,
                         created_by         VARCHAR(255),
                         created_date       DATETIME(6)  NOT NULL,
                         last_modified_by   VARCHAR(255),
                         last_modified_date DATETIME(6)  NOT NULL,
                         deleted_at         DATETIME(6)  NULL,
                         PRIMARY KEY (id),
                         CONSTRAINT fk_contact_account FOREIGN KEY (account_id) REFERENCES account (id)
);