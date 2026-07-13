-- Server-side notification inbox.
--
-- A push is fire-and-forget: if the user dismisses the toast, never taps it, has
-- no device token, or their browser refuses to register for push, the app never
-- learns the notification existed and it is lost forever. Persisting every
-- notification we raise means the bell can show it regardless of whether the
-- push was ever delivered — the inbox, not the push, is the source of truth.
CREATE TABLE notifications
(
    id         uuid          NOT NULL,
    user_id    uuid          NOT NULL,
    title      varchar(200)  NOT NULL,
    body       varchar(1000) NOT NULL,
    type       varchar(64),
    -- The FCM data payload (JSON), so the bell can deep-link exactly like a push.
    data       text,
    is_read    boolean       NOT NULL DEFAULT false,
    read_at    timestamp,
    created_at timestamp     NOT NULL DEFAULT now(),
    CONSTRAINT notifications_pk PRIMARY KEY (id),
    CONSTRAINT notifications_users_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- The bell only ever asks for one user's unread notifications, newest first.
CREATE INDEX idx_notifications_user_unread
    ON notifications (user_id, is_read, created_at DESC);
