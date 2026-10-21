package jp.shuwa.restaurant;

import jp.shuwa.restaurant.cli.RestaurantCli;
import jp.shuwa.restaurant.db.CatalogRepository;
import jp.shuwa.restaurant.db.Connections;
import jp.shuwa.restaurant.db.Database;
import jp.shuwa.restaurant.db.DbConfig;
import jp.shuwa.restaurant.domain.MenuSearch;
import jp.shuwa.restaurant.domain.MenuSearch.Sort;
import jp.shuwa.restaurant.domain.Rejected;
import jp.shuwa.restaurant.service.AdminService;
import jp.shuwa.restaurant.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RestaurantTest {
    private Connections connections;
    private CatalogRepository catalog;
    private OrderService orders;
    private AdminService admin;

    @BeforeEach
    void setUp() throws Exception {
        if ("true".equals(System.getenv("RESTAURANT_TEST_MYSQL"))) {
            var env = System.getenv();
            if (!"restaurant_test".equals(env.get("RESTAURANT_DB_NAME"))
                    || !Set.of("localhost", "127.0.0.1").contains(env.get("RESTAURANT_DB_HOST"))) {
                throw new IllegalStateException("MySQL tests require an isolated loopback restaurant_test database");
            }
            connections = DbConfig.fromEnvironment(env);
            try (var connection = connections.open(); var statement = connection.createStatement()) {
                for (var table : List.of("order_item", "customer_order", "offering", "menu_allergen", "allergen", "menu", "shop")) {
                    statement.execute("DROP TABLE IF EXISTS " + table);
                }
            }
        } else {
            connections = DbConfig.demo();
        }
        try (var connection = connections.open()) {
            Database.script(connection, "/db/schema.sql");
            Database.script(connection, "/db/demo.sql");
        }
        catalog = new CatalogRepository(connections);
        orders = new OrderService(connections);
        admin = new AdminService(connections);
    }

    private MenuSearch search(Set<Integer> excluded, Integer price, Integer calories, boolean limited, Sort sort) {
        return new MenuSearch(1, excluded, price, calories, limited, sort);
    }

    private long scalar(String sql) throws SQLException {
        try (var connection = connections.open(); var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    @Test void noAllergenSelectionReturnsAllRowsIncludingFirst() throws Exception {
        var rows = catalog.search(search(Set.of(), null, null, false, Sort.ID));
        assertEquals(List.of(1,2,3,4,5,6), rows.stream().map(row -> row.id()).toList());
    }

    @Test void singleMatchingRowIsNotSkipped() throws Exception {
        var rows = catalog.search(search(Set.of(), 300, 120, false, Sort.ID));
        assertEquals(1, rows.size());
        assertEquals(2, rows.getFirst().id());
    }

    @Test void multipleAllergensExcludeAnyMatch() throws Exception {
        var rows = catalog.search(search(Set.of(2,3), null, null, false, Sort.ID));
        assertEquals(List.of(2), rows.stream().map(row -> row.id()).toList());
    }

    @Test void nonexistentAllergenIsRejected() {
        assertThrows(Rejected.class, () -> catalog.search(search(Set.of(999), null, null, false, Sort.ID)));
    }

    @Test void allSearchModesHaveStableOrdering() throws Exception {
        assertEquals(List.of(6,4,2,1,3,5), catalog.search(search(Set.of(), null, null, false, Sort.PRICE)).stream().map(row -> row.id()).toList());
        assertEquals(List.of(2,4,6,1,5,3), catalog.search(search(Set.of(), null, null, false, Sort.CALORIES)).stream().map(row -> row.id()).toList());
        assertEquals(List.of(4), catalog.search(search(Set.of(), null, null, true, Sort.ID)).stream().map(row -> row.id()).toList());
        orders.place(1, Map.of(3,2), Set.of());
        assertEquals(3, catalog.search(search(Set.of(), null, null, false, Sort.POPULARITY)).getFirst().id());
    }

    @Test void storeSpecificAvailabilityAndPrice() throws Exception {
        var rows = catalog.search(new MenuSearch(2, Set.of(), null, null, false, Sort.ID));
        assertEquals(3, rows.size());
        assertEquals(680, rows.getFirst().price());
    }

    @Test void noMatchesReturnsEmptyList() throws Exception {
        assertTrue(catalog.search(search(Set.of(), 0, 0, false, Sort.ID)).isEmpty());
    }

    @Test void criteriaIsImmutable() {
        var ids = new HashSet<>(Set.of(1));
        var criteria = search(ids, null, null, false, Sort.ID);
        ids.add(2);
        assertEquals(Set.of(1), criteria.excludedAllergens());
        assertThrows(UnsupportedOperationException.class, () -> criteria.excludedAllergens().add(3));
    }

    @Test void multiItemOrderCommitsStockAndLedger() throws Exception {
        var receipt = orders.place(1, Map.of(1,2,2,1), Set.of());
        assertTrue(receipt.orderId() > 0);
        assertEquals(1600L, receipt.total());
        assertEquals(8, admin.stock(1,1).quantity());
        assertEquals(14, admin.stock(1,2).quantity());
        assertEquals(1, scalar("SELECT COUNT(*) FROM customer_order"));
        assertEquals(2, scalar("SELECT COUNT(*) FROM order_item"));
        assertEquals(2, scalar("SELECT quantity FROM order_item WHERE menu_id = 1"));
    }

    @Test void insufficientStockRejectsEntireCart() throws Exception {
        assertThrows(Rejected.class, () -> orders.place(1, Map.of(1,2,5,1), Set.of()));
        assertEquals(10, admin.stock(1,1).quantity());
        assertEquals(0, scalar("SELECT COUNT(*) FROM customer_order"));
    }

    @Test void directIdCannotBypassAllergenExclusion() throws Exception {
        assertThrows(Rejected.class, () -> orders.place(1, Map.of(1,1), Set.of(3)));
        assertEquals(10, admin.stock(1,1).quantity());
        assertEquals(0, scalar("SELECT COUNT(*) FROM customer_order"));
    }

    @Test void nonexistentOfferingDoesNotCommit() throws Exception {
        assertThrows(Rejected.class, () -> orders.place(2, Map.of(3,1), Set.of()));
        assertEquals(0, scalar("SELECT COUNT(*) FROM customer_order"));
    }

    @Test void zeroNegativeAndExcessiveQuantitiesAreRejected() throws Exception {
        for (int quantity : List.of(0,-1,101,Integer.MAX_VALUE)) {
            assertThrows(IllegalArgumentException.class, () -> orders.place(1, Map.of(1,quantity), Set.of()));
        }
        assertThrows(IllegalArgumentException.class, () -> orders.place(1, Map.of(), Set.of()));
        assertEquals(0, scalar("SELECT COUNT(*) FROM customer_order"));
    }

    @Test void sqlFailureAfterPartialWriteRollsBackEverything() throws Exception {
        var faulty = failOnPrepare("INSERT INTO order_item", 2);
        assertThrows(SQLException.class, () -> new OrderService(faulty).place(1, Map.of(1,2,2,1), Set.of()));
        assertEquals(10, admin.stock(1,1).quantity());
        assertEquals(15, admin.stock(1,2).quantity());
        assertEquals(0, scalar("SELECT COUNT(*) FROM customer_order"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM order_item"));
    }

    private Connections failOnPrepare(String fragment, int occurrence) {
        var seen = new AtomicInteger();
        return () -> {
            var actual = connections.open();
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("prepareStatement") && args[0] instanceof String sql
                                && sql.contains(fragment) && seen.incrementAndGet() == occurrence) {
                            throw new SQLException("Injected write failure");
                        }
                        try { return method.invoke(actual, args); }
                        catch (InvocationTargetException error) { throw error.getCause(); }
                    });
        };
    }

    @Test void concurrentOrdersCannotOversellLastItem() throws Exception {
        var stock = admin.stock(1,1);
        admin.setStock(1,1,stock.version(),1);
        var gate = new CountDownLatch(1);
        var results = new ArrayList<Future<Boolean>>();
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int i=0; i<20; i++) results.add(executor.submit(() -> {
                gate.await();
                try { orders.place(1, Map.of(1,1), Set.of()); return true; }
                catch (Rejected soldOut) { return false; }
            }));
            gate.countDown();
            int successes = 0;
            for (var result : results) if (result.get(30, TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes);
        }
        assertEquals(0, admin.stock(1,1).quantity());
        assertEquals(1, scalar("SELECT COUNT(*) FROM customer_order"));
    }

    @Test void staleStockEditDoesNotEraseAnOrder() throws Exception {
        var stock = admin.stock(1,1);
        orders.place(1, Map.of(1,1), Set.of());
        assertThrows(Rejected.class, () -> admin.setStock(1,1,stock.version(),20));
        assertEquals(9, admin.stock(1,1).quantity());
    }

    @Test void versionDetectsAbaStockChange() throws Exception {
        var old = admin.stock(1,1);
        admin.setStock(1,1,old.version(),9);
        var next = admin.stock(1,1);
        admin.setStock(1,1,next.version(),10);
        assertThrows(Rejected.class, () -> admin.setStock(1,1,old.version(),20));
    }

    @Test void databaseConstraintsRejectNegativeInventoryAndInvalidLinks() throws Exception {
        try (var connection = connections.open(); var statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.executeUpdate("UPDATE offering SET stock = -1 WHERE shop_id = 1 AND menu_id = 1"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO menu_allergen VALUES (1,999)"));
        }
        assertEquals(10, admin.stock(1,1).quantity());
    }

    @Test void addMenuAndAllergensAreAtomic() throws Exception {
        int id = admin.addMenu("テストメニュー", 100, Set.of(1,2));
        assertTrue(id > 6);
        assertEquals(2, scalar("SELECT COUNT(*) FROM menu_allergen WHERE menu_id = " + id));
        long before = scalar("SELECT COUNT(*) FROM menu");
        assertThrows(SQLException.class, () -> new AdminService(failOnPrepare("INSERT INTO menu_allergen",1))
                .addMenu("ロールバック確認", 20, Set.of(1)));
        assertEquals(before, scalar("SELECT COUNT(*) FROM menu"));
    }

    @Test void invalidAllergenDoesNotLeaveOrphanMenu() throws Exception {
        assertThrows(Rejected.class, () -> admin.addMenu("無効メニュー",100,Set.of(999)));
        assertEquals(6, scalar("SELECT COUNT(*) FROM menu"));
    }

    @Test void duplicateOfferingRejectedByDatabase() throws Exception {
        assertThrows(SQLException.class, () -> admin.addOffering(1,1,100,1,false));
        assertEquals(10, admin.stock(1,1).quantity());
    }

    @Test void quotedNameIsDataNotSql() throws Exception {
        int id = admin.addMenu("sample'); DROP TABLE menu;--",1,Set.of());
        assertEquals(7, scalar("SELECT COUNT(*) FROM menu"));
        assertTrue(catalog.menus().stream().anyMatch(menu -> menu.id() == id && menu.name().contains("DROP TABLE")));
    }

    @Test void salesUseOrderTimePriceNotCurrentCatalogPrice() throws Exception {
        orders.place(1, Map.of(1,2), Set.of());
        try (var connection = connections.open(); var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE offering SET price_yen = 999 WHERE shop_id = 1 AND menu_id = 1");
        }
        var sales = admin.sales(1).stream().filter(row -> row.menuId() == 1).findFirst().orElseThrow();
        assertEquals(2, sales.quantity());
        assertEquals(0, new BigDecimal("1300").compareTo(sales.revenue()));
        assertTrue(admin.sales(2).stream().allMatch(row -> row.quantity() == 0));
    }

    @Test void totalsLargerThanIntegerDoNotOverflow() throws Exception {
        var cart = new HashMap<Integer,Integer>();
        for (int i=0; i<30; i++) {
            int id = admin.addMenu("高額サンプル" + i,1,Set.of());
            admin.addOffering(1,id,1_000_000,100,false);
            cart.put(id,100);
        }
        assertEquals(3_000_000_000L, orders.place(1,cart,Set.of()).total());
        assertEquals(0, new BigDecimal("3000000000").compareTo(admin.sales(1).stream()
                .map(row -> row.revenue()).reduce(BigDecimal.ZERO, BigDecimal::add)));
    }

    private String cli(String text, boolean administrative) throws Exception {
        var output = new StringWriter();
        new RestaurantCli(catalog,orders,admin,new StringReader(text),new PrintWriter(output,true)).run(administrative);
        return output.toString();
    }

    @Test void cliEndToEndCustomerOrderAndInputRecovery() throws Exception {
        var output = cli("invalid\n999\n1\n\n\n\n1\n1\n1:2 2:1\n1\n0\n",false);
        assertTrue(output.contains("入力エラー"));
        assertTrue(output.contains("一覧のID"));
        assertTrue(output.contains("合計: 1600円"));
        assertEquals(8,admin.stock(1,1).quantity());
    }

    @Test void eofBeforeConfirmationDoesNotOrder() throws Exception {
        var output = cli("1\n\n\n\n1\n1\n1:1\n",false);
        assertTrue(output.contains("終了しました"));
        assertEquals(0,scalar("SELECT COUNT(*) FROM customer_order"));
    }

    @Test void cliSearchCannotOrderHiddenMenu() throws Exception {
        var output = cli("1\n\n300\n120\n1\n1\n1:1\n0\n",false);
        assertTrue(output.contains("表示した検索結果"));
        assertEquals(0,scalar("SELECT COUNT(*) FROM customer_order"));
    }

    @Test void cliAdminCanContinueAfterAddingOffering() throws Exception {
        var output = cli("5\n2\n3\n750\n6\n1\n1\n2\n0\n",true);
        assertTrue(output.contains("店舗に商品を登録しました"));
        assertTrue(output.contains("3 | チーズドリア"));
        assertEquals(6, admin.stock(2,3).quantity());
    }
}
