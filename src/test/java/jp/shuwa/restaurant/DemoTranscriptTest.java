package jp.shuwa.restaurant;

import jp.shuwa.restaurant.cli.RestaurantCli;
import jp.shuwa.restaurant.db.CatalogRepository;
import jp.shuwa.restaurant.db.Database;
import jp.shuwa.restaurant.db.DbConfig;
import jp.shuwa.restaurant.service.AdminService;
import jp.shuwa.restaurant.service.OrderService;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoTranscriptTest {
    @Test void captureActualDemoOutput() throws Exception {
        var connections = DbConfig.demo();
        try (var connection = connections.open()) {
            Database.script(connection,"/db/schema.sql");
            Database.script(connection,"/db/demo.sql");
        }
        var customer = new StringWriter();
        new RestaurantCli(new CatalogRepository(connections),new OrderService(connections),new AdminService(connections),
                new StringReader("1\n4\n720\n650\n1\n2\n1:2 2:1\n1\n0\n"),new PrintWriter(customer,true)).run(false);
        assertTrue(customer.toString().contains("合計: 1600円"));
        var management = new StringWriter();
        new RestaurantCli(new CatalogRepository(connections),new OrderService(connections),new AdminService(connections),
                new StringReader("3\n1\n0\n"),new PrintWriter(management,true)).run(true);
        assertTrue(management.toString().contains("総売上: 1600円"));
        var directory = Path.of("target/demo");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("customer.txt"),customer.toString(),StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("management.txt"),management.toString(),StandardCharsets.UTF_8);
    }
}
