package jp.shuwa.restaurant.service;

import jp.shuwa.restaurant.db.CatalogRepository;
import jp.shuwa.restaurant.db.Connections;
import jp.shuwa.restaurant.db.Database;
import jp.shuwa.restaurant.domain.Models.SalesRow;
import jp.shuwa.restaurant.domain.Models.Stock;
import jp.shuwa.restaurant.domain.Rejected;
import jp.shuwa.restaurant.domain.Rules;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class AdminService {
    private final Connections connections;
    public AdminService(Connections connections) { this.connections = connections; }

    public Stock stock(int shopId, int menuId) throws SQLException {
        Rules.id(shopId);
        Rules.id(menuId);
        try (var connection = connections.open(); var statement = Database.prepare(connection,
                "SELECT stock, version FROM offering WHERE shop_id = ? AND menu_id = ?")) {
            statement.setInt(1, shopId);
            statement.setInt(2, menuId);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new Rejected("この店舗では提供していない商品です。");
                return new Stock(rows.getInt("stock"), rows.getLong("version"));
            }
        }
    }

    public void setStock(int shopId, int menuId, long expectedVersion, int quantity) throws SQLException {
        Rules.id(shopId);
        Rules.id(menuId);
        Rules.range(quantity, 0, Rules.MAX_STOCK, "在庫");
        if (expectedVersion < 0) throw new IllegalArgumentException("更新バージョンが不正です。");
        try (var connection = connections.open(); var statement = Database.prepare(connection, """
                UPDATE offering SET stock = ?, version = version + 1
                WHERE shop_id = ? AND menu_id = ? AND version = ?
                """)) {
            statement.setInt(1, quantity);
            statement.setInt(2, shopId);
            statement.setInt(3, menuId);
            statement.setLong(4, expectedVersion);
            if (statement.executeUpdate() != 1) throw new Rejected("在庫が更新されています。再確認してください。");
        }
    }

    public int addMenu(String name, int calories, Set<Integer> allergenIds) throws SQLException {
        var safeName = Rules.name(name);
        Rules.range(calories, 0, Rules.MAX_CALORIES, "カロリー");
        var ids = Rules.allergenIds(allergenIds);
        return Database.transaction(connections, connection -> {
            CatalogRepository.checkAllergens(connection, ids);
            int menuId;
            try (var statement = connection.prepareStatement(
                    "INSERT INTO menu (name, calories) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                statement.setQueryTimeout(10);
                statement.setString(1, safeName);
                statement.setInt(2, calories);
                statement.executeUpdate();
                try (var keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Menu ID unavailable");
                    menuId = keys.getInt(1);
                }
            }
            try (var statement = Database.prepare(connection,
                    "INSERT INTO menu_allergen (menu_id, allergen_id) VALUES (?, ?)")) {
                for (var id : ids.stream().sorted().toList()) {
                    statement.setInt(1, menuId);
                    statement.setInt(2, id);
                    statement.addBatch();
                }
                if (!ids.isEmpty()) statement.executeBatch();
            }
            return menuId;
        });
    }

    public void addOffering(int shopId, int menuId, int price, int stock, boolean limited) throws SQLException {
        Rules.id(shopId);
        Rules.id(menuId);
        Rules.range(price, 1, Rules.MAX_PRICE, "価格");
        Rules.range(stock, 0, Rules.MAX_STOCK, "在庫");
        try (var connection = connections.open(); var statement = Database.prepare(connection, """
                INSERT INTO offering (shop_id, menu_id, price_yen, stock, limited) VALUES (?, ?, ?, ?, ?)
                """)) {
            statement.setInt(1, shopId);
            statement.setInt(2, menuId);
            statement.setInt(3, price);
            statement.setInt(4, stock);
            statement.setBoolean(5, limited);
            statement.executeUpdate();
        }
    }

    public List<SalesRow> sales(int shopId) throws SQLException {
        Rules.id(shopId);
        try (var connection = connections.open(); var statement = Database.prepare(connection, """
                SELECT m.id, m.name, COALESCE(s.quantity, 0) AS quantity, COALESCE(s.revenue, 0) AS revenue
                FROM offering o JOIN menu m ON m.id = o.menu_id
                LEFT JOIN (SELECT shop_id, menu_id, SUM(quantity) AS quantity,
                                  SUM(CAST(quantity AS DECIMAL(20,0)) * unit_price_yen) AS revenue
                           FROM order_item GROUP BY shop_id, menu_id) s
                  ON s.shop_id = o.shop_id AND s.menu_id = o.menu_id
                WHERE o.shop_id = ? ORDER BY revenue DESC, m.id
                """)) {
            statement.setInt(1, shopId);
            try (var rows = statement.executeQuery()) {
                var result = new ArrayList<SalesRow>();
                while (rows.next()) result.add(new SalesRow(rows.getInt("id"), rows.getString("name"),
                        rows.getLong("quantity"), rows.getBigDecimal("revenue")));
                return List.copyOf(result);
            }
        }
    }
}
