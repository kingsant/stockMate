import bean.StockBean;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.mock.MockApplication;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import handler.StockRefreshService;
import org.junit.Test;
import support.MemoryProperties;
import utils.WindowUtils;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import javax.swing.JTable;
import javax.swing.SwingUtilities;

import static org.junit.Assert.*;

public class MultiWindowOwnershipTest {
    @Test
    public void twoWindowsKeepTheirOwnTableAndShareOneRequest() throws Exception {
        Disposable disposable = Disposer.newDisposable();
        MockApplication application = new MockApplication(disposable);
        MemoryProperties properties = new MemoryProperties();
        properties.setValue("key_stocks", "sh600000");
        properties.setValue("key_cron_expression_stock", "0 0 0 1 1 ? 2099");
        application.registerService(PropertiesComponent.class, properties);
        ApplicationManager.setApplication(application, disposable);
        List<Runnable> tasks = new ArrayList<>();
        StockRefreshService service = new StockRefreshService(new StockRefreshService.QuoteFetcher() {
            @Override
            public List<StockBean> fetch(List<String> codes, boolean sina) {
                StockBean quote = new StockBean(codes.get(0));
                quote.setName("共享行情");
                quote.setNow("10");
                quote.setTime("20261023100000");
                return List.of(quote);
            }
        }, new Executor() {
            @Override
            public void execute(Runnable task) {
                tasks.add(task);
            }
        }, new StockRefreshService.AlertSink() {
            @Override
            public void accept(StockBean quote) {
            }
        });
        StockWindow[] windows = new StockWindow[2];
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    windows[0] = new StockWindow(service);
                    windows[1] = new StockWindow(service);
                    JTable first = findTable(windows[0].getmPanel());
                    JTable second = findTable(windows[1].getmPanel());
                    assertNotNull(first);
                    assertNotNull(second);
                    assertNotSame(first, second);
                    assertNotSame(first.getModel(), second.getModel());
                    assertTrue(SwingUtilities.isDescendingFrom(first, windows[0].getmPanel()));
                    assertTrue(SwingUtilities.isDescendingFrom(second, windows[1].getmPanel()));
                    assertEquals(1, tasks.size());
                }
            });
            tasks.remove(0).run();
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    assertEquals("10", currentPrice(findTable(windows[0].getmPanel())));
                    assertEquals("10", currentPrice(findTable(windows[1].getmPanel())));
                    windows[0].dispose();
                    service.refresh();
                    assertEquals(1, tasks.size());
                    assertNotNull(findTable(windows[1].getmPanel()));
                }
            });
        } finally {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    service.dispose();
                }
            });
            Disposer.dispose(disposable);
        }
    }

    private static JTable findTable(Container parent) {
        for (Component component : parent.getComponents()) {
            if (component instanceof JTable) {
                return (JTable) component;
            }
            if (component instanceof Container) {
                JTable found = findTable((Container) component);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static Object currentPrice(JTable table) {
        for (int i = 0; i < table.getModel().getColumnCount(); i++) {
            if (WindowUtils.remapPinYin(table.getModel().getColumnName(i)).equals("当前价")) {
                return table.getModel().getValueAt(0, i);
            }
        }
        throw new AssertionError("当前价列不存在");
    }
}
