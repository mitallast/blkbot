ALTER TABLE exchange_arbitration_history

ADD COLUMN left_volume DECIMAL NOT NULL,
ADD COLUMN right_volume DECIMAL NOT NULL,

ADD COLUMN left_bid DECIMAL NOT NULL,
ADD COLUMN right_bid DECIMAL NOT NULL,

ADD COLUMN left_ask DECIMAL NOT NULL,
ADD COLUMN right_ask DECIMAL NOT NULL;