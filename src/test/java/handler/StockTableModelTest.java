package handler;

import bean.StockBean;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.mock.MockApplication;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import org.junit.Test;
import support.MemoryProperties;
import utils.PinYinUtils;
import utils.WindowUtils;

import java.awt.Color;
import java.awt.Component;
import java.util.List;

import javax.swing.JTable;
import javax.swing.SwingUtilities;

import static org.junit.Assert.assertEquals;

public class StockTableModelTest {
    @Test
    public void migratesSavedColumnsAndKeepsRemainingOrderInBothModes() throws Exception {
        withApplication(new Scenario() {
            @Override
            public void run(MemoryProperties properties) {
                JTable defaultTable = new JTable();
                StockTableModel defaultModel = new StockTableModel(defaultTable);
                assertColumns(defaultModel, WindowUtils.STOCK_TABLE_HEADER_VALUE);
                properties.setValue(WindowUtils.STOCK_TABLE_HEADER_KEY,
                        "收益率," + PinYinUtils.toPinYin("当前价") + ",编码,涨跌,股票名称,持仓,编码,未知,成本价,收益");

                StockTableModel model = new StockTableModel(new JTable());

                String expected = "当前价,编码,股票名称,涨跌幅,最高价,最低价,更新时间";
                assertColumns(model, expected);
                assertEquals(expected, properties.getValue(WindowUtils.STOCK_TABLE_HEADER_KEY));
                model.refreshDisplay(true);
                assertColumns(model, expected);
                model.refreshDisplay(false);
                assertColumns(model, expected);
                assertEquals("编码", WindowUtils.remapPinYin(model.getColumnName(model.codeColumnIndex)));
            }
        });
    }

    @Test
    public void positiveAndNegativePercentagesUseThemeForegroundAndSelectionColors() throws Exception {
        withApplication(new Scenario() {
            @Override
            public void run(MemoryProperties properties) {
                JTable table = new JTable();
                table.setForeground(new Color(33, 44, 55));
                table.setSelectionForeground(new Color(200, 210, 220));
                StockTableModel model = new StockTableModel(table);
                List<StockBean> quotes = List.of(quote("sh600000", "3"), quote("sz000001", "-10"));
                for (boolean normalMode : new boolean[]{true, false}) {
                    model.refreshDisplay(normalMode);
                    model.showQuotes(quotes);
                    int column = column(model, "涨跌幅");
                    assertEquals("+3%", model.getValueAt(0, column));
                    assertEquals("-10%", model.getValueAt(1, column));
                    for (int row = 0; row < 2; row++) {
                        table.clearSelection();
                        Component unselected = table.prepareRenderer(table.getCellRenderer(row, column), row, column);
                        assertEquals(table.getForeground(), unselected.getForeground());
                        table.setRowSelectionInterval(row, row);
                        Component selected = table.prepareRenderer(table.getCellRenderer(row, column), row, column);
                        assertEquals(table.getSelectionForeground(), selected.getForeground());
                    }
                }
            }
        });
    }

    @Test
    public void sortsPercentageNumericallyAfterRemovingColumns() throws Exception {
        withApplication(new Scenario() {
            @Override
            public void run(MemoryProperties properties) {
                JTable table = new JTable();
                StockTableModel model = new StockTableModel(table);
                model.refreshDisplay(true);
                model.showQuotes(List.of(quote("sh600000", "12"), quote("sz000001", "2"), quote("sz000002", "-10")));

                table.getRowSorter().toggleSortOrder(column(model, "涨跌幅"));

                assertEquals("sz000002", model.getValueAt(table.convertRowIndexToModel(0), model.codeColumnIndex));
                assertEquals("sz000001", model.getValueAt(table.convertRowIndexToModel(1), model.codeColumnIndex));
                assertEquals("sh600000", model.getValueAt(table.convertRowIndexToModel(2), model.codeColumnIndex));
            }
        });
    }

    private static StockBean quote(String code, String percentage) {
        StockBean quote = new StockBean(code);
        quote.setName("示例");
        quote.setChangePercent(percentage);
        return quote;
    }

    private static int column(StockTableModel model, String name) {
        for (int i = 0; i < model.getColumnCount(); i++) {
            if (WindowUtils.remapPinYin(model.getColumnName(i)).equals(name)) {
                return i;
            }
        }
        throw new AssertionError("列不存在：" + name);
    }

    private static void assertColumns(StockTableModel model, String expected) {
        String[] names = expected.split(",");
        assertEquals(names.length, model.getColumnCount());
        for (int i = 0; i < names.length; i++) {
            assertEquals(names[i], WindowUtils.remapPinYin(model.getColumnName(i)));
        }
    }

    private interface Scenario {
        void run(MemoryProperties properties);
    }

    private static void withApplication(Scenario scenario) throws Exception {
        Disposable disposable = Disposer.newDisposable();
        MockApplication application = new MockApplication(disposable);
        MemoryProperties properties = new MemoryProperties();
        application.registerService(PropertiesComponent.class, properties);
        ApplicationManager.setApplication(application, disposable);
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    scenario.run(properties);
                }
            });
        } finally {
            Disposer.dispose(disposable);
        }
    }
}
