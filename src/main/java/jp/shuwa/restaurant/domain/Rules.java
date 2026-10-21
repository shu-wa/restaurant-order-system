package jp.shuwa.restaurant.domain;

import java.util.Set;

public final class Rules {
    public static final int MAX_STOCK = 1_000_000;
    public static final int MAX_PRICE = 1_000_000;
    public static final int MAX_CALORIES = 100_000;
    public static final int MAX_QUANTITY = 100;
    public static final int MAX_ITEMS = 50;

    private Rules() { }

    public static int range(int value, int min, int max, String label) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(label + "は" + min + "～" + max + "で指定してください。");
        }
        return value;
    }

    public static int id(int value) {
        return range(value, 1, Integer.MAX_VALUE, "ID");
    }

    public static String name(String value) {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > 80
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("名前は制御文字を含まない1～80文字で指定してください。");
        }
        return value.strip();
    }

    public static Set<Integer> allergenIds(Set<Integer> values) {
        if (values == null || values.size() > 100) {
            throw new IllegalArgumentException("アレルゲンIDは100件以内で指定してください。");
        }
        values.forEach(Rules::id);
        return Set.copyOf(values);
    }
}
