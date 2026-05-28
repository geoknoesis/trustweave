CREATE TABLE token_status_lists (
    id VARCHAR(255) PRIMARY KEY,
    issuer_did VARCHAR(255) NOT NULL,
    purpose VARCHAR(50) NOT NULL,
    size INT NOT NULL,
    bits_per_entry INT NOT NULL DEFAULT 1,
    status_array BYTEA NOT NULL,
    status_list_token TEXT,
    status_list_uri VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE token_credential_indices (
    credential_id VARCHAR(255) NOT NULL,
    status_list_id VARCHAR(255) NOT NULL REFERENCES token_status_lists(id),
    entry_index INT NOT NULL,
    PRIMARY KEY (credential_id, status_list_id)
);

CREATE TABLE token_next_index (
    status_list_id VARCHAR(255) PRIMARY KEY REFERENCES token_status_lists(id),
    next_index INT NOT NULL DEFAULT 0
);
