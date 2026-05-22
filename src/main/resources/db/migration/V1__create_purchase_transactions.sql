CREATE TABLE purchase_transactions (
    id               UUID           NOT NULL,
    description      VARCHAR(50)    NOT NULL,
    transaction_date DATE           NOT NULL,
    purchase_amount  NUMERIC(19, 2) NOT NULL,
    CONSTRAINT pk_purchase_transactions PRIMARY KEY (id)
);
