package alert;

import bean.StockBean;
import com.intellij.ide.util.PropertiesComponent;
import common.Consts;
import notice.NoticeManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public final class AlertManager {
    private static final String PREFIX = "uka.alert.";
    private static final Map<String, AlertEvaluator> EVALUATORS = new HashMap<>();
    public static final DateTimeFormatter QUOTE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private AlertManager() {
    }

    public static StockAlertRule load(String code) {
        String value = PropertiesComponent.getInstance().getValue(PREFIX + code);
        if (value == null) {
            return null;
        }
        try {
            return StockAlertRule.parse(value);
        } catch (RuntimeException e) {
            StockAlertRule rule = new StockAlertRule();
            rule.muted = true;
            return rule;
        }
    }

    public static void save(String code, StockAlertRule rule) {
        PropertiesComponent.getInstance().setValue(PREFIX + code, rule.serialize());
        EVALUATORS.remove(code);
    }

    public static void remove(String code) {
        PropertiesComponent.getInstance().unsetValue(PREFIX + code);
        EVALUATORS.remove(code);
    }

    public static void recordPriceAndAlert(StockBean bean) {
        if (bean.getTime() == null || bean.getNow() == null || bean.getChangePercent() == null) {
            return;
        }
        StockAlertRule rule = load(bean.getCode());
        if (rule == null) {
            if (!PropertiesComponent.getInstance().getBoolean(Consts.KEY_ALERT)) {
                return;
            }
            rule = new StockAlertRule();
            rule.movementPoints = 2.0;
        }
        try {
            LocalDateTime quoteTime = LocalDateTime.parse(bean.getTime(), QUOTE_TIME);
            long age = Duration.between(quoteTime, LocalDateTime.now(ZoneId.of("Asia/Shanghai"))).getSeconds();
            if (age > 60 || age < -60) {
                return;
            }
            AlertEvaluator evaluator = EVALUATORS.get(bean.getCode());
            if (evaluator == null) {
                evaluator = new AlertEvaluator();
                EVALUATORS.put(bean.getCode(), evaluator);
            }
            String reason = evaluator.evaluate(rule, Double.parseDouble(bean.getNow()),
                    Double.parseDouble(bean.getChangePercent()), quoteTime);
            if (reason != null) {
                NoticeManager.notify(bean.getName() + " (" + bean.getCode() + ")", reason + "，现价 " + bean.getNow());
            }
        } catch (RuntimeException e) {
            // 无有效行情时间时不触发提醒。
        }
    }
}
