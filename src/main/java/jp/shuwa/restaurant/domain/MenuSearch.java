package jp.shuwa.restaurant.domain;

import java.util.Set;

public record MenuSearch(int shopId, Set<Integer> excludedAllergens, Integer maxPrice,
                         Integer maxCalories, boolean limitedOnly, Sort sort) {
    public enum Sort {
        ID("m.id"), PRICE("o.price_yen, m.id"), CALORIES("m.calories, m.id"),
        POPULARITY("sold_quantity DESC, m.id");

        private final String sql;
        Sort(String sql) { this.sql = sql; }
        public String sql() { return sql; }
    }

    public MenuSearch {
        Rules.id(shopId);
        excludedAllergens = Rules.allergenIds(excludedAllergens);
        if (maxPrice != null) Rules.range(maxPrice, 0, Rules.MAX_PRICE, "価格上限");
        if (maxCalories != null) Rules.range(maxCalories, 0, Rules.MAX_CALORIES, "カロリー上限");
        if (sort == null) throw new IllegalArgumentException("表示順を指定してください。");
    }
}
