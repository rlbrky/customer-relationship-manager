CREATE TABLE role (
                      id   BIGINT      NOT NULL AUTO_INCREMENT,
                      name VARCHAR(50) NOT NULL,
                      PRIMARY KEY (id),
                      CONSTRAINT uk_role_name UNIQUE (name)
);

CREATE TABLE crm_user (
                          id                 BIGINT       NOT NULL AUTO_INCREMENT,
                          version            INT,
                          created_by         VARCHAR(255),
                          created_date       DATETIME(6)  NOT NULL,
                          last_modified_by   VARCHAR(255),
                          last_modified_date DATETIME(6)  NOT NULL,
                          username           VARCHAR(20)  NOT NULL,
                          email              VARCHAR(254) NOT NULL,
                          password_hash      VARCHAR(100) NOT NULL,
                          first_name         VARCHAR(50)  NOT NULL,
                          last_name          VARCHAR(50)  NOT NULL,
                          enabled            BIT(1)       NOT NULL,
                          PRIMARY KEY (id),
                          CONSTRAINT uk_crm_user_username UNIQUE (username),
                          CONSTRAINT uk_crm_user_email    UNIQUE (email)
);

CREATE TABLE user_roles (
                            user_id BIGINT NOT NULL,
                            role_id BIGINT NOT NULL,
                            PRIMARY KEY (user_id, role_id),
                            CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES crm_user (id),
                            CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES role (id)
);