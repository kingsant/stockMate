package handler;

import bean.StockBean;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class QuoteParserTest {
    @Test(expected = IllegalArgumentException.class)
    public void rejectsStockUnavailableFromSelectedProvider() {
        List<StockBean> quotes = QuoteParser.sina("var hq_str_hk00700=\"\";",
                Map.of("hk00700", new String[]{"hk00700"}));

        QuoteParser.validate(List.of("hk00700"), quotes);
    }

    @Test
    public void normalizesSinaDateAndTimeForAlerts() {
        String[] fields = new String[32];
        Arrays.fill(fields, "0");
        fields[0] = "测试股票";
        fields[2] = "10";
        fields[3] = "11";
        fields[30] = "2026-10-23";
        fields[31] = "11:00:05";
        String response = "var hq_str_sh600000=\"" + String.join(",", fields) + "\";";

        List<StockBean> result = QuoteParser.sina(response, Map.of("sh600000", new String[]{"sh600000"}));

        assertEquals(1, result.size());
        assertEquals("20261023110005", result.get(0).getTime());
        assertEquals("10.00", result.get(0).getChangePercent());
    }

    @Test
    public void parsesHongKongAndUsTimestampsAndCanonicalSymbols() {
        String[] fields = new String[35];
        Arrays.fill(fields, "0");
        fields[1] = "测试股票";
        fields[3] = "10";
        fields[30] = "2026/10/23 16:08:14";
        String hk = "v_hk00700=\"" + String.join("~", fields) + "\";";

        List<StockBean> hkQuotes = QuoteParser.tencent(hk, Map.of("hk00700", new String[]{"hk00700"}));

        assertEquals("20261023160814", hkQuotes.get(0).getTime());
        fields[30] = "2026-10-22 16:00:01";
        String us = "v_usAAPL=\"" + String.join("~", fields) + "\";";
        List<StockBean> usQuotes = QuoteParser.tencent(us, Map.of("usAAPL", new String[]{"usAAPL"}));
        assertEquals("20261023040001", usQuotes.get(0).getTime());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDuplicatedQuotesInsteadOfPublishingPartialBatch() {
        StockBean quote = new StockBean("sh600000");
        quote.setTime("20261023110000");

        QuoteParser.validate(List.of("sh600000", "sz000001"), List.of(quote, quote));
    }

    @Test(expected = java.time.format.DateTimeParseException.class)
    public void rejectsMalformedTimestampBeforePublishing() {
        StockBean quote = new StockBean("sh600000");
        quote.setTime("invalid");

        QuoteParser.validate(List.of("sh600000"), List.of(quote));
    }

    @Test
    public void ignoresErrorPagesAndUnknownStocks() {
        assertTrue(QuoteParser.tencent("<html>error</html>", Map.of()).isEmpty());
        assertTrue(QuoteParser.sina("var hq_str_sh999999=\"\";", Map.of()).isEmpty());
    }
}
