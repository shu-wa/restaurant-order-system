package jp.shuwa.restaurant.service;

import jp.shuwa.restaurant.db.CatalogRepository;
import jp.shuwa.restaurant.db.Connections;
import jp.shuwa.restaurant.db.Database;
import jp.shuwa.restaurant.domain.Models.OrderLine;
import jp.shuwa.restaurant.domain.Models.Receipt;
import jp.shuwa.restaurant.domain.Rejected;
import jp.shuwa.restaurant.domain.Rules;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class OrderService {
    private final Connections connections;
    public OrderService(Connections connections) { this.connections = connections; }

    public Receipt place(int shopId, Map<Integer, Integer> cart, Set<Integer> excludedAllergens) throws SQLException {
        Rules.id(shopId);
        if (cart == null || cart.isEmpty() || cart.size() > Rules.MAX_ITEMS) {
            throw new IllegalArgumentException("注文は1～50種類の商品で指定してください。");
        }
        var items = new TreeMap<Integer, Integer>();
        cart.forEach((id, quantity) -> {
            Rules.id(id);
            Rules.range(quantity, 1, Rules.MAX_QUANTITY, "数量");
            items.put(id, quantity);
        });
        var excluded = Rules.allergenIds(excludedAllergens);
        return Database.transaction(connections, connection -> {
            CatalogRepository.checkAllergens(connection, excluded);
            var lines = new ArrayList<OrderLine>();
            // Always lock in menu-ID order, including multi-item orders.
            for (var item : items.entrySet()) {
                lines.add(lockAndCheck(connection, shopId, item.getKey(), item.getValue(), excluded));
            }
            long orderId;
            try (var statement = connection.prepareStatement(
                    "INSERT INTO customer_order (shop_id) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
                statement.setQueryTimeout(10);
                statement.setInt(1, shopId);
                statement.executeUpdate();
                try (var keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Order ID unavailable");
                    orderId = keys.getLong(1);
                }
            }
            for (var line : lines) {
                try (var update = Database.prepare(connection, """
                        UPDATE offering SET stock = stock - ?, version = version + 1
                        WHERE shop_id = ? AND menu_id = ? AND stock >= ?
                        """)) {
                    update.setInt(1, line.quantity());
                    update.setInt(2, shopId);
                    update.setInt(3, line.menuId());
                    update.setInt(4, line.quantity());
                    if (update.executeUpdate() != 1) throw new Rejected("在庫が変わりました。再検索してください。");
                }
                try (var insert = Database.prepare(connection, """
                        INSERT INTO order_item (order_id, shop_id, menu_id, menu_name, quantity, unit_price_yen)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)) {
                    insert.setLong(1, orderId);
                    insert.setInt(2, shopId);
                    insert.setInt(3, line.menuId());
                    insert.setString(4, line.name());
                    insert.setInt(5, line.quantity());
                    insert.setInt(6, line.unitPrice());
                    insert.executeUpdate();
                }
            }
            return new Receipt(orderId, lines);
        });
    }

    private OrderLine lockAndCheck(Connection connection, int shop, int menu, int quantity,
                                   Set<Integer> excluded) throws SQLException {
        try (var statement = Database.prepare(connection, """
                SELECT o.stock, o.price_yen, m.name FROM offering o JOIN menu m ON m.id = o.menu_id
                WHERE o.shop_id = ? AND o.menu_id = ? FOR UPDATE
                """)) {
            statement.setInt(1, shop);
            statement.setInt(2, menu);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new Rejected("この店舗では提供していない商品です。ID: " + menu);
                if (rows.getInt("stock") < quantity) throw new Rejected("在庫が足りません。ID: " + menu);
                if (!excluded.isEmpty()) {
                    try (var check = Database.prepare(connection,
                            "SELECT 1 FROM menu_allergen WHERE menu_id = ? AND allergen_id IN ("
                                    + CatalogRepository.placeholders(excluded.size()) + ")")) {
                        check.setInt(1, menu);
                        int index = 2;
                        for (var id : excluded) check.setInt(index++, id);
                        try (var matches = check.executeQuery()) {
                            if (matches.next()) throw new Rejected("除外指定のアレルゲンを含むため注文できません。ID: " + menu);
                        }
                    }
                }
                return new OrderLine(menu, rows.getString("name"), quantity, rows.getInt("price_yen"));
            }
        }
    }
}
