package handler;

import org.junit.Test;
import java.time.LocalDateTime;
import static org.junit.Assert.*;

public class RefreshStatusTest {
    @Test
    public void failureKeepsLastSuccessAndBecomesStale() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 23, 10, 0);
        RefreshStatus status = new RefreshStatus();
        status.succeed(now, now);

        status.fail("网络断开");

        assertTrue(status.describe(now.plusSeconds(61)).contains("数据过期"));
        assertTrue(status.describe(now.plusSeconds(61)).contains("最后成功 10-23 10:00:00"));
        assertTrue(status.describe(now.plusSeconds(61)).contains("网络断开"));
    }

    @Test
    public void successfulRequestDoesNotMakeOldQuoteFresh() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 23, 10, 0);
        RefreshStatus status = new RefreshStatus();

        status.succeed(now, now.minusDays(1));

        assertTrue(status.describe(now).contains("数据过期"));
        assertTrue(status.describe(now).contains("行情截至 10-22"));
        status.succeed(now, now);
        assertFalse(status.describe(now).contains("数据过期"));
        assertFalse(status.describe(now).contains("刷新失败"));
    }
}
