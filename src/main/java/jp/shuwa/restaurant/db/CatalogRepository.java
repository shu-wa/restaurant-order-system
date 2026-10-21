package jp.shuwa.restaurant.db;

import jp.shuwa.restaurant.domain.MenuSearch;
import jp.shuwa.restaurant.domain.Models.MenuRow;
import jp.shuwa.restaurant.domain.Models.NamedId;
import jp.shuwa.restaurant.domain.Rejected;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class CatalogRepository {
    private final Connections connections;

    public CatalogRepository(Connections connections) { this.connections = connections; }

    public List<NamedId> shops() throws SQLException { return named("SELECT id, name FROM shop ORDER BY id"); }
    public List<NamedId> allergens() throws SQLException { return named("SELECT id, name FROM allergen ORDER BY id"); }
    public List<NamedId> menus() throws SQLException { return named("SELECT id, name FROM menu ORDER BY id"); }

    private List<NamedId> named(String sql) throws SQLException {
        try (var connection = connections.open(); var statement = Database.prepare(connection, sql);
             var rows = statement.executeQuery()) {
            var result = new ArrayList<NamedId>();
            while (rows.next()) result.add(new NamedId(rows.getInt("id"), rows.getString("name")));
            return List.copyOf(result);
        }
    }

    public List<MenuRow> search(MenuSearch criteria) throws SQLException {
        try (var connection = connections.open()) {
            checkAllergens(connection, criteria.excludedAllergens());
            var sql = new StringBuilder("""
                    SELECT m.id, m.name, m.calories, o.price_yen, o.stock, o.limited,
                           COALESCE(s.quantity, 0) AS sold_quantity
                    FROM offering o JOIN menu m ON m.id = o.menu_id
                    LEFT JOIN (SELECT shop_id, menu_id, SUM(quantity) AS quantity
                               FROM order_item GROUP BY shop_id, menu_id) s
                      ON s.shop_id = o.shop_id AND s.menu_id = o.menu_id
                    WHERE o.shop_id = ?
                    """);
            var values = new ArrayList<Integer>();
            values.add(criteria.shopId());
            if (!criteria.excludedAllergens().isEmpty()) {
                sql.append(" AND NOT EXISTS (SELECT 1 FROM menu_allergen a WHERE a.menu_id = m.id AND a.allergen_id IN (")
                        .append(placeholders(criteria.excludedAllergens().size())).append("))");
                values.addAll(criteria.excludedAllergens().stream().sorted().toList());
            }
            if (criteria.maxPrice() != null) {
                sql.append(" AND o.price_yen <= ?");
                values.add(criteria.maxPrice());
            }
            if (criteria.maxCalories() != null) {
                sql.append(" AND m.calories <= ?");
                values.add(criteria.maxCalories());
            }
            if (criteria.limitedOnly()) sql.append(" AND o.limited = TRUE");
            // The ORDER BY fragment comes only from the enum, never from input text.
            sql.append(" ORDER BY ").append(criteria.sort().sql());
            try (var statement = Database.prepare(connection, sql.toString())) {
                for (int i = 0; i < values.size(); i++) statement.setInt(i + 1, values.get(i));
                try (var rows = statement.executeQuery()) {
                    var result = new ArrayList<MenuRow>();
                    while (rows.next()) {
                        result.add(new MenuRow(rows.getInt("id"), rows.getString("name"), rows.getInt("calories"),
                                rows.getInt("price_yen"), rows.getInt("stock"), rows.getBoolean("limited"),
                                rows.getLong("sold_quantity")));
                    }
                    return List.copyOf(result);
                }
            }
        }
    }

    public static void checkAllergens(Connection connection, Set<Integer> ids) throws SQLException {
        if (ids.isEmpty()) return;
        try (var statement = Database.prepare(connection,
                "SELECT COUNT(*) FROM allergen WHERE id IN (" + placeholders(ids.size()) + ")")) {
            int index = 1;
            for (var id : ids) statement.setInt(index++, id);
            try (var rows = statement.executeQuery()) {
                rows.next();
                if (rows.getInt(1) != ids.size()) throw new Rejected("存在しないアレルゲンIDがあります。");
            }
        }
    }

    public static String placeholders(int count) { return String.join(",", Collections.nCopies(count, "?")); }
}
