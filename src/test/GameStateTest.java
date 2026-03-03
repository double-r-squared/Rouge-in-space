/**
 * Test skeleton for GameState behavior.
 *
 * These are placeholders for future unit tests.
 */
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GameStateTest {

    @Test
    void log_keepsOnlyConfiguredNumberOfEntries() {
        List<String> originalLog = GameState.combatLog;
        try {
            GameState.combatLog = new ArrayList<>();

            for (int i = 0; i < GameState.LOG_SIZE + 1; i++) {
                GameState.log("entry-" + i);
            }

            assertEquals(GameState.LOG_SIZE, GameState.combatLog.size());
            assertEquals("entry-1", GameState.combatLog.get(0));
            assertEquals("entry-2", GameState.combatLog.get(1));
        } finally {
            GameState.combatLog = originalLog;
        }
    }

    @Test
    void enemyAt_returnsOnlyAliveEnemies() {
        // TODO Arrange / Act / Assert
    }

    @Test
    void itemAt_ignoresConsumedItems() {
        // TODO Arrange / Act / Assert
    }

    @Test
    void totalPlayedMillis_includesCurrentSessionTime() {
        // TODO Arrange / Act / Assert
    }
}