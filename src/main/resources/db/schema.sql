CREATE TABLE shop (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(80) NOT NULL,
    CONSTRAINT shop_name_nonempty CHECK (CHAR_LENGTH(TRIM(name)) > 0)
);
CREATE TABLE menu (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(80) NOT NULL,
    calories INT NOT NULL,
    CONSTRAINT menu_name_nonempty CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT menu_calories_valid CHECK (calories BETWEEN 0 AND 100000)
);
CREATE TABLE allergen (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(80) NOT NULL UNIQUE
);
CREATE TABLE menu_allergen (
    menu_id INT NOT NULL,
    allergen_id INT NOT NULL,
    PRIMARY KEY (menu_id, allergen_id),
    FOREIGN KEY (menu_id) REFERENCES menu(id),
    FOREIGN KEY (allergen_id) REFERENCES allergen(id)
);
CREATE TABLE offering (
    shop_id INT NOT NULL,
    menu_id INT NOT NULL,
    price_yen INT NOT NULL,
    stock INT NOT NULL,
    limited BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (shop_id, menu_id),
    FOREIGN KEY (shop_id) REFERENCES shop(id),
    FOREIGN KEY (menu_id) REFERENCES menu(id),
    CONSTRAINT offering_price_valid CHECK (price_yen BETWEEN 1 AND 1000000),
    CONSTRAINT offering_stock_valid CHECK (stock BETWEEN 0 AND 1000000),
    CONSTRAINT offering_version_valid CHECK (version >= 0)
);
CREATE TABLE customer_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    shop_id INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE (id, shop_id),
    FOREIGN KEY (shop_id) REFERENCES shop(id)
);
CREATE TABLE order_item (
    order_id BIGINT NOT NULL,
    shop_id INT NOT NULL,
    menu_id INT NOT NULL,
    menu_name VARCHAR(80) NOT NULL,
    quantity INT NOT NULL,
    unit_price_yen INT NOT NULL,
    PRIMARY KEY (order_id, menu_id),
    FOREIGN KEY (order_id, shop_id) REFERENCES customer_order(id, shop_id),
    FOREIGN KEY (shop_id, menu_id) REFERENCES offering(shop_id, menu_id),
    CONSTRAINT item_quantity_valid CHECK (quantity BETWEEN 1 AND 100),
    CONSTRAINT item_price_valid CHECK (unit_price_yen BETWEEN 1 AND 1000000)
);
CREATE INDEX order_item_shop_menu ON order_item(shop_id, menu_id);
CREATE INDEX menu_allergen_reverse ON menu_allergen(allergen_id, menu_id);
