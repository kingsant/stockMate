package quartz;

import org.quartz.CronExpression;

import java.text.ParseException;

public final class QuartzManager {
    private QuartzManager() {
    }

    public static boolean checkCronExpression(String expression) {
        try {
            new CronExpression(expression);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }
}
