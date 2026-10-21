package jp.shuwa.restaurant.domain;

import java.math.BigDecimal;
import java.util.List;

public final class Models {
    private Models() { }
    public record NamedId(int id, String name) { }
    public record MenuRow(int id, String name, int calories, int price, int stock,
                          boolean limited, long soldQuantity) { }
    public record OrderLine(int menuId, String name, int quantity, int unitPrice) {
        public long subtotal() { return (long) quantity * unitPrice; }
    }
    public record Receipt(long orderId, List<OrderLine> lines) {
        public Receipt { lines = List.copyOf(lines); }
        public long total() { return lines.stream().mapToLong(OrderLine::subtotal).sum(); }
    }
    public record SalesRow(int menuId, String name, long quantity, BigDecimal revenue) { }
    public record Stock(int quantity, long version) { }
}
