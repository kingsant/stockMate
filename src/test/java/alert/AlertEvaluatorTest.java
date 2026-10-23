package alert;

import org.junit.Test;
import java.time.LocalDateTime;
import static org.junit.Assert.*;

public class AlertEvaluatorTest {
    private final LocalDateTime start = LocalDateTime.of(2026, 10, 23, 10, 59, 50);

    @Test
    public void detectsMovementAcrossMinuteBoundary() {
        StockAlertRule rule = new StockAlertRule();
        rule.movementPoints = 2.0;
        AlertEvaluator evaluator = new AlertEvaluator();

        assertNull(evaluator.evaluate(rule, 10, 1, start));
        assertNotNull(evaluator.evaluate(rule, 10.3, 3.1, start.plusSeconds(20)));
    }

    @Test
    public void excludesQuotesOutsideWindowAndPreviousDay() {
        StockAlertRule rule = new StockAlertRule();
        rule.movementPoints = 2.0;
        AlertEvaluator evaluator = new AlertEvaluator();

        evaluator.evaluate(rule, 10, 1, start);

        assertNull(evaluator.evaluate(rule, 10.5, 6, start.plusSeconds(61)));
        assertNull(evaluator.evaluate(rule, 10.9, 10, start.plusDays(1)));
    }

    @Test
    public void suppressesDuplicateQuotesAndCooldownThenRearms() {
        StockAlertRule rule = new StockAlertRule();
        rule.abovePrice = 10.0;
        rule.cooldownSeconds = 30;
        AlertEvaluator evaluator = new AlertEvaluator();

        assertNotNull(evaluator.evaluate(rule, 11, 1, start));
        assertNull(evaluator.evaluate(rule, 11, 1, start));
        assertNull(evaluator.evaluate(rule, 9, 1, start.plusSeconds(1)));
        assertNull(evaluator.evaluate(rule, 11, 1, start.plusSeconds(2)));
        assertNotNull(evaluator.evaluate(rule, 11, 1, start.plusSeconds(30)));
        assertNull(evaluator.evaluate(rule, 11, 1, start.plusSeconds(90)));
    }

    @Test
    public void supportsNegativeDailyMoveAndMute() {
        StockAlertRule rule = new StockAlertRule();
        rule.fallPercent = 3.0;
        rule.muted = true;
        AlertEvaluator evaluator = new AlertEvaluator();

        assertNull(evaluator.evaluate(rule, 9, -4, start));
        rule.muted = false;
        assertNotNull(evaluator.evaluate(rule, 9, -4, start.plusSeconds(1)));
    }

    @Test
    public void rearmsDailyRulesOnNewTradingDay() {
        StockAlertRule rule = new StockAlertRule();
        rule.risePercent = 3.0;
        AlertEvaluator evaluator = new AlertEvaluator();

        assertNotNull(evaluator.evaluate(rule, 11, 4, start));
        assertNotNull(evaluator.evaluate(rule, 12, 4, start.plusDays(1)));
    }

    @Test
    public void roundTripsEveryPersistedRuleSetting() {
        StockAlertRule rule = new StockAlertRule();
        rule.abovePrice = 10.1;
        rule.belowPrice = 9.1;
        rule.risePercent = 3.0;
        rule.fallPercent = 2.0;
        rule.movementPoints = 1.5;
        rule.windowSeconds = 120;
        rule.cooldownSeconds = 600;
        rule.muted = true;

        StockAlertRule restored = StockAlertRule.parse(rule.serialize());

        assertEquals(rule.serialize(), restored.serialize());
        assertTrue(restored.muted);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonFiniteThresholds() {
        StockAlertRule rule = new StockAlertRule();
        rule.abovePrice = Double.NaN;

        rule.validate();
    }
}
