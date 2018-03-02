ALTER TABLE exchange_arbitration_history
RENAME COLUMN left_volume TO left_volume_base;

ALTER TABLE exchange_arbitration_history
RENAME COLUMN right_volume TO right_volume_base;

ALTER TABLE exchange_arbitration_history
ADD COLUMN left_volume_quote DECIMAL NOT NULL,
ADD COLUMN right_volume_quote DECIMAL NOT NULL;