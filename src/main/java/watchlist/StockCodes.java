package watchlist;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class StockCodes {
    private StockCodes() {
    }

    public static String normalize(String value) {
        String code = value.trim().toLowerCase(Locale.ROOT);
        if (code.matches("[0-9]{6}")) {
            if (code.startsWith("6")) {
                return "sh" + code;
            }
            if (code.startsWith("0") || code.startsWith("3")) {
                return "sz" + code;
            }
            if (code.startsWith("4") || code.startsWith("8") || code.startsWith("92")) {
                return "bj" + code;
            }
        }
        if (code.matches("us[a-z][a-z0-9.]{0,14}")) {
            return "us" + code.substring(2).toUpperCase(Locale.ROOT);
        }
        if (code.matches("(sh|sz|bj)[0-9]{6}") || code.matches("hk[0-9]{5}")) {
            return code;
        }
        throw new IllegalArgumentException("请输入六位 A 股代码，或带 sh/sz/bj/hk/us 前缀的代码");
    }

    public static String codeOf(String entry) {
        return normalize(entry.split(",", 2)[0]);
    }

    public static List<String> parse(String value) {
        Map<String, String> entries = new LinkedHashMap<>();
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        String normalized = value.replace('，', ',').replace('；', ';').replace('\n', ';');
        String[] parts;
        if (normalized.contains(";")) {
            parts = normalized.split(";");
        } else {
            String[] commaParts = normalized.split(",");
            boolean allCodes = true;
            for (String part : commaParts) {
                try {
                    normalize(part);
                } catch (IllegalArgumentException e) {
                    allCodes = false;
                    break;
                }
            }
            if (!allCodes && commaParts.length == 3) {
                parts = new String[]{normalized};
            } else {
                parts = commaParts;
            }
        }
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            String[] fields = part.trim().split(",", 2);
            String code = normalize(fields[0]);
            String entry = code;
            if (fields.length == 2) {
                entry += "," + fields[1];
            }
            entries.putIfAbsent(code, entry);
        }
        return new ArrayList<>(entries.values());
    }

    public static List<String> replace(List<String> entries, String oldCode, String newCode) {
        String code = normalize(newCode);
        for (String entry : entries) {
            if (codeOf(entry).equals(code) && !code.equals(oldCode)) {
                throw new IllegalArgumentException("该股票已在自选列表中");
            }
        }
        List<String> result = new ArrayList<>();
        boolean found = oldCode == null;
        for (String entry : entries) {
            if (codeOf(entry).equals(oldCode)) {
                int separator = entry.indexOf(',');
                result.add(code + (separator < 0 ? "" : entry.substring(separator)));
                found = true;
            } else {
                result.add(entry);
            }
        }
        if (!found) {
            throw new IllegalArgumentException("该股票已被移除，请重新添加");
        }
        if (oldCode == null) {
            result.add(code);
        }
        return result;
    }
}
