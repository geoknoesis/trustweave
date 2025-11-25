-- DIDComm Messages Storage Schema
-- PostgreSQL migration script

-- Main messages table
CREATE TABLE IF NOT EXISTS didcomm_messages (
    id VARCHAR(255) PRIMARY KEY,
    type VARCHAR(255) NOT NULL,
    from_did VARCHAR(255),
    to_dids VARCHAR(255)[],
    body JSONB,
    created_time VARCHAR(255),
    expires_time VARCHAR(255),
    thid VARCHAR(255),
    pthid VARCHAR(255),
    message_json JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index table for DID lookups
CREATE TABLE IF NOT EXISTS didcomm_message_dids (
    message_id VARCHAR(255) REFERENCES didcomm_messages(id) ON DELETE CASCADE,
    did VARCHAR(255) NOT NULL,
    role VARCHAR(10) NOT NULL CHECK (role IN ('from', 'to')),
    PRIMARY KEY (message_id, did, role)
);

-- Index table for thread lookups
CREATE TABLE IF NOT EXISTS didcomm_message_threads (
    message_id VARCHAR(255) REFERENCES didcomm_messages(id) ON DELETE CASCADE,
    thid VARCHAR(255) NOT NULL,
    PRIMARY KEY (message_id, thid)
);

-- Performance indexes
CREATE INDEX IF NOT EXISTS idx_messages_from_did ON didcomm_messages(from_did);
CREATE INDEX IF NOT EXISTS idx_messages_thid ON didcomm_messages(thid);
CREATE INDEX IF NOT EXISTS idx_messages_created ON didcomm_messages(created_time);
CREATE INDEX IF NOT EXISTS idx_messages_type ON didcomm_messages(type);
CREATE INDEX IF NOT EXISTS idx_message_dids_did ON didcomm_message_dids(did);
CREATE INDEX IF NOT EXISTS idx_message_threads_thid ON didcomm_message_threads(thid);

