import alert.AlertManager;
import alert.StockAlertRule;
import bean.StockBean;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ui.table.JBTable;
import common.Consts;
import handler.StockRefreshService;
import handler.StockTableModel;
import utils.LogUtil;
import utils.PopupsUiUtil;
import utils.WindowUtils;
import watchlist.StockCodes;

import java.awt.BorderLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Objects;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

public class StockWindow implements StockRefreshService.Listener {
    public static final String NAME = "sMate";
    private final JPanel mPanel = new JPanel(new BorderLayout());
    private final StockRefreshService service;
    private final StockTableModel model;
    private final JBTable table = new JBTable();
    private final JLabel refreshTimeLabel = new JLabel();
    private final JButton pauseAction = new JButton(AllIcons.Actions.Pause);
    private List<StockBean> displayedQuotes;
    private boolean disposed;

    public JPanel getmPanel() {
        return mPanel;
    }

    private void configureTable() {
        refreshTimeLabel.setBorder(new EmptyBorder(0, 0, 0, 5));
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
                if (row < 0 || disposed) {
                    return;
                }
                table.setRowSelectionInterval(row, row);
                String code = String.valueOf(table.getModel().getValueAt(table.convertRowIndexToModel(row), model.codeColumnIndex));
                if (SwingUtilities.isLeftMouseButton(event) && event.getClickCount() > 1) {
                    showChart(code, PopupsUiUtil.StockShowType.min, event);
                } else if (SwingUtilities.isRightMouseButton(event)) {
                    showMenu(code, event);
                }
            }
        });
    }

    private void showChart(String code, PopupsUiUtil.StockShowType type, MouseEvent event) {
        try {
            PopupsUiUtil.showImageByStockCode(code, type, new Point(event.getXOnScreen(), event.getYOnScreen()));
        } catch (MalformedURLException e) {
            LogUtil.info(e.getMessage());
        }
    }

    private void showMenu(String code, MouseEvent event) {
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
                List<String> entries;
                try {
                    entries = StockCodes.parse(PropertiesComponent.getInstance().getValue("key_stocks"));
                } catch (IllegalArgumentException e) {
                    JOptionPane.showMessageDialog(table, e.getMessage(), "无法删除", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                for (int i = entries.size() - 1; i >= 0; i--) {
                    if (StockCodes.codeOf(entries.get(i)).equals(code)) {
                        entries.remove(i);
                    }
                }
                PropertiesComponent.getInstance().setValue("key_stocks", String.join(";\n", entries));
                AlertManager.remove(code);
                service.reload();
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

    private void editStock(String oldCode) {
        String input = JOptionPane.showInputDialog(table,
                "输入股票代码，例如 600519、sz000001、bj920001、hk00700", oldCode == null ? "" : oldCode);
        if (input == null) {
            return;
        }
        final String code;
        try {
            code = StockCodes.normalize(input);
            StockCodes.replace(StockCodes.parse(PropertiesComponent.getInstance().getValue("key_stocks")), oldCode, code);
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(table, e.getMessage(), "无法保存", JOptionPane.ERROR_MESSAGE);
            return;
        }
        long editGeneration = service.getGeneration();
        String originalConfig = PropertiesComponent.getInstance().getValue("key_stocks");
        service.validate(code, new StockRefreshService.ValidationCallback() {
            @Override
            public void completed(String error) {
                if (disposed) {
                    return;
                }
                if (service.getGeneration() != editGeneration || !Objects.equals(originalConfig,
                        PropertiesComponent.getInstance().getValue("key_stocks"))) {
                    JOptionPane.showMessageDialog(table, "自选列表已变化，请重新操作", "无法保存", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (error != null) {
                    JOptionPane.showMessageDialog(table, error, "无法保存", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                try {
                    List<String> entries = StockCodes.replace(
                            StockCodes.parse(PropertiesComponent.getInstance().getValue("key_stocks")), oldCode, code);
                    PropertiesComponent.getInstance().setValue("key_stocks", String.join(";\n", entries));
                    if (oldCode != null && !oldCode.equals(code)) {
                        AlertManager.remove(oldCode);
                    }
                    service.reload();
                } catch (IllegalArgumentException e) {
                    JOptionPane.showMessageDialog(table, e.getMessage(), "无法保存", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }

    public StockWindow() {
        this(StockRefreshService.getInstance());
    }

    StockWindow(StockRefreshService service) {
        this.service = service;
        model = new StockTableModel(table);
        configureTable();
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        JButton add = new JButton(AllIcons.General.Add);
        add.setToolTipText("添加自选股");
        add.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                editStock(null);
            }
        });
        toolbar.add(add);
        JButton refresh = new JButton(AllIcons.Actions.Refresh);
        refresh.setToolTipText("刷新 / 重试并恢复所有窗口自动刷新");
        refresh.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                service.refresh();
            }
        });
        toolbar.add(refresh);
        pauseAction.setToolTipText("暂停所有窗口自动刷新");
        pauseAction.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                service.pause();
            }
        });
        toolbar.add(pauseAction);
        JPanel top = new JPanel(new BorderLayout());
        top.add(toolbar, BorderLayout.WEST);
        top.add(refreshTimeLabel, BorderLayout.CENTER);
        mPanel.add(top, BorderLayout.NORTH);
        mPanel.add(new JScrollPane(table), BorderLayout.CENTER);
        service.subscribe(this);
    }

    @Override
    public void updated(List<StockBean> quotes, String status, boolean running) {
        PropertiesComponent properties = PropertiesComponent.getInstance();
        model.setStriped(properties.getBoolean("key_table_striped"));
        model.refreshColorful(properties.getBoolean("key_colorful"));
        if (displayedQuotes != quotes) {
            displayedQuotes = quotes;
            model.showQuotes(quotes);
        }
        String oldestTime = null;
        for (StockBean quote : quotes) {
            String time = quote.getTime();
            if (time != null && time.length() == 14 && (oldestTime == null || time.compareTo(oldestTime) < 0)) {
                oldestTime = time;
            }
        }
        String timeText = "--:--:--";
        if (oldestTime != null) {
            timeText = oldestTime.substring(8, 10) + ":" + oldestTime.substring(10, 12) + ":" + oldestTime.substring(12, 14);
        }
        refreshTimeLabel.setText(timeText);
        pauseAction.setEnabled(running);
    }

    public static void apply() {
        StockRefreshService.getInstance().reload();
    }

    public void dispose() {
        if (!disposed) {
            disposed = true;
            service.unsubscribe(this);
        }
    }
}
