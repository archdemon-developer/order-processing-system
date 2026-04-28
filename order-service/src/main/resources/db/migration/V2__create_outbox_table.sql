CREATE TABLE outbox_events (
                               id              UUID        PRIMARY KEY,
                               aggregatetype   VARCHAR     NOT NULL,
                               aggregateid     VARCHAR     NOT NULL,
                               type            VARCHAR     NOT NULL,
                               payload         JSONB       NOT NULL,
                               createdat       TIMESTAMPTZ NOT NULL DEFAULT now()
);