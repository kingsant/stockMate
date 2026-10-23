package alert;

public class StockAlertRule {
    public Double abovePrice;
    public Double belowPrice;
    public Double risePercent;
    public Double fallPercent;
    public Double movementPoints;
    public int windowSeconds = 60;
    public int cooldownSeconds = 300;
    public boolean muted;

    public String serialize() {
        validate();
        return value(abovePrice) + ";" + value(belowPrice) + ";" + value(risePercent) + ";"
                + value(fallPercent) + ";" + value(movementPoints) + ";"
                + windowSeconds + ";" + cooldownSeconds + ";" + muted;
    }

    public static StockAlertRule parse(String value) {
        String[] fields = value.split(";", -1);
        if (fields.length != 8) {
            throw new IllegalArgumentException("提醒规则格式无效");
        }
        StockAlertRule rule = new StockAlertRule();
        rule.abovePrice = number(fields[0]);
        rule.belowPrice = number(fields[1]);
        rule.risePercent = number(fields[2]);
        rule.fallPercent = number(fields[3]);
        rule.movementPoints = number(fields[4]);
        rule.windowSeconds = Integer.parseInt(fields[5]);
        rule.cooldownSeconds = Integer.parseInt(fields[6]);
        rule.muted = Boolean.parseBoolean(fields[7]);
        rule.validate();
        return rule;
    }

    private static String value(Double number) {
        return number == null ? "" : number.toString();
    }

    private static Double number(String value) {
        return value.isBlank() ? null : Double.valueOf(value);
    }

    public void validate() {
        positive(abovePrice);
        positive(belowPrice);
        positive(risePercent);
        positive(fallPercent);
        positive(movementPoints);
        if (abovePrice != null && belowPrice != null && belowPrice >= abovePrice) {
            throw new IllegalArgumentException("低价阈值必须小于高价阈值");
        }
        if (windowSeconds < 1 || windowSeconds > 3600 || cooldownSeconds < 0 || cooldownSeconds > 86400) {
            throw new IllegalArgumentException("异动窗口为 1–3600 秒，冷却时间为 0–86400 秒");
        }
    }

    private static void positive(Double value) {
        if (value != null && (!Double.isFinite(value) || value <= 0)) {
            throw new IllegalArgumentException("阈值必须是大于零的有限数字，留空表示不启用");
        }
    }
}
