CREATE SEQUENCE exchange_arbitration_history_seq;

CREATE TABLE exchange_arbitration_history (
    id BIGINT PRIMARY KEY DEFAULT nextval('exchange_arbitration_history_seq'),
    timestamp TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    base VARCHAR(64) NOT NULL,
    quote VARCHAR(64) NOT NULL,
    left_exchange VARCHAR(64) NOT NULL,
    right_exchange VARCHAR(64) NOT NULL,
    left_price DECIMAL NOT NULL,
    right_price DECIMAL NOT NULL
);

ALTER SEQUENCE exchange_arbitration_history_seq OWNED BY exchange_arbitration_history.id;