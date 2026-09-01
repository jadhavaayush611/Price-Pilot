-- Flyway migration to create tables for persistent AI shopping assistant conversations and grounded messages
-- Author: Principal Shopping Assistant & Decision Support Engineer

CREATE TABLE IF NOT EXISTS assistant_conversations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_assistant_conversations_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_assistant_conv_user_updated ON assistant_conversations(user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS assistant_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    role VARCHAR(30) NOT NULL,
    content TEXT NOT NULL,
    intent VARCHAR(50),
    evidence_bundle TEXT,
    payload TEXT,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_assistant_messages_conv FOREIGN KEY (conversation_id) REFERENCES assistant_conversations(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_assistant_msg_conv_created ON assistant_messages(conversation_id, created_at ASC);
