package jp.shuwa.restaurant;

import jp.shuwa.restaurant.cli.RestaurantCli;
import jp.shuwa.restaurant.db.CatalogRepository;
import jp.shuwa.restaurant.db.Database;
import jp.shuwa.restaurant.db.DbConfig;
import jp.shuwa.restaurant.security.AdminAuth;
import jp.shuwa.restaurant.service.AdminService;
import jp.shuwa.restaurant.service.OrderService;

import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class Main {
    private Main() { }

    public static void main(String[] args) {
        try {
            var flags = new HashSet<>(Arrays.asList(args));
            if (flags.size() != args.length || !Set.of("--demo", "--admin", "--hash-password", "--help").containsAll(flags)) {
                throw new IllegalArgumentException("引数が不正です。--helpを確認してください。");
            }
            if (flags.contains("--help")) {
                System.out.println("java -jar target/restaurant.jar [--demo] [--admin]\n--demo: 架空データの一時DB\n--admin: 管理モード\n--hash-password: 管理パスワードのハッシュ作成");
                return;
            }
            if (flags.contains("--hash-password")) {
                if (flags.size() != 1) throw new IllegalArgumentException("--hash-passwordは単独で指定してください。");
                var console = System.console();
                if (console == null) throw new IllegalArgumentException("パスワード作成は対話型ターミナルで実行してください。");
                var password = console.readPassword("管理パスワード (12～128文字): ");
                if (password == null) return;
                try { console.printf("%s%n", AdminAuth.hash(password)); }
                finally { Arrays.fill(password, '\0'); }
                return;
            }
            boolean demo = flags.contains("--demo");
            boolean admin = flags.contains("--admin");
            if (admin && !demo) {
                var encoded = System.getenv("RESTAURANT_ADMIN_PASSWORD_HASH");
                var console = System.console();
                if (encoded == null || console == null) {
                    throw new IllegalArgumentException("管理モードにはハッシュ設定と対話型ターミナルが必要です。");
                }
                boolean authenticated = false;
                for (int attempt = 0; attempt < 3 && !authenticated; attempt++) {
                    var password = console.readPassword("管理パスワード: ");
                    if (password == null) return;
                    try { authenticated = AdminAuth.verify(password, encoded); }
                    finally { Arrays.fill(password, '\0'); }
                    if (!authenticated) console.printf("認証できませんでした。%n");
                }
                if (!authenticated) throw new IllegalArgumentException("認証を終了しました。");
            }
            var connections = demo ? DbConfig.demo() : DbConfig.fromEnvironment(System.getenv());
            if (demo) {
                try (var connection = connections.open()) {
                    Database.script(connection, "/db/schema.sql");
                    Database.script(connection, "/db/demo.sql");
                }
                System.out.println("DEMO: 架空データです。終了すると変更は消えます。実際の食物アレルギーの判断には使用できません。");
            }
            var out = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
            new RestaurantCli(new CatalogRepository(connections), new OrderService(connections),
                    new AdminService(connections), new InputStreamReader(System.in, StandardCharsets.UTF_8), out).run(admin);
        } catch (IllegalArgumentException error) {
            System.err.println(error.getMessage());
            System.exit(2);
        } catch (Exception error) {
            System.err.println("起動・入出力処理を完了できませんでした。設定とDBの状態を確認してください。");
            System.exit(1);
        }
    }
}
