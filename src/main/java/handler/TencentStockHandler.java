package handler;

import bean.StockBean;
import utils.HttpClientPool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TencentStockHandler {
    private TencentStockHandler() {
    }

    public static List<StockBean> fetch(List<String> entries) throws Exception {
        List<String> codes = new ArrayList<>();
        Map<String, String[]> codeMap = new HashMap<>();
        for (String entry : entries) {
            String[] fields = entry.split(",");
            codes.add(fields[0]);
            codeMap.put(fields[0], fields);
        }
        String response = HttpClientPool.getHttpClient().get("https://qt.gtimg.cn/q=" + String.join(",", codes));
        return QuoteParser.tencent(response, codeMap);
    }
}
