package watchlist;

import cn.hutool.json.JSONUtil;
import utils.HttpClientPool;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StockSearch {
    private StockSearch() {
    }

    public record Result(String code, String name, String pinyin) {
        @Override
        public String toString() {
            return name + "  " + code + "  " + pinyin;
        }
    }

    public static List<Result> fetch(String query) throws Exception {
        String url = "https://smartbox.gtimg.cn/s3/?q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&t=all";
        return parse(HttpClientPool.getHttpClient().get(url));
    }

    public static List<Result> parse(String response) {
        String text = response.trim();
        if (!text.startsWith("v_hint=")) {
            throw new IllegalArgumentException("搜索响应格式无效");
        }
        String value = text.substring("v_hint=".length()).trim();
        if (value.endsWith(";")) {
            value = value.substring(0, value.length() - 1);
        }
        if (!value.startsWith("\"") || !value.endsWith("\"")) {
            throw new IllegalArgumentException("搜索响应格式无效");
        }
        String records = JSONUtil.parseObj("{\"value\":" + value + "}").getStr("value");
        Map<String, Result> results = new LinkedHashMap<>();
        for (String record : records.split("\\^")) {
            String[] fields = record.split("~", -1);
            if (fields.length < 4 || fields[2].isBlank()) {
                continue;
            }
            try {
                String code = StockCodes.normalize(fields[0] + fields[1]);
                results.putIfAbsent(code, new Result(code, fields[2], fields[3]));
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (results.size() >= 30) {
                break;
            }
        }
        return new ArrayList<>(results.values());
    }
}
