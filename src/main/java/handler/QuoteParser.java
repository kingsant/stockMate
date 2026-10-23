package handler;

import alert.AlertManager;
import bean.StockBean;
import com.jgoodies.common.base.Strings;
import org.apache.commons.lang3.StringUtils;
import watchlist.StockCodes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QuoteParser {
    private static final Pattern STOCK_PATTERN = Pattern.compile("var hq_str_(\\w+?)=\"(.*?)\";");

    private QuoteParser() {
    }

    public static LocalDateTime validate(List<String> entries, List<StockBean> quotes) {
        Set<String> expected = new LinkedHashSet<>();
        for (String entry : entries) {
            expected.add(StockCodes.codeOf(entry));
        }
        LocalDateTime oldest = null;
        for (StockBean quote : quotes) {
            if (!expected.remove(quote.getCode())) {
                throw new IllegalArgumentException("行情包含重复或非自选股票");
            }
            LocalDateTime time = LocalDateTime.parse(quote.getTime(), AlertManager.QUOTE_TIME);
            if (oldest == null || time.isBefore(oldest)) {
                oldest = time;
            }
        }
        if (!expected.isEmpty() || oldest == null) {
            throw new IllegalArgumentException("以下代码无行情：" + String.join("、", expected) + "，请检查代码或切换数据源");
        }
        return oldest;
    }

    public static List<StockBean> tencent(String result, Map<String, String[]> codeMap) {
        List<StockBean> quotes = new ArrayList<>();
        String[] lines = result.split("\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (!line.contains("=") || !line.contains("~")) {
                continue;
            }
            String code = StockCodes.normalize(line.substring(line.indexOf("_") + 1, line.indexOf("=")));
            String dataStr = line.substring(line.indexOf("=") + 2, line.length() - 2);
            String[] values = dataStr.split("~");
            if (values.length < 35 || !codeMap.containsKey(code) || values[1].isBlank()) {
                continue;
            }
            StockBean bean = new StockBean(code, codeMap);
            bean.setName(values[1]);
            bean.setNow(values[3]);
            bean.setChange(values[31]);
            bean.setChangePercent(values[32]);
            String timestamp = values[30].trim();
            LocalDateTime quoteTime;
            if (timestamp.contains("/")) {
                quoteTime = LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"));
            } else if (timestamp.contains("-")) {
                quoteTime = LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } else {
                quoteTime = LocalDateTime.parse(timestamp, AlertManager.QUOTE_TIME);
            }
            if (code.startsWith("us")) {
                quoteTime = quoteTime.atZone(ZoneId.of("America/New_York"))
                        .withZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDateTime();
            }
            bean.setTime(quoteTime.format(AlertManager.QUOTE_TIME));
            bean.setMax(values[33]);
            bean.setMin(values[34]);

            BigDecimal now = new BigDecimal(values[3]);
            String costPriceStr = bean.getCostPrise();
            if (Strings.isNotEmpty(costPriceStr)) {
                BigDecimal costPriceDec = new BigDecimal(costPriceStr);
                BigDecimal incomeDiff = now.add(costPriceDec.negate());
                if (costPriceDec.compareTo(BigDecimal.ZERO) <= 0) {
                    bean.setIncomePercent("0");
                } else {
                    BigDecimal incomePercentDec = incomeDiff.divide(costPriceDec, 5, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.TEN)
                            .multiply(BigDecimal.TEN)
                            .setScale(3, RoundingMode.HALF_UP);
                    bean.setIncomePercent(incomePercentDec.toString());
                }

                String bondStr = bean.getBonds();
                if (Strings.isNotEmpty(bondStr)) {
                    BigDecimal bondDec = new BigDecimal(bondStr);
                    BigDecimal incomeDec = incomeDiff.multiply(bondDec)
                            .setScale(2, RoundingMode.HALF_UP);
                    bean.setIncome(incomeDec.toString());
                }
            }

            if (!Double.isFinite(Double.parseDouble(bean.getNow()))
                    || !Double.isFinite(Double.parseDouble(bean.getChangePercent()))) {
                throw new IllegalArgumentException("行情价格或涨跌幅无效");
            }
            quotes.add(bean);
        }
        return quotes;
    }

    public static List<StockBean> sina(String response, Map<String, String[]> codeMap) {
        List<StockBean> quotes = new ArrayList<>();
        for (String line : response.split("\n")) {
            Matcher matcher = STOCK_PATTERN.matcher(line.trim());
            if (!matcher.matches()) {
                continue;
            }
            String code = matcher.group(1);
            String[] split = matcher.group(2).split(",");
            if (split.length < 32 || !codeMap.containsKey(code) || split[0].isBlank()) {
                continue;
            }
            StockBean bean = new StockBean(code, codeMap);
            bean.setName(split[0]);
            BigDecimal now = new BigDecimal(split[3]);
            BigDecimal yesterday = new BigDecimal(split[2]);
            BigDecimal diff = now.add(yesterday.negate());

            bean.setNow(now.toString());
            bean.setChange(diff.toString());
            BigDecimal percent = yesterday.signum() == 0 ? BigDecimal.ZERO : diff.divide(yesterday, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.TEN)
                    .multiply(BigDecimal.TEN)
                    .setScale(2, RoundingMode.HALF_UP);
            bean.setChangePercent(percent.toString());
            String quoteTime = split[30].replace("-", "") + split[31].replace(":", "");
            LocalDateTime.parse(quoteTime, AlertManager.QUOTE_TIME);
            bean.setTime(quoteTime);
            bean.setMax(split[4]);
            bean.setMin(split[5]);

            String costPriceStr = bean.getCostPrise();
            if (StringUtils.isNotBlank(costPriceStr)) {
                BigDecimal costPriceDec = new BigDecimal(costPriceStr);
                BigDecimal incomeDiff = now.add(costPriceDec.negate());
                BigDecimal incomePercentDec = incomeDiff.divide(costPriceDec, 5, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.TEN)
                        .multiply(BigDecimal.TEN)
                        .setScale(3, RoundingMode.HALF_UP);
                bean.setIncomePercent(incomePercentDec.toString());

                String bondStr = bean.getBonds();
                if (StringUtils.isNotBlank(bondStr)) {
                    BigDecimal bondDec = new BigDecimal(bondStr);
                    BigDecimal incomeDec = incomeDiff.multiply(bondDec)
                            .setScale(2, RoundingMode.HALF_UP);
                    bean.setIncome(incomeDec.toString());
                }
            }

            if (!Double.isFinite(Double.parseDouble(bean.getNow()))
                    || !Double.isFinite(Double.parseDouble(bean.getChangePercent()))) {
                throw new IllegalArgumentException("行情价格或涨跌幅无效");
            }
            quotes.add(bean);
        }
        return quotes;
    }
}
