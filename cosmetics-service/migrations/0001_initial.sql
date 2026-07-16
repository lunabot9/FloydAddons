CREATE TABLE appearances (
    uuid TEXT PRIMARY KEY,
    appearance_json TEXT NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE auth_challenges (
    id TEXT PRIMARY KEY,
    uuid TEXT NOT NULL UNIQUE,
    username TEXT NOT NULL,
    server_id TEXT NOT NULL,
    expires_at INTEGER NOT NULL
);

CREATE INDEX auth_challenges_expires_at ON auth_challenges(expires_at);

CREATE TABLE sessions (
    token_hash TEXT PRIMARY KEY,
    uuid TEXT NOT NULL,
    expires_at INTEGER NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE INDEX sessions_uuid ON sessions(uuid);
CREATE INDEX sessions_expires_at ON sessions(expires_at);
