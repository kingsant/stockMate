import alert.AlertManager;
import alert.StockAlertRule;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.GridLayout;

public final class AlertRuleDialog {
    private AlertRuleDialog() {
    }

    public static void edit(Component parent, String code) {
        StockAlertRule saved = AlertManager.load(code);
        if (saved == null) {
            saved = new StockAlertRule();
        }
        JPanel panel = new JPanel(new GridLayout(0, 2, 8, 8));
        JTextField above = field(panel, "价格高于", saved.abovePrice);
        JTextField below = field(panel, "价格低于", saved.belowPrice);
        JTextField rise = field(panel, "日涨幅达到（%，填正数）", saved.risePercent);
        JTextField fall = field(panel, "日跌幅达到（%，填正数）", saved.fallPercent);
        JTextField movement = field(panel, "短时变化达到（百分点）", saved.movementPoints);
        JTextField window = field(panel, "短时窗口（秒）", saved.windowSeconds);
        JTextField cooldown = field(panel, "单股冷却（秒）", saved.cooldownSeconds);
        JCheckBox muted = new JCheckBox("该股票静音", saved.muted);
        panel.add(muted);
        panel.add(new JLabel("阈值留空即关闭；单股规则独立于全局开关"));
        while (JOptionPane.showConfirmDialog(parent, panel, code + " 提醒规则",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                StockAlertRule rule = new StockAlertRule();
                rule.abovePrice = number(above);
                rule.belowPrice = number(below);
                rule.risePercent = number(rise);
                rule.fallPercent = number(fall);
                rule.movementPoints = number(movement);
                rule.windowSeconds = Integer.parseInt(window.getText().trim());
                rule.cooldownSeconds = Integer.parseInt(cooldown.getText().trim());
                rule.muted = muted.isSelected();
                AlertManager.save(code, rule);
                return;
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(parent, "请检查输入：" + e.getMessage(), "无法保存", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static JTextField field(JPanel panel, String label, Number value) {
        JTextField field = new JTextField(value == null ? "" : value.toString(), 12);
        panel.add(new JLabel(label));
        panel.add(field);
        return field;
    }

    private static Double number(JTextField field) {
        String text = field.getText().trim();
        return text.isEmpty() ? null : Double.valueOf(text);
    }
}
