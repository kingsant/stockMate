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

import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import javax.swing.SwingUtilities;

import static org.junit.Assert.*;

public class RefreshLifecycleTest {
    @Test
    public void sharesRequestsAndKeepsOtherSubscribersAlive() throws Exception {
        withService(new Scenario() {
            @Override
            public void run(Fixture fixture) {
                View first = new View();
                View second = new View();
                fixture.service.subscribe(first);
                fixture.service.subscribe(second);
                fixture.service.refresh();
                fixture.service.refresh();
                assertEquals(1, fixture.tasks.size());
                fixture.completeRequest();
                assertEquals("sh600000", first.quotes.get(0).getCode());
                assertSame(first.quotes, second.quotes);
                assertEquals(1, fixture.alerts.size());
                View third = new View();
                fixture.service.subscribe(third);
                assertSame(first.quotes, third.quotes);
                assertTrue(fixture.tasks.isEmpty());
                assertEquals(1, fixture.alerts.size());
                fixture.service.unsubscribe(first);
                fixture.service.refresh();
                fixture.completeRequest();
                assertEquals(2, fixture.alerts.size());
                assertSame(second.quotes, third.quotes);
            }
        });
    }

    @Test
    public void reloadWaitsForOldRequestAndDoesNotPublishRemovedStock() throws Exception {
        withService(new Scenario() {
            @Override
            public void run(Fixture fixture) {
                View view = new View();
                fixture.service.subscribe(view);
                fixture.properties.setValue("key_stocks", "sz000001");
                fixture.service.reload();
                assertEquals(1, fixture.tasks.size());
                fixture.completeRequest();
                assertEquals("sz000001", view.quotes.get(0).getCode());
                assertNull(view.quotes.get(0).getNow());
                assertEquals(1, fixture.tasks.size());
                assertTrue(fixture.alerts.isEmpty());
                fixture.completeRequest();
                assertEquals("10", view.quotes.get(0).getNow());
                assertEquals(1, fixture.alerts.size());
            }
        });
    }

    @Test
    public void pauseResumeAndCloseReopenWaitForPhysicalCompletion() throws Exception {
        withService(new Scenario() {
            @Override
            public void run(Fixture fixture) {
                View first = new View();
                fixture.service.subscribe(first);
                fixture.service.pause();
                assertFalse(first.running);
                fixture.service.refresh();
                assertTrue(first.running);
                assertEquals(1, fixture.tasks.size());
                fixture.completeRequest();
                assertTrue(fixture.alerts.isEmpty());
                assertEquals(1, fixture.tasks.size());
                fixture.service.unsubscribe(first);
                View reopened = new View();
                fixture.service.subscribe(reopened);
                assertEquals(1, fixture.tasks.size());
                fixture.completeRequest();
                assertEquals(1, fixture.tasks.size());
                assertTrue(fixture.alerts.isEmpty());
                fixture.completeRequest();
                assertEquals("10", reopened.quotes.get(0).getNow());
                fixture.service.unsubscribe(reopened);
                fixture.service.refresh();
                assertTrue(fixture.tasks.isEmpty());
            }
        });
    }

    @Test
    public void validationUsesSameRequestQueueWithoutPublishingOrAlerting() throws Exception {
        withService(new Scenario() {
            @Override
            public void run(Fixture fixture) {
                View view = new View();
                List<String> validationResults = new ArrayList<>();
                fixture.service.subscribe(view);
                fixture.service.validate("sz000001", new StockRefreshService.ValidationCallback() {
                    @Override
                    public void completed(String error) {
                        validationResults.add(error);
                    }
                });
                fixture.service.refresh();
                assertEquals(2, fixture.tasks.size());

                fixture.completeRequest();
                fixture.completeRequest();

                assertEquals(1, validationResults.size());
                assertNull(validationResults.get(0));
                assertEquals("sh600000", view.quotes.get(0).getCode());
                assertEquals(1, fixture.alerts.size());
            }
        });
    }

    @Test
    public void rulesPersistInApplicationSettings() throws Exception {
        withService(new Scenario() {
            @Override
            public void run(Fixture fixture) {
                StockAlertRule rule = new StockAlertRule();
                rule.belowPrice = 5.0;
                rule.muted = true;

                AlertManager.save("sh600000", rule);

                assertEquals(rule.serialize(), AlertManager.load("sh600000").serialize());
                AlertManager.remove("sh600000");
                assertNull(AlertManager.load("sh600000"));
            }
        });
    }

    private interface Scenario {
        void run(Fixture fixture);
    }

    private void withService(Scenario scenario) throws Exception {
        Disposable disposable = Disposer.newDisposable();
        MockApplication application = new MockApplication(disposable);
        Fixture fixture = new Fixture();
        application.registerService(PropertiesComponent.class, fixture.properties);
        ApplicationManager.setApplication(application, disposable);
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    try {
                        scenario.run(fixture);
                    } finally {
                        fixture.service.dispose();
                    }
                }
            });
        } finally {
            Disposer.dispose(disposable);
        }
    }

    private static class View implements StockRefreshService.Listener {
        private List<StockBean> quotes;
        private boolean running;

        @Override
        public void updated(List<StockBean> quotes, String status, boolean running) {
            this.quotes = quotes;
            this.running = running;
        }
    }

    private static class Fixture {
        private final MemoryProperties properties = new MemoryProperties();
        private final List<Runnable> tasks = new ArrayList<>();
        private final List<StockBean> alerts = new ArrayList<>();
        private final StockRefreshService service;

        private Fixture() {
            properties.setValue("key_stocks", "sh600000");
            properties.setValue("key_cron_expression_stock", "0 0 0 1 1 ? 2099");
            service = new StockRefreshService(new StockRefreshService.QuoteFetcher() {
                @Override
                public List<StockBean> fetch(List<String> codes, boolean sina) {
                    StockBean bean = new StockBean(codes.get(0));
                    bean.setNow("10");
                    bean.setTime("20261023100000");
                    return List.of(bean);
                }
            }, new Executor() {
                @Override
                public void execute(Runnable task) {
                    tasks.add(task);
                }
            }, new StockRefreshService.AlertSink() {
                @Override
                public void accept(StockBean quote) {
                    alerts.add(quote);
                }
            });
        }

        private void completeRequest() {
            tasks.remove(0).run();
            // 在当前 EDT 测试中处理真实异步完成事件。
            SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    loop.exit();
                }
            });
            loop.enter();
        }
    }
}
