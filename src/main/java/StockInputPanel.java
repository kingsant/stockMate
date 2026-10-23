import alert.AlertManager;
import com.intellij.ide.util.PropertiesComponent;
import handler.StockRefreshService;
import watchlist.StockCodes;
import watchlist.StockSearch;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class StockInputPanel extends JPanel implements AutoCloseable {
    private final StockRefreshService service;
    private final String oldCode;
    private final StockRefreshService.SearchSession searchSession;
    private final JTextField search = new JTextField();
    private final DefaultListModel<StockSearch.Result> matches = new DefaultListModel<>();
    private final JList<StockSearch.Result> results = new JList<>(matches);
    private final JTextArea input = new JTextArea(6, 36);
    private final JLabel message = new JLabel(" ");
    private final JButton save = new JButton("保存");
    private final Timer debounce;
    private JDialog dialog;
    private boolean closed;
    private boolean saving;

    public StockInputPanel(StockRefreshService service, String oldCode) {
        super(new BorderLayout(8, 8));
        this.service = service;
        this.oldCode = oldCode;
        searchSession = service.newSearchSession();
        setBorder(new EmptyBorder(12, 12, 12, 12));
        search.setName("search");
        results.setName("results");
        input.setName("codes");
        message.setName("message");
        save.setName("save");
        input.setText(oldCode == null ? "" : oldCode);
        results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        results.setVisibleRowCount(6);
        JScrollPane suggestions = new JScrollPane(results);
        suggestions.setPreferredSize(new Dimension(450, 130));
        JPanel lookup = new JPanel(new BorderLayout(4, 4));
        lookup.add(new JLabel("代码 / 名称 / 拼音前缀（如 gzmt）"), BorderLayout.NORTH);
        lookup.add(search, BorderLayout.CENTER);
        lookup.add(suggestions, BorderLayout.SOUTH);
        add(lookup, BorderLayout.NORTH);
        JPanel batch = new JPanel(new BorderLayout(4, 4));
        batch.add(new JLabel(oldCode == null ? "一行一个代码，最多 100 只；搜索结果按回车或双击加入" : "编辑单个代码；搜索结果按回车或双击替换"), BorderLayout.NORTH);
        batch.add(new JScrollPane(input), BorderLayout.CENTER);
        add(batch, BorderLayout.CENTER);
        JButton cancel = new JButton("取消");
        cancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                close();
            }
        });
        save.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                saveInput();
            }
        });
        JPanel buttons = new JPanel();
        buttons.add(save);
        buttons.add(cancel);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(message, BorderLayout.NORTH);
        bottom.add(buttons, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
        debounce = new Timer(300, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                searchSession.search(search.getText(), new StockRefreshService.SearchCallback() {
                    @Override
                    public void completed(List<StockSearch.Result> found, String error) {
                        if (closed || saving) {
                            return;
                        }
                        matches.clear();
                        for (StockSearch.Result result : found) {
                            matches.addElement(result);
                        }
                        if (!matches.isEmpty()) {
                            results.setSelectedIndex(0);
                        }
                        message.setText(error != null ? error : found.isEmpty() ? "没有匹配结果" : "回车或双击选择");
                    }
                });
            }
        });
        debounce.setRepeats(false);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                queryChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                queryChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                queryChanged();
            }
        });
        search.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                addSelection();
            }
        });
        search.getInputMap().put(KeyStroke.getKeyStroke("DOWN"), "select-result");
        search.getActionMap().put("select-result", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (!matches.isEmpty()) {
                    results.requestFocusInWindow();
                    results.setSelectedIndex(0);
                }
            }
        });
        results.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "add-result");
        results.getActionMap().put("add-result", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                addSelection();
            }
        });
        results.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                int index = results.locationToIndex(event.getPoint());
                if (event.getClickCount() == 2 && index >= 0
                        && results.getCellBounds(index, index).contains(event.getPoint())) {
                    addSelection();
                }
            }
        });
    }

    public void open(Component parent) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        dialog = new JDialog(owner, oldCode == null ? "添加自选股" : "编辑自选股", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setContentPane(this);
        dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("ESCAPE"), "cancel");
        dialog.getRootPane().getActionMap().put("cancel", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                close();
            }
        });
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                close();
            }
        });
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        try {
            dialog.setVisible(true);
        } finally {
            close();
        }
    }

    private void queryChanged() {
        searchSession.invalidate();
        debounce.stop();
        matches.clear();
        if (!closed && !saving && !search.getText().isBlank()) {
            message.setText("搜索中…");
            debounce.restart();
        } else {
            message.setText(" ");
        }
    }

    private void addSelection() {
        StockSearch.Result selected = results.getSelectedValue();
        if (selected == null || closed || saving) {
            return;
        }
        if (oldCode != null) {
            input.setText(selected.code());
            return;
        }
        for (String line : input.getText().split("\\R", -1)) {
            try {
                if (StockCodes.normalize(line).equals(selected.code())) {
                    message.setText("已在输入列表中");
                    return;
                }
            } catch (IllegalArgumentException e) {
                continue;
            }
        }
        String text = input.getText().stripTrailing();
        input.setText(text.isEmpty() ? selected.code() : text + "\n" + selected.code());
        message.setText("已加入输入列表");
    }

    private void saveInput() {
        if (closed || saving) {
            return;
        }
        final List<String> requested;
        final List<String> merged;
        final String originalConfig = PropertiesComponent.getInstance().getValue("key_stocks");
        try {
            List<String> entered = StockCodes.parseLines(input.getText());
            List<String> existing = StockCodes.parse(originalConfig);
            if (oldCode != null) {
                if (entered.size() != 1) {
                    throw new IllegalArgumentException("编辑时只能输入一个股票代码");
                }
                requested = entered;
                merged = StockCodes.replace(existing, oldCode, entered.get(0));
            } else {
                Set<String> existingCodes = new HashSet<>();
                for (String entry : existing) {
                    existingCodes.add(StockCodes.codeOf(entry));
                }
                requested = new ArrayList<>();
                for (String code : entered) {
                    if (!existingCodes.contains(code)) {
                        requested.add(code);
                    }
                }
                if (requested.isEmpty()) {
                    throw new IllegalArgumentException("输入的股票均已在自选列表中");
                }
                merged = StockCodes.append(existing, requested);
            }
        } catch (IllegalArgumentException e) {
            message.setText(e.getMessage());
            return;
        }
        long generation = service.getGeneration();
        setSaving(true);
        message.setText("正在校验 " + requested.size() + " 只股票…");
        service.validate(requested, new StockRefreshService.ValidationCallback() {
            @Override
            public void completed(String error) {
                if (closed) {
                    return;
                }
                setSaving(false);
                if (service.getGeneration() != generation || !Objects.equals(originalConfig,
                        PropertiesComponent.getInstance().getValue("key_stocks"))) {
                    message.setText("自选列表或设置已变化，请重新保存");
                    return;
                }
                if (error != null) {
                    message.setText(error);
                    return;
                }
                PropertiesComponent.getInstance().setValue("key_stocks", String.join(";\n", merged));
                if (oldCode != null && !oldCode.equals(requested.get(0))) {
                    AlertManager.remove(oldCode);
                }
                service.reload();
                close();
            }
        });
    }

    private void setSaving(boolean value) {
        saving = value;
        save.setEnabled(!value);
        input.setEnabled(!value);
        search.setEnabled(!value);
        results.setEnabled(!value);
        if (value) {
            debounce.stop();
            searchSession.invalidate();
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            debounce.stop();
            searchSession.close();
            if (dialog != null) {
                dialog.dispose();
            }
        }
    }
}
