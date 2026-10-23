package handler;

import alert.AlertManager;
import bean.StockBean;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import org.quartz.CronExpression;
import utils.HttpClientPool;
import watchlist.StockCodes;
import watchlist.StockSearch;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

@Service(Service.Level.APP)
public final class StockRefreshService implements Disposable {
    public interface Listener {
        void updated(List<StockBean> quotes, String status, boolean running);
    }

    public interface QuoteFetcher {
        List<StockBean> fetch(List<String> codes, boolean useSina) throws Exception;
    }

    public interface AlertSink {
        void accept(StockBean quote);
    }

    public interface ValidationCallback {
        void completed(String error);
    }

    public interface SearchFetcher {
        List<StockSearch.Result> fetch(String query) throws Exception;
    }

    public interface SearchCallback {
        void completed(List<StockSearch.Result> results, String error);
    }

    private final List<Listener> listeners = new ArrayList<>();
    private final List<CronExpression> schedules = new ArrayList<>();
    private final QuoteFetcher fetcher;
    private final Executor executor;
    private final AlertSink alerts;
    private final SearchFetcher searchFetcher;
    private final Timer timer;
    private List<String> codes = List.of();
    private List<StockBean> quotes = List.of();
    private RefreshStatus status = new RefreshStatus();
    private boolean useSina;
    private boolean paused;
    private boolean inFlight;
    private boolean pendingRefresh;
    private volatile boolean disposed;
    private long generation;
    private Date nextRefresh;
    private String configError;

    public StockRefreshService() {
        this(new QuoteFetcher() {
            @Override
            public List<StockBean> fetch(List<String> entries, boolean sina) throws Exception {
                if (sina) {
                    return SinaStockHandler.fetch(entries);
                }
                return TencentStockHandler.fetch(entries);
            }
        }, new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), new ThreadFactory() {
            @Override
            public Thread newThread(Runnable task) {
                Thread thread = new Thread(task, "uka-quotes");
                thread.setDaemon(true);
                return thread;
            }
        }), new AlertSink() {
            @Override
            public void accept(StockBean quote) {
                AlertManager.recordPriceAndAlert(quote);
            }
        });
        HttpClientPool.getHttpClient().buildHttpClient(PropertiesComponent.getInstance().getValue("key_proxy"));
    }

    public StockRefreshService(QuoteFetcher fetcher, Executor executor, AlertSink alerts) {
        this(fetcher, executor, alerts, new SearchFetcher() {
            @Override
            public List<StockSearch.Result> fetch(String query) throws Exception {
                return StockSearch.fetch(query);
            }
        });
    }

    public StockRefreshService(QuoteFetcher fetcher, Executor executor, AlertSink alerts, SearchFetcher searchFetcher) {
        this.fetcher = fetcher;
        this.executor = executor;
        this.alerts = alerts;
        this.searchFetcher = searchFetcher;
        timer = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (!paused && nextRefresh != null && !new Date().before(nextRefresh)) {
                    scheduleNext();
                    requestRefresh();
                }
                publish();
            }
        });
    }

    public static StockRefreshService getInstance() {
        return ApplicationManager.getApplication().getService(StockRefreshService.class);
    }

    public void subscribe(Listener listener) {
        requireEdt();
        if (disposed || listeners.contains(listener)) {
            return;
        }
        listeners.add(listener);
        if (listeners.size() == 1) {
            timer.start();
            reload();
        } else {
            notifyListener(listener);
        }
    }

    public void unsubscribe(Listener listener) {
        requireEdt();
        listeners.remove(listener);
        if (listeners.isEmpty()) {
            generation++;
            pendingRefresh = false;
            nextRefresh = null;
            timer.stop();
        }
    }

    public void reload() {
        requireEdt();
        generation++;
        configError = null;
        status = new RefreshStatus();
        PropertiesComponent properties = PropertiesComponent.getInstance();
        try {
            codes = StockCodes.parse(properties.getValue("key_stocks"));
            useSina = properties.getBoolean("key_stocks_sina");
            schedules.clear();
            String cron = properties.getValue("key_cron_expression_stock", "*/10 * * * * ?");
            for (String expression : cron.split(";", -1)) {
                schedules.add(new CronExpression(expression.trim()));
            }
        } catch (IllegalArgumentException | ParseException e) {
            codes = List.of();
            schedules.clear();
            configError = "配置无效：" + e.getMessage();
        }
        List<StockBean> placeholders = new ArrayList<>();
        for (String code : codes) {
            placeholders.add(new StockBean(code));
        }
        quotes = List.copyOf(placeholders);
        paused = false;
        pendingRefresh = !codes.isEmpty() && !listeners.isEmpty();
        scheduleNext();
        publish();
        requestRefresh();
    }

    public void refresh() {
        requireEdt();
        if (paused) {
            pendingRefresh = true;
        }
        paused = false;
        scheduleNext();
        requestRefresh();
        publish();
    }

    public void pause() {
        requireEdt();
        paused = true;
        generation++;
        pendingRefresh = false;
        nextRefresh = null;
        publish();
    }

    public long getGeneration() {
        requireEdt();
        return generation;
    }

    public void validate(List<String> requestedCodes, ValidationCallback callback) {
        requireEdt();
        boolean selectedSource = useSina;
        List<String> requested = List.copyOf(requestedCodes);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                String error = null;
                try {
                    QuoteParser.validate(requested, fetcher.fetch(requested, selectedSource));
                } catch (Exception e) {
                    error = "校验失败：" + failureMessage(e);
                }
                String result = error;
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        if (!disposed) {
                            callback.completed(result);
                        }
                    }
                });
            }
        });
    }

    public SearchSession newSearchSession() {
        requireEdt();
        return new SearchSession();
    }

    public final class SearchSession implements AutoCloseable {
        private volatile long revision;
        private volatile boolean closed;

        private SearchSession() {
        }

        public void invalidate() {
            requireEdt();
            revision++;
        }

        public void search(String input, SearchCallback callback) {
            requireEdt();
            String query = input.trim();
            long requestRevision = ++revision;
            if (closed || disposed || query.isEmpty()) {
                return;
            }
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (closed || disposed || revision != requestRevision) {
                        return;
                    }
                    List<StockSearch.Result> results = List.of();
                    String failure = null;
                    try {
                        results = searchFetcher.fetch(query);
                    } catch (Exception e) {
                        failure = "搜索失败：" + failureMessage(e);
                    }
                    List<StockSearch.Result> response = List.copyOf(results);
                    String error = failure;
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            if (!closed && !disposed && revision == requestRevision) {
                                callback.completed(response, error);
                            }
                        }
                    });
                }
            });
        }

        @Override
        public void close() {
            requireEdt();
            closed = true;
            revision++;
        }
    }

    private void requestRefresh() {
        if (disposed || paused || listeners.isEmpty() || codes.isEmpty()) {
            return;
        }
        if (inFlight) {
            return;
        }
        pendingRefresh = false;
        inFlight = true;
        long requestGeneration = generation;
        List<String> requested = List.copyOf(codes);
        boolean selectedSource = useSina;
        publish();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                List<StockBean> response = List.of();
                LocalDateTime oldestQuote = null;
                String failure = null;
                try {
                    response = fetcher.fetch(requested, selectedSource);
                    oldestQuote = QuoteParser.validate(requested, response);
                } catch (Exception e) {
                    failure = failureMessage(e);
                }
                List<StockBean> result = response;
                LocalDateTime oldest = oldestQuote;
                String error = failure;
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        inFlight = false;
                        if (disposed) {
                            return;
                        }
                        if (!paused && !listeners.isEmpty() && requestGeneration == generation) {
                            if (error == null) {
                                quotes = List.copyOf(result);
                                status.succeed(LocalDateTime.now(ZoneId.of("Asia/Shanghai")), oldest);
                                for (StockBean quote : quotes) {
                                    alerts.accept(quote);
                                }
                            } else {
                                status.fail(error);
                            }
                        }
                        publish();
                        if (pendingRefresh) {
                            requestRefresh();
                        }
                    }
                });
            }
        });
    }

    private void scheduleNext() {
        nextRefresh = null;
        if (paused || listeners.isEmpty() || codes.isEmpty()) {
            return;
        }
        Date now = new Date();
        for (CronExpression schedule : schedules) {
            Date candidate = schedule.getNextValidTimeAfter(now);
            if (candidate != null && (nextRefresh == null || candidate.before(nextRefresh))) {
                nextRefresh = candidate;
            }
        }
    }

    private void publish() {
        for (Listener listener : new ArrayList<>(listeners)) {
            notifyListener(listener);
        }
    }

    private void notifyListener(Listener listener) {
        String description = status.describe(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        if (configError != null) {
            description = configError;
        } else if (codes.isEmpty()) {
            description = "暂无自选股";
        } else if (paused) {
            description = "已暂停 | " + description;
        } else if (inFlight) {
            description = "刷新中 | " + description;
        }
        listener.updated(quotes, description, !paused && !codes.isEmpty());
    }

    private static String failureMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("行情状态只能在 EDT 上修改");
        }
    }

    @Override
    public void dispose() {
        disposed = true;
        timer.stop();
        if (SwingUtilities.isEventDispatchThread()) {
            listeners.clear();
        } else {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    listeners.clear();
                }
            });
        }
        if (executor instanceof ThreadPoolExecutor) {
            ((ThreadPoolExecutor) executor).shutdownNow();
        }
    }
}
