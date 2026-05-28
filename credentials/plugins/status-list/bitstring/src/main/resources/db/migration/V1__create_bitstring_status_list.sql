CREATE TABLE bitstring_status_lists (
    id VARCHAR(255) PRIMARY KEY,
    issuer_did VARCHAR(255) NOT NULL,
    purpose VARCHAR(50) NOT NULL,
    size INT NOT NULL,
    bits_per_entry INT NOT NULL DEFAULT 1,
    encoded_list TEXT NOT NULL,
    status_list_vc TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_bsl_issuer ON bitstring_status_lists(issuer_did);

CREATE TABLE bitstring_credential_indices (
    credential_id VARCHAR(255) NOT NULL,
    status_list_id VARCHAR(255) NOT NULL REFERENCES bitstring_status_lists(id),
    entry_index INT NOT NULL,
    PRIMARY KEY (credential_id, status_list_id)
);

CREATE TABLE bitstring_next_index (
    status_list_id VARCHAR(255) PRIMARY KEY REFERENCES bitstring_status_lists(id),
    next_index INT NOT NULL DEFAULT 0
);
