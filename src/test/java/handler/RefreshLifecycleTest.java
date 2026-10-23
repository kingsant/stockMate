package handler;

import alert.AlertManager;
import alert.StockAlertRule;
import bean.StockBean;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.mock.MockApplication;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import org.junit.Test;
import support.MemoryProperties;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static org.junit.Assert.*;

public class RefreshLifecycleTest {
    @Test
    public void disposedRequestCannotPublishAndRulesPersistInApplicationSettings() throws Exception {
        Disposable disposable = Disposer.newDisposable();
        List<Runnable> requests = new ArrayList<>();
        MockApplication application = new MockApplication(disposable) {
            @Override
            public Future<?> executeOnPooledThread(Runnable task) {
                requests.add(task);
                return CompletableFuture.completedFuture(null);
            }
        };
        application.registerService(PropertiesComponent.class, new MemoryProperties());
        ApplicationManager.setApplication(application, disposable);
        StockRefreshHandler[] reference = new StockRefreshHandler[1];
        JLabel label = new JLabel();
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    reference[0] = new StockRefreshHandler(new JTable(), label) {
                        @Override
                        protected List<StockBean> fetch(List<String> codes) {
                            StockBean bean = new StockBean("sh600000");
                            bean.setName("响应股票");
                            bean.setNow("10");
                            bean.setChangePercent("1");
                            bean.setTime("20261023100000");
                            return List.of(bean);
                        }
                    };
                    reference[0].setupTable(List.of("sh600000"));
                    reference[0].resume();
                    reference[0].handle(List.of("sh600000"));
                }
            });
            flushEdt();
            assertEquals(1, requests.size());
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    reference[0].dispose();
                    reference[0].clearRow();
                    label.setText("新会话");
                }
            });

            requests.get(0).run();
            flushEdt();

            assertEquals(0, reference[0].getRowCount());
            assertEquals("新会话", label.getText());
            StockAlertRule rule = new StockAlertRule();
            rule.belowPrice = 5.0;
            rule.muted = true;
            AlertManager.save("sh600000", rule);
            assertEquals(rule.serialize(), AlertManager.load("sh600000").serialize());
            AlertManager.remove("sh600000");
            assertNull(AlertManager.load("sh600000"));
        } finally {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    if (reference[0] != null) {
                        reference[0].dispose();
                    }
                }
            });
            Disposer.dispose(disposable);
        }
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
            }
        });
    }
}
