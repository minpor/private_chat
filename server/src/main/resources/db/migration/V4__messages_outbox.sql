CREATE TABLE messages (
    id            UUID PRIMARY KEY,
    chat_id       UUID        NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    sender_id     UUID        NOT NULL REFERENCES users (id),
    client_msg_id UUID        NOT NULL,
    body          TEXT        NOT NULL,
    reply_to      UUID,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,
    CONSTRAINT uq_messages_chat_client_msg UNIQUE (chat_id, client_msg_id)
);

CREATE INDEX idx_messages_chat_created ON messages (chat_id, created_at DESC);
CREATE INDEX idx_messages_sender ON messages (sender_id);

CREATE TABLE outbox (
    id           UUID PRIMARY KEY,
    event_type   VARCHAR(64) NOT NULL,
    payload      JSONB       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;
