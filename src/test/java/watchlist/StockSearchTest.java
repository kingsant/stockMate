package watchlist;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class StockSearchTest {
    @Test
    public void decodesUnicodeKeepsIndicesAndDeduplicatesMarkets() {
        String response = "v_hint=\"sh~600519~\\u8d35\\u5dde\\u8305\\u53f0~gzmt~GP-A"
                + "^sz~399317~国证A指~gzaz~ZS^sh~600519~贵州茅台~gzmt~GP-A"
                + "^unknown~123~无效~wx~other\";";

        List<StockSearch.Result> results = StockSearch.parse(response);

        assertEquals(2, results.size());
        assertEquals("贵州茅台", results.get(0).name());
        assertEquals("sh600519", results.get(0).code());
        assertEquals("gzmt", results.get(0).pinyin());
        assertEquals("sz399317", results.get(1).code());
    }

    @Test
    public void acceptsNoMatches() {
        assertTrue(StockSearch.parse("v_hint=\"\"").isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsErrorPages() {
        StockSearch.parse("<html>upstream unavailable</html>");
    }
}
