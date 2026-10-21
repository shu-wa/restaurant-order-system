package jp.shuwa.restaurant.db;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {
    private Database() { }

    public static PreparedStatement prepare(Connection connection, String sql) throws SQLException {
        var statement = connection.prepareStatement(sql);
        statement.setQueryTimeout(10);
        return statement;
    }

    @FunctionalInterface
    public interface Work<T> { T run(Connection connection) throws SQLException; }

    public static <T> T transaction(Connections connections, Work<T> work) throws SQLException {
        try (var connection = connections.open()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            try {
                var result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException error) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    error.addSuppressed(rollbackError);
                }
                throw error;
            }
        }
    }

    // Only trusted, bundled SQL resources are accepted. Never call this with user SQL.
    public static void script(Connection connection, String resource) throws SQLException, IOException {
        try (var input = Database.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing SQL resource: " + resource);
            var sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(10);
                for (var part : sql.split(";")) {
                    if (!part.isBlank()) statement.execute(part);
                }
            }
        }
    }
}
