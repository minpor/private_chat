CREATE TYPE chat_type AS ENUM ('direct', 'group');

CREATE TABLE chats (
    id         UUID PRIMARY KEY,
    type       chat_type   NOT NULL,
    title      VARCHAR(255),
    created_by UUID        NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE chat_members (
    chat_id   UUID        NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    user_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (chat_id, user_id)
);

CREATE INDEX idx_chat_members_user_id ON chat_members (user_id);
