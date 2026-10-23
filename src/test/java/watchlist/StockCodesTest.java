package watchlist;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class StockCodesTest {
    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidExistingListInsteadOfTreatingItAsEmpty() {
        StockCodes.parse("sh600519;badcode;sz000001");
    }

    @Test
    public void normalizesAndDeduplicatesMarkets() {
        assertEquals("usAAPL", StockCodes.normalize("usaapl"));
        List<String> entries = StockCodes.parse("600519;SH600519;000001;920001;hk00700");

        assertEquals(List.of("sh600519", "sz000001", "bj920001", "hk00700"), entries);
    }

    @Test
    public void acceptsCommaSeparatedCodesWithoutTreatingThemAsHoldings() {
        assertEquals(List.of("sh600519", "sz000001", "sz000002"), StockCodes.parse("600519,000001,000002"));
    }

    @Test
    public void editingKeepsExistingOptionalFields() {
        List<String> entries = StockCodes.parse("600519,123.4,100;000001");

        assertEquals(List.of("sh600000,123.4,100", "sz000001"), StockCodes.replace(entries, "sh600519", "600000"));
        assertEquals(List.of("sh600519,123.4,100"), StockCodes.parse("600519,123.4,100"));
    }

    @Test
    public void acceptsWhitespaceAndWindowsLineEndings() {
        assertEquals(List.of("sh600519", "sz000001"), StockCodes.parse(" 600519 \r\n SZ000001 \r\n"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNormalizedDuplicates() {
        StockCodes.replace(List.of("sh600519"), null, "600519");
    }

    @Test(expected = IllegalArgumentException.class)
    public void doesNotRestoreDeletedStockFromPendingEdit() {
        StockCodes.replace(List.of("sz000001"), "sh600519", "600000");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidInput() {
        StockCodes.normalize("600519&other=1");
    }
}
