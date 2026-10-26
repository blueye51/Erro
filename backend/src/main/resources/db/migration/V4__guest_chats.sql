CREATE SCHEMA IF NOT EXISTS erro_chat;

CREATE TABLE erro_chat.guest (
    id uuid PRIMARY KEY,
    token_hash varchar(64) NOT NULL UNIQUE,
    created_at timestamptz NOT NULL DEFAULT now(),
    last_seen_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL
);
CREATE INDEX guest_expiry ON erro_chat.guest(expires_at);
CREATE TABLE erro_chat.conversation (
    id uuid PRIMARY KEY,
    guest_id uuid NOT NULL REFERENCES erro_chat.guest(id) ON DELETE CASCADE,
    title varchar(100) NOT NULL DEFAULT 'New chat',
    context_start bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX conversation_guest ON erro_chat.conversation(guest_id,updated_at DESC,id);
CREATE TABLE erro_chat.message (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id uuid NOT NULL REFERENCES erro_chat.conversation(id) ON DELETE CASCADE,
    role varchar(10) NOT NULL CHECK (role IN ('user','assistant')),
    content text NOT NULL,
    answer jsonb,
    filenames jsonb NOT NULL DEFAULT '[]',
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX message_conversation ON erro_chat.message(conversation_id,id DESC);
CREATE INDEX message_user_search ON erro_chat.message USING gin(to_tsvector('simple',content)) WHERE role='user';
CREATE TABLE erro_chat.chat_request (
    id uuid NOT NULL,
    conversation_id uuid NOT NULL REFERENCES erro_chat.conversation(id) ON DELETE CASCADE,
    message text NOT NULL CHECK (length(message) BETWEEN 1 AND 8000),
    attachment_ids jsonb NOT NULL DEFAULT '[]',
    status varchar(12) NOT NULL CHECK (status IN ('QUEUED','PROCESSING','COMPLETE','FAILED')),
    lease uuid,
    lease_until timestamptz,
    error varchar(300) NOT NULL DEFAULT '',
    assistant_message_id bigint REFERENCES erro_chat.message(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    finished_at timestamptz,
    PRIMARY KEY (conversation_id,id)
);
CREATE UNIQUE INDEX one_active_chat_request ON erro_chat.chat_request(conversation_id)
    WHERE status IN ('QUEUED','PROCESSING');
CREATE INDEX chat_request_queue ON erro_chat.chat_request(created_at) WHERE status='QUEUED';
CREATE TABLE erro_chat.attachment (
    id uuid PRIMARY KEY,
    conversation_id uuid NOT NULL REFERENCES erro_chat.conversation(id) ON DELETE CASCADE,
    filename varchar(160) NOT NULL,
    file_bytes bigint NOT NULL CHECK (file_bytes BETWEEN 1 AND 20971520),
    sha256 varchar(64) NOT NULL,
    status varchar(12) NOT NULL CHECK (status IN ('QUEUED','PROCESSING','READY','ERROR')),
    progress varchar(300) NOT NULL DEFAULT 'Waiting to process',
    error varchar(300) NOT NULL DEFAULT '',
    original bytea,
    extraction jsonb,
    lease uuid,
    lease_until timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(conversation_id,sha256)
);
CREATE INDEX attachment_conversation ON erro_chat.attachment(conversation_id,created_at);
CREATE INDEX attachment_queue ON erro_chat.attachment(created_at) WHERE status='QUEUED';
