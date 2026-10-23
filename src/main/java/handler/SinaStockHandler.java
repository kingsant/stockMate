package handler;

import bean.StockBean;
import utils.HttpClientPool;

import javax.swing.JLabel;
import javax.swing.JTable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SinaStockHandler extends StockRefreshHandler {
    public SinaStockHandler(JTable table, JLabel label) {
        super(table, label);
    }

    @Override
    protected List<StockBean> fetch(List<String> entries) throws Exception {
        List<String> codes = new ArrayList<>();
        Map<String, String[]> codeMap = new HashMap<>();
        for (String entry : entries) {
            String[] fields = entry.split(",");
            codes.add(fields[0]);
            codeMap.put(fields[0], fields);
        }
        String response = HttpClientPool.getHttpClient().get("https://hq.sinajs.cn/list=" + String.join(",", codes));
        return QuoteParser.sina(response, codeMap);
    }
}
