package jp.shuwa.restaurant.db;

import java.net.URI;
import java.sql.DriverManager;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public final class DbConfig {
    private DbConfig() { }

    public static Connections fromEnvironment(Map<String, String> env) {
        var host = env.getOrDefault("RESTAURANT_DB_HOST", "localhost");
        var database = env.getOrDefault("RESTAURANT_DB_NAME", "restaurant");
        var user = env.getOrDefault("RESTAURANT_DB_USER", "");
        var password = env.getOrDefault("RESTAURANT_DB_PASSWORD", "");
        var port = env.getOrDefault("RESTAURANT_DB_PORT", "3306");
        if (!host.matches("[A-Za-z0-9.-]+") || !database.matches("[A-Za-z0-9_]+")
                || !port.matches("[0-9]{1,5}") || Integer.parseInt(port) < 1 || Integer.parseInt(port) > 65535
                || user.isBlank() || user.equalsIgnoreCase("root") || password.isBlank()) {
            throw new IllegalArgumentException("DB接続設定を確認してください。root・空パスワードは使用できません。");
        }
        boolean local = host.equals("localhost") || host.equals("127.0.0.1");
        var tls = env.getOrDefault("RESTAURANT_DB_TLS", "VERIFY_IDENTITY");
        if (!tls.equals("VERIFY_IDENTITY") && !(local && Set.of("DISABLED", "REQUIRED").contains(tls))) {
            throw new IllegalArgumentException("外部DB接続にはVERIFY_IDENTITYが必要です。");
        }
        var url = URI.create("mysql://" + host + ":" + port + "/" + database);
        var properties = new Properties();
        properties.setProperty("user", user);
        properties.setProperty("password", password);
        properties.setProperty("sslMode", tls);
        properties.setProperty("allowPublicKeyRetrieval", "false");
        properties.setProperty("allowLoadLocalInfile", "false");
        properties.setProperty("allowUrlInLocalInfile", "false");
        properties.setProperty("allowMultiQueries", "false");
        properties.setProperty("useServerPrepStmts", "true");
        properties.setProperty("connectTimeout", "5000");
        properties.setProperty("socketTimeout", "10000");
        properties.setProperty("characterEncoding", "UTF-8");
        properties.setProperty("connectionTimeZone", "UTC");
        return () -> DriverManager.getConnection("jdbc:" + url, properties);
    }

    public static Connections demo() {
        var url = "jdbc:h2:mem:demo_" + java.util.UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000";
        return () -> DriverManager.getConnection(url);
    }
}
