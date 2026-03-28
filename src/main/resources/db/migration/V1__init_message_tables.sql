-- 消息发送记录
CREATE TABLE message_record (
    id BIGSERIAL PRIMARY KEY,
    biz_id VARCHAR(64) UNIQUE,
    channel VARCHAR(16) NOT NULL,
    recipient VARCHAR(256) NOT NULL,
    subject VARCHAR(256),
    content TEXT,
    template_code VARCHAR(64),
    status VARCHAR(16) DEFAULT 'PENDING',
    retry_count INT DEFAULT 0,
    error_msg TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    sent_at TIMESTAMP
);

CREATE INDEX idx_message_record_biz_id ON message_record(biz_id);
CREATE INDEX idx_message_record_status ON message_record(status);
CREATE INDEX idx_message_record_channel ON message_record(channel);

-- 消息模板
CREATE TABLE message_template (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) UNIQUE NOT NULL,
    channel VARCHAR(16) NOT NULL,
    name VARCHAR(128),
    subject_template VARCHAR(256),
    content_template TEXT NOT NULL,
    vars_schema JSONB,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_message_template_code ON message_template(code);

-- 站内信
CREATE TABLE in_app_message (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(256),
    content TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    biz_type VARCHAR(32),
    biz_id VARCHAR(64),
    created_at TIMESTAMP DEFAULT NOW(),
    read_at TIMESTAMP
);

CREATE INDEX idx_in_app_message_user_id ON in_app_message(user_id);
CREATE INDEX idx_in_app_message_is_read ON in_app_message(user_id, is_read);
