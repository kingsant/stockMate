package handler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class RefreshStatus {
    private LocalDateTime lastSuccess;
    private LocalDateTime quoteTime;
    private String error;

    public void succeed(LocalDateTime now, LocalDateTime oldestQuote) {
        lastSuccess = now;
        quoteTime = oldestQuote;
        error = null;
    }

    public void fail(String reason) {
        error = reason;
    }

    public String describe(LocalDateTime now) {
        String text = lastSuccess == null ? "尚未成功刷新"
                : "最后成功 " + lastSuccess.format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"));
        if (lastSuccess != null && (Duration.between(lastSuccess, now).getSeconds() > 60
                || quoteTime == null || Duration.between(quoteTime, now).getSeconds() > 60)) {
            text = "数据过期 | " + text;
        }
        if (quoteTime != null) {
            text += " | 行情截至 " + quoteTime.format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"));
        }
        if (error != null) {
            text = "刷新失败：" + error + " | " + text;
        }
        return text;
    }
}
