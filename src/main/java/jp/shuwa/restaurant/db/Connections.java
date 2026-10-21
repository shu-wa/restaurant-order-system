package jp.shuwa.restaurant.db;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface Connections {
    Connection open() throws SQLException;
}
