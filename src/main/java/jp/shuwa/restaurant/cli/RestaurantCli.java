package jp.shuwa.restaurant.cli;

import jp.shuwa.restaurant.db.CatalogRepository;
import jp.shuwa.restaurant.domain.MenuSearch;
import jp.shuwa.restaurant.domain.MenuSearch.Sort;
import jp.shuwa.restaurant.domain.Models.MenuRow;
import jp.shuwa.restaurant.domain.Models.NamedId;
import jp.shuwa.restaurant.domain.Rejected;
import jp.shuwa.restaurant.domain.Rules;
import jp.shuwa.restaurant.service.AdminService;
import jp.shuwa.restaurant.service.OrderService;

import java.io.EOFException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

public final class RestaurantCli {
    private final CatalogRepository catalog;
    private final OrderService orders;
    private final AdminService admin;
    private final ConsoleInput input;
    private final PrintWriter out;

    public RestaurantCli(CatalogRepository catalog, OrderService orders, AdminService admin, Reader reader, PrintWriter out) {
        this.catalog = catalog;
        this.orders = orders;
        this.admin = admin;
        this.input = new ConsoleInput(reader, out);
        this.out = out;
    }

    public void run(boolean administrative) throws IOException {
        out.println(administrative ? "飲食店管理システム" : "飲食店注文システム");
        while (true) {
            try {
                if (administrative ? !adminAction() : !customerAction()) return;
            } catch (EOFException end) {
                out.println("\n終了しました。");
                return;
            } catch (IllegalArgumentException | Rejected error) {
                out.println("操作を中止しました: " + error.getMessage());
            } catch (SQLException error) {
                var state = error.getSQLState();
                if (state != null && state.startsWith("23")) {
                    out.println("登録済みのID、または存在しない関連IDです。変更は確定していません。");
                } else {
                    out.println("DB操作を完了できませんでした。接続・DBの状態を確認してください。");
                    return;
                }
            } finally {
                out.flush();
            }
        }
    }

    private int shop() throws SQLException, IOException {
        return select("店舗ID (0:終了)", catalog.shops(), true);
    }

    private int select(String label, List<NamedId> options, boolean allowExit) throws IOException {
        options.forEach(item -> out.println(item.id() + " | " + item.name()));
        if (options.isEmpty()) {
            if (allowExit) { out.println("登録データがありません。"); return 0; }
            throw new Rejected("登録データがありません。");
        }
        while (true) {
            int id = input.number(label, allowExit ? 0 : 1, Integer.MAX_VALUE);
            if ((allowExit && id == 0) || options.stream().anyMatch(item -> item.id() == id)) return id;
            out.println("一覧のIDを指定してください。");
        }
    }

    private boolean customerAction() throws SQLException, IOException {
        int shopId = shop();
        if (shopId == 0) return false;
        catalog.allergens().forEach(item -> out.println(item.id() + " | " + item.name()));
        var excluded = ConsoleInput.ids(input.line("除外するアレルゲンID (空白区切り / Enter:指定なし)"));
        var price = input.optionalNumber("価格上限・円 (Enter:指定なし)", Rules.MAX_PRICE);
        var calories = input.optionalNumber("カロリー上限・kcal (Enter:指定なし)", Rules.MAX_CALORIES);
        boolean limited = input.number("1:すべて / 2:限定のみ", 1, 2) == 2;
        int sort = input.number("1:ID順 / 2:価格順 / 3:カロリー順 / 4:販売数順", 1, 4);
        var criteria = new MenuSearch(shopId, excluded, price, calories, limited, Sort.values()[sort - 1]);
        var rows = catalog.search(criteria);
        printMenu(rows);
        if (rows.isEmpty()) return true;
        var cart = ConsoleInput.cart(input.line("注文 ID:数量 (例: 1:2 3:1 / Enter:戻る)"));
        if (cart.isEmpty()) return true;
        if (cart.keySet().stream().anyMatch(id -> rows.stream().noneMatch(row -> row.id() == id))) {
            throw new Rejected("表示した検索結果の中から商品を選んでください。");
        }
        if (input.number("注文を確定しますか (1:確定 / 0:取消)", 0, 1) == 0) return true;
        var receipt = orders.place(shopId, cart, excluded);
        out.println("注文番号: " + receipt.orderId());
        receipt.lines().forEach(line -> out.println(line.name() + " | " + line.quantity() + "個 | " + line.subtotal() + "円"));
        out.println("合計: " + receipt.total() + "円");
        return true;
    }

    private boolean adminAction() throws IOException, SQLException {
        int action = input.number("0:終了 / 1:在庫一覧 / 2:在庫更新 / 3:売上 / 4:メニュー追加 / 5:店舗へ商品登録", 0, 5);
        if (action == 0) return false;
        if (action == 4) {
            var name = input.line("メニュー名");
            int calories = input.number("カロリー・kcal", 0, Rules.MAX_CALORIES);
            catalog.allergens().forEach(item -> out.println(item.id() + " | " + item.name()));
            var ids = ConsoleInput.ids(input.line("含むアレルゲンID (空白区切り / Enter:なし)"));
            int id = admin.addMenu(name, calories, ids);
            out.println("メニューを追加しました。ID: " + id);
            return true;
        }
        int shopId = shop();
        if (shopId == 0) return true;
        switch (action) {
            case 1 -> printMenu(catalog.search(new MenuSearch(shopId, Set.of(), null, null, false, Sort.ID)));
            case 2 -> {
                int menuId = input.number("メニューID", 1, Integer.MAX_VALUE);
                var previous = admin.stock(shopId, menuId);
                out.println("現在の在庫: " + previous.quantity());
                int quantity = input.number("更新後の在庫数", 0, Rules.MAX_STOCK);
                admin.setStock(shopId, menuId, previous.version(), quantity);
                out.println("在庫を更新しました。");
            }
            case 3 -> {
                var sales = admin.sales(shopId);
                out.println("ID | 商品名 | 注文数 | 売上(円)");
                sales.forEach(row -> out.println(row.menuId() + " | " + row.name() + " | " + row.quantity() + " | " + row.revenue().toPlainString()));
                var total = sales.stream().map(row -> row.revenue()).reduce(BigDecimal.ZERO, BigDecimal::add);
                out.println("総売上: " + total.toPlainString() + "円");
            }
            case 5 -> {
                int menuId = select("追加するメニューID", catalog.menus(), false);
                int price = input.number("価格・円", 1, Rules.MAX_PRICE);
                int stock = input.number("在庫数", 0, Rules.MAX_STOCK);
                boolean limited = input.number("1:通常 / 2:限定", 1, 2) == 2;
                admin.addOffering(shopId, menuId, price, stock, limited);
                out.println("店舗に商品を登録しました。");
            }
            default -> throw new IllegalStateException("Unsupported action");
        }
        return true;
    }

    private void printMenu(List<MenuRow> rows) {
        if (rows.isEmpty()) { out.println("該当するメニューはありません。"); return; }
        out.println("ID | 商品名 | kcal | 価格(円) | 在庫 | 区分 | 販売数");
        rows.forEach(row -> out.println(row.id() + " | " + row.name() + " | " + row.calories() + " | "
                + row.price() + " | " + row.stock() + " | " + (row.limited() ? "限定" : "通常") + " | " + row.soldQuantity()));
    }
}
