CREATE TABLE url_mapping (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    original_url VARCHAR(2048) NOT NULL,
    -- utf8mb4_bin = case-sensitive, so "aB" and "Ab" are different codes
    short_code   VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    expires_at   DATETIME(6)   NULL,
    click_count  BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_url_mapping_short_code UNIQUE (short_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;