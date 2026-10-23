package alert;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class AlertEvaluator {
    private final Deque<Sample> timeline = new ArrayDeque<>();
    private LocalDateTime lastQuote;
    private LocalDateTime lastAlert;
    private final boolean[] triggered = new boolean[5];

    public String evaluate(StockAlertRule rule, double price, double percentage, LocalDateTime quoteTime) {
        if (rule.muted || !Double.isFinite(price) || !Double.isFinite(percentage) || price <= 0
                || lastQuote != null && !quoteTime.isAfter(lastQuote)) {
            return null;
        }
        if (lastQuote != null && !lastQuote.toLocalDate().equals(quoteTime.toLocalDate())) {
            timeline.clear();
            for (int i = 0; i < triggered.length; i++) {
                triggered[i] = false;
            }
        }
        lastQuote = quoteTime;
        LocalDateTime cutoff = quoteTime.minusSeconds(rule.windowSeconds);
        while (!timeline.isEmpty() && timeline.peekFirst().time.isBefore(cutoff)) {
            timeline.removeFirst();
        }
        double movement = 0;
        if (!timeline.isEmpty()) {
            movement = percentage - timeline.peekFirst().percentage;
        }
        timeline.addLast(new Sample(quoteTime, percentage));
        boolean[] matches = {
                rule.abovePrice != null && price > rule.abovePrice,
                rule.belowPrice != null && price < rule.belowPrice,
                rule.risePercent != null && percentage >= rule.risePercent,
                rule.fallPercent != null && percentage <= -rule.fallPercent,
                rule.movementPoints != null && Math.abs(movement) >= rule.movementPoints
        };
        String[] descriptions = {"价格高于 " + rule.abovePrice, "价格低于 " + rule.belowPrice,
                "日涨幅达到 " + rule.risePercent + "%", "日跌幅达到 " + rule.fallPercent + "%",
                rule.windowSeconds + " 秒内涨跌幅变化 " + String.format(java.util.Locale.ROOT, "%.2f", movement) + " 个百分点"};
        List<String> reasons = new ArrayList<>();
        boolean cooling = lastAlert != null && Duration.between(lastAlert, quoteTime).getSeconds() < rule.cooldownSeconds;
        for (int i = 0; i < matches.length; i++) {
            if (!matches[i]) {
                triggered[i] = false;
            } else if (!triggered[i] && !cooling) {
                reasons.add(descriptions[i]);
                triggered[i] = true;
            }
        }
        if (reasons.isEmpty()) {
            return null;
        }
        lastAlert = quoteTime;
        return String.join("；", reasons);
    }

    private static class Sample {
        private final LocalDateTime time;
        private final double percentage;

        private Sample(LocalDateTime time, double percentage) {
            this.time = time;
            this.percentage = percentage;
        }
    }
}
