package jp.shuwa.restaurant.cli;

import jp.shuwa.restaurant.domain.Rules;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class ConsoleInput {
    private final BufferedReader reader;
    private final PrintWriter out;
    public ConsoleInput(Reader reader, PrintWriter out) {
        this.reader = new BufferedReader(reader);
        this.out = out;
    }

    public String line(String prompt) throws IOException {
        out.print(prompt + " > ");
        out.flush();
        var value = new StringBuilder();
        boolean tooLong = false;
        while (true) {
            int c = reader.read();
            if (c == -1) {
                if (value.isEmpty() && !tooLong) throw new EOFException();
                break;
            }
            if (c == '\n') break;
            if (value.length() < 4096) value.append((char) c);
            else tooLong = true;
        }
        if (tooLong) throw new IllegalArgumentException("入力が長すぎます。");
        return value.toString().strip();
    }

    public int number(String prompt, int min, int max) throws IOException {
        while (true) {
            try { return parseInt(line(prompt), min, max); }
            catch (IllegalArgumentException error) { out.println("入力エラー: " + error.getMessage()); }
        }
    }

    public Integer optionalNumber(String prompt, int max) throws IOException {
        while (true) {
            var value = line(prompt);
            if (value.isEmpty()) return null;
            try { return parseInt(value, 0, max); }
            catch (IllegalArgumentException error) { out.println("入力エラー: " + error.getMessage()); }
        }
    }

    public static int parseInt(String value, int min, int max) {
        if (!value.matches("[0-9]{1,10}")) throw new IllegalArgumentException("整数で入力してください。");
        try { return Rules.range(Integer.parseInt(value), min, max, "数値"); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("数値が大きすぎます。"); }
    }

    public static Set<Integer> ids(String value) {
        var result = new TreeSet<Integer>();
        if (!value.isBlank()) {
            for (var part : value.strip().split("\\s+")) result.add(parseInt(part, 1, Integer.MAX_VALUE));
        }
        return Rules.allergenIds(result);
    }

    public static Map<Integer, Integer> cart(String value) {
        var result = new TreeMap<Integer, Integer>();
        if (!value.isBlank()) {
            for (var part : value.strip().split("\\s+")) {
                var pair = part.split(":", -1);
                if (pair.length != 2) throw new IllegalArgumentException("注文はID:数量で指定してください。例: 1:2 3:1");
                int id = parseInt(pair[0], 1, Integer.MAX_VALUE);
                int quantity = parseInt(pair[1], 1, Rules.MAX_QUANTITY);
                if (result.putIfAbsent(id, quantity) != null) throw new IllegalArgumentException("同じ商品IDは1回で指定してください。");
            }
        }
        if (result.size() > Rules.MAX_ITEMS) throw new IllegalArgumentException("注文は50種類までです。");
        return Map.copyOf(result);
    }
}
