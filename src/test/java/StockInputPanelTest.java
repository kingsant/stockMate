import bean.StockBean;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.mock.MockApplication;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import handler.StockRefreshService;
import org.junit.Test;
import support.MemoryProperties;
import watchlist.StockSearch;

import java.awt.Component;
import java.awt.Container;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import static org.junit.Assert.*;

public class StockInputPanelTest {
    @Test
    public void batchSaveValidatesOnceAndPreservesExistingFields() throws Exception {
        withPanel(null, new Scenario() {
            @Override
            public void run(Fixture fixture) {
                fixture.input().setText("600000\n600519\nSZ000001\nsh600519");

                fixture.save().doClick();
                assertEquals(1, fixture.tasks.size());
                fixture.complete();

                assertEquals(List.of("sh600519", "sz000001"), fixture.requested);
                assertEquals("sh600000,5,100;\nsh600519;\nsz000001", fixture.properties.getValue("key_stocks"));
                assertEquals(1, fixture.requests);
                assertTrue(fixture.tasks.isEmpty());
            }
        });
    }

    @Test
    public void invalidLinesAndUnavailableQuotesKeepInputAndConfig() throws Exception {
        withPanel(null, new Scenario() {
            @Override
            public void run(Fixture fixture) {
                fixture.input().setText("600519\ninvalid");
                fixture.save().doClick();
                assertTrue(fixture.message().getText().contains("第 2 行"));
                assertTrue(fixture.tasks.isEmpty());
                fixture.input().setText("600519\n000001");
                fixture.failValidation = true;
                fixture.save().doClick();
                fixture.complete();

                assertEquals("600519\n000001", fixture.input().getText());
                assertTrue(fixture.message().getText().contains("sz000001"));
                assertEquals("sh600000,5,100", fixture.properties.getValue("key_stocks"));
                assertTrue(fixture.save().isEnabled());
            }
        });
    }

    @Test
    public void changedConfigAndClosedPanelCannotBeOverwrittenByPendingValidation() throws Exception {
        withPanel(null, new Scenario() {
            @Override
            public void run(Fixture fixture) {
                fixture.input().setText("600519");
                fixture.save().doClick();
                fixture.properties.setValue("key_stocks", "sz000001");
                fixture.service.reload();
                fixture.complete();
                assertEquals("sz000001", fixture.properties.getValue("key_stocks"));
                assertTrue(fixture.message().getText().contains("已变化"));
                fixture.save().doClick();
                fixture.panel.close();
                fixture.complete();
                assertEquals("sz000001", fixture.properties.getValue("key_stocks"));
            }
        });
    }

    @Test
    public void editingRejectsMultipleCodesAndKeepsAttachedFields() throws Exception {
        withPanel("sh600000", new Scenario() {
            @Override
            public void run(Fixture fixture) {
                fixture.input().setText("600519\n000001");
                fixture.save().doClick();
                assertTrue(fixture.message().getText().contains("只能输入一个"));
                assertTrue(fixture.tasks.isEmpty());
                fixture.input().setText("600519");
                fixture.save().doClick();
                fixture.complete();
                assertEquals("sh600519,5,100", fixture.properties.getValue("key_stocks"));
            }
        });
    }

    @Test
    public void pinyinSearchDebouncesAndEnterAppendsWithoutDuplicates() throws Exception {
        withPanel(null, new Scenario() {
            @Override
            public void run(Fixture fixture) {
                JTextField search = (JTextField) find(fixture.panel, "search");
                search.setText("g");
                search.setText("gz");
                search.setText("gzmt");
                waitForDebounce();
                assertEquals(1, fixture.tasks.size());
                fixture.complete();
                JList<?> results = (JList<?>) find(fixture.panel, "results");
                assertEquals(1, results.getModel().getSize());
                results.getActionMap().get("add-result").actionPerformed(new ActionEvent(results, 0, "ENTER"));
                results.getActionMap().get("add-result").actionPerformed(new ActionEvent(results, 0, "ENTER"));
                assertEquals("sh600519", fixture.input().getText());
                assertEquals("gzmt", fixture.query);
                search.setText("cancel");
                fixture.panel.close();
                waitForDebounce();
                assertTrue(fixture.tasks.isEmpty());
            }
        });
    }

    private interface Scenario {
        void run(Fixture fixture);
    }

    private void withPanel(String oldCode, Scenario scenario) throws Exception {
        Disposable disposable = Disposer.newDisposable();
        MockApplication application = new MockApplication(disposable);
        MemoryProperties properties = new MemoryProperties();
        properties.setValue("key_stocks", "sh600000,5,100");
        application.registerService(PropertiesComponent.class, properties);
        ApplicationManager.setApplication(application, disposable);
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    Fixture fixture = new Fixture(properties, oldCode);
                    try {
                        scenario.run(fixture);
                    } finally {
                        fixture.panel.close();
                        fixture.service.dispose();
                    }
                }
            });
        } finally {
            Disposer.dispose(disposable);
        }
    }

    private static void waitForDebounce() {
        SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        Timer timer = new Timer(400, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                loop.exit();
            }
        });
        timer.setRepeats(false);
        timer.start();
        loop.enter();
    }

    private static Component find(Container container, String name) {
        for (Component component : container.getComponents()) {
            if (name.equals(component.getName())) {
                return component;
            }
            if (component instanceof Container) {
                Component found = find((Container) component, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static class Fixture {
        private final MemoryProperties properties;
        private final List<Runnable> tasks = new ArrayList<>();
        private final StockRefreshService service;
        private final StockInputPanel panel;
        private List<String> requested;
        private String query;
        private int requests;
        private boolean failValidation;

        private Fixture(MemoryProperties properties, String oldCode) {
            this.properties = properties;
            service = new StockRefreshService(new StockRefreshService.QuoteFetcher() {
                @Override
                public List<StockBean> fetch(List<String> codes, boolean sina) {
                    requested = codes;
                    requests++;
                    List<StockBean> quotes = new ArrayList<>();
                    for (String code : codes) {
                        if (failValidation && code.equals("sz000001")) {
                            continue;
                        }
                        StockBean quote = new StockBean(code);
                        quote.setTime("20261023100000");
                        quotes.add(quote);
                    }
                    return quotes;
                }
            }, new Executor() {
                @Override
                public void execute(Runnable task) {
                    tasks.add(task);
                }
            }, new StockRefreshService.AlertSink() {
                @Override
                public void accept(StockBean quote) {
                    fail("Validation must not publish alerts");
                }
            }, new StockRefreshService.SearchFetcher() {
                @Override
                public List<StockSearch.Result> fetch(String input) {
                    query = input;
                    return List.of(new StockSearch.Result("sh600519", "贵州茅台", "gzmt"));
                }
            });
            service.reload();
            panel = new StockInputPanel(service, oldCode);
        }

        private JTextArea input() {
            return (JTextArea) find(panel, "codes");
        }

        private JButton save() {
            return (JButton) find(panel, "save");
        }

        private JLabel message() {
            return (JLabel) find(panel, "message");
        }

        private void complete() {
            tasks.remove(0).run();
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
