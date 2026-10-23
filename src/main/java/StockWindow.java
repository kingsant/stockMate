import alert.AlertManager;
import alert.StockAlertRule;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import com.jgoodies.common.base.Strings;
import common.Consts;
import handler.QuoteParser;
import handler.SinaStockHandler;
import handler.StockRefreshHandler;
import handler.TencentStockHandler;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import quartz.HandlerJob;
import quartz.QuartzManager;
import utils.HttpClientPool;
import utils.LogUtil;
import utils.PopupsUiUtil;
import utils.WindowUtils;
import watchlist.StockCodes;

import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class StockWindow {
    public static final String NAME = "sk";
    private JPanel mPanel;

    static StockRefreshHandler handler;

    static JBTable table;
    static JLabel refreshTimeLabel;

    public JPanel getmPanel() {
        return mPanel;
    }

    static {
        refreshTimeLabel = new JLabel();
        refreshTimeLabel.setToolTipText("最后刷新时间");
        refreshTimeLabel.setBorder(new EmptyBorder(0, 0, 0, 5));
        table = new JBTable();
        //记录列名的变化
        table.getTableHeader().addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                StringBuilder tableHeadChange = new StringBuilder();
                for (int i = 0; i < table.getColumnCount(); i++) {
                    tableHeadChange.append(table.getColumnName(i)).append(",");
                }
                PropertiesComponent instance = PropertiesComponent.getInstance();
                //将列名的修改放入环境中 key:stock_table_header_key
                instance.setValue(WindowUtils.STOCK_TABLE_HEADER_KEY, tableHeadChange
                        .substring(0, tableHeadChange.length() > 0 ? tableHeadChange.length() - 1 : 0));

                //LogUtil.info(instance.getValue(WindowUtils.STOCK_TABLE_HEADER_KEY));
            }

        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                int row = table.rowAtPoint(event.getPoint());
                if (row < 0 || handler == null) {
                    return;
                }
                table.setRowSelectionInterval(row, row);
                String code = String.valueOf(table.getModel().getValueAt(table.convertRowIndexToModel(row), handler.codeColumnIndex));
                if (SwingUtilities.isLeftMouseButton(event) && event.getClickCount() > 1) {
                    showChart(code, PopupsUiUtil.StockShowType.min, event);
                } else if (SwingUtilities.isRightMouseButton(event)) {
                    showMenu(code, event);
                }
            }
        });
    }

    private static void showChart(String code, PopupsUiUtil.StockShowType type, MouseEvent event) {
        try {
            PopupsUiUtil.showImageByStockCode(code, type, new Point(event.getXOnScreen(), event.getYOnScreen()));
        } catch (MalformedURLException e) {
            LogUtil.info(e.getMessage());
        }
    }

    private static void showMenu(String code, MouseEvent event) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem edit = new JMenuItem("编辑股票代码");
        edit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent action) {
                editStock(code);
            }
        });
        menu.add(edit);
        JMenuItem remove = new JMenuItem("删除自选股");
        remove.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent action) {
                if (JOptionPane.showConfirmDialog(table, "从自选中删除 " + code + "？", "删除自选股",
                        JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
                    return;
                }
                List<String> entries = loadStocks();
                for (int i = entries.size() - 1; i >= 0; i--) {
                    if (StockCodes.codeOf(entries.get(i)).equals(code)) {
                        entries.remove(i);
                    }
                }
                PropertiesComponent.getInstance().setValue("key_stocks", String.join(";\n", entries));
                AlertManager.remove(code);
                apply();
            }
        });
        menu.add(remove);
        JMenuItem rules = new JMenuItem("设置提醒规则");
        rules.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent action) {
                AlertRuleDialog.edit(table, code);
            }
        });
        menu.add(rules);
        JMenuItem mute = new JMenuItem("切换单股静音");
        mute.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent action) {
                StockAlertRule rule = AlertManager.load(code);
                if (rule == null) {
                    rule = new StockAlertRule();
                    if (PropertiesComponent.getInstance().getBoolean(Consts.KEY_ALERT)) {
                        rule.movementPoints = 2.0;
                    }
                }
                rule.muted = !rule.muted;
                AlertManager.save(code, rule);
                JOptionPane.showMessageDialog(table, code + (rule.muted ? " 已静音" : " 已取消静音"));
            }
        });
        menu.add(mute);
        menu.addSeparator();
        for (PopupsUiUtil.StockShowType type : PopupsUiUtil.StockShowType.values()) {
            JMenuItem chart = new JMenuItem(type.getDesc());
            chart.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent action) {
                    showChart(code, type, event);
                }
            });
            menu.add(chart);
        }
        menu.show(table, event.getX(), event.getY());
    }

    private static void editStock(String oldCode) {
        String input = JOptionPane.showInputDialog(table,
                "输入股票代码，例如 600519、sz000001、bj920001、hk00700", oldCode == null ? "" : oldCode);
        if (input == null) {
            return;
        }
        final String code;
        try {
            code = StockCodes.normalize(input);
            StockCodes.replace(loadStocks(), oldCode, code);
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(table, e.getMessage(), "无法保存", JOptionPane.ERROR_MESSAGE);
            return;
        }
        StockRefreshHandler editingHandler = handler;
        String originalConfig = PropertiesComponent.getInstance().getValue("key_stocks");
        refreshTimeLabel.setText("正在校验 " + code);
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            @Override
            public void run() {
                String failure = null;
                try {
                    String response = HttpClientPool.getHttpClient().get("https://qt.gtimg.cn/q=" + code);
                    HashMap<String, String[]> codes = new HashMap<>();
                    codes.put(code, new String[]{code});
                    if (QuoteParser.tencent(response, codes).isEmpty()) {
                        throw new IllegalArgumentException("未查询到该股票，请检查代码");
                    }
                } catch (Exception e) {
                    failure = "校验失败：" + e.getMessage();
                }
                String error = failure;
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        if (handler == null) {
                            return;
                        }
                        if (handler != editingHandler || !Objects.equals(originalConfig,
                                PropertiesComponent.getInstance().getValue("key_stocks"))) {
                            JOptionPane.showMessageDialog(table, "自选列表已变化，请重新操作", "无法保存", JOptionPane.WARNING_MESSAGE);
                            return;
                        }
                        if (error != null) {
                            JOptionPane.showMessageDialog(table, error, "无法保存", JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                        try {
                            List<String> entries = StockCodes.replace(loadStocks(), oldCode, code);
                            PropertiesComponent.getInstance().setValue("key_stocks", String.join(";\n", entries));
                            if (oldCode != null && !oldCode.equals(code)) {
                                AlertManager.remove(oldCode);
                            }
                            apply();
                        } catch (IllegalArgumentException e) {
                            JOptionPane.showMessageDialog(table, e.getMessage(), "无法保存", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                });
            }
        });
    }

    public StockWindow() {

        //切换接口
        if (handler != null) {
            handler.dispose();
        }
        handler = factoryHandler();

        AnActionButton refreshAction = new AnActionButton("停止刷新当前表格数据", AllIcons.Actions.Pause) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                stop();
                this.setEnabled(false);
            }
        };
        ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(table)
                .addExtraAction(new AnActionButton("添加自选股", AllIcons.General.Add) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) {
                        editStock(null);
                    }
                })
                .addExtraAction(new AnActionButton("刷新 / 重试并恢复自动刷新", AllIcons.Actions.Refresh) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) {
                        refresh();
                        refreshAction.setEnabled(true);
                    }
                })
                .addExtraAction(refreshAction)
                .setToolbarPosition(ActionToolbarPosition.TOP);
        JPanel toolPanel = toolbarDecorator.createPanel();
        toolbarDecorator.getActionsPanel().add(refreshTimeLabel, BorderLayout.EAST);
        toolPanel.setBorder(new EmptyBorder(0,0,0,0));
        mPanel.add(toolPanel, BorderLayout.CENTER);
        // 非主要tab，需要创建，创建时立即应用数据
        apply();
    }

    private static StockRefreshHandler factoryHandler() {
        if (PropertiesComponent.getInstance().getBoolean("key_stocks_sina")) {
            return new SinaStockHandler(table, refreshTimeLabel);
        }
        return new TencentStockHandler(table, refreshTimeLabel);
    }

    public static void apply() {
        if (handler != null) {
            QuartzManager.getInstance(NAME).stopJob();
            handler.dispose();
            handler = factoryHandler();
            PropertiesComponent instance = PropertiesComponent.getInstance();
            handler.setStriped(instance.getBoolean("key_table_striped"));
            handler.setupTable(loadStocks());
            refresh();
        }
    }
    public static void refresh() {
        if (handler != null) {
            PropertiesComponent instance = PropertiesComponent.getInstance();
            handler.refreshColorful(instance.getBoolean("key_colorful"));
            List<String> codes = loadStocks();
            if (CollectionUtils.isEmpty(codes)) {
                stop(); //如果没有数据则不需要启动时钟任务浪费资源
            } else {
                handler.resume();
                handler.handle(codes);
                QuartzManager quartzManager = QuartzManager.getInstance(NAME);
                HashMap<String, Object> dataMap = new HashMap<>();
                dataMap.put(HandlerJob.KEY_HANDLER, handler);
                dataMap.put(HandlerJob.KEY_CODES, codes);
                String cronExpression = instance.getValue("key_cron_expression_stock");
                if (Strings.isEmpty(cronExpression)) {
                    cronExpression = "*/10 * * * * ?";
                }
                quartzManager.runJob(HandlerJob.class, cronExpression, dataMap);
            }
        }
    }

    public static void stop() {
        QuartzManager.getInstance(NAME).stopJob();
        if (handler != null) {
            handler.stopHandle();
        }
    }

    private static List<String> loadStocks() {
        try {
            return StockCodes.parse(PropertiesComponent.getInstance().getValue("key_stocks"));
        } catch (IllegalArgumentException e) {
            refreshTimeLabel.setText("自选配置无效：" + e.getMessage());
            return new ArrayList<>();
        }
    }

    public void dispose() {
        if (!SwingUtilities.isDescendingFrom(table, mPanel)) {
            return;
        }
        stop();
        if (handler != null) {
            handler.dispose();
            handler = null;
        }
    }
}
