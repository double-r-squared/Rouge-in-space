/**
 * Test for GameState behavior.
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
        List<Enemy> original = GameState.enemies;
        try {
            GameState.enemies = new ArrayList<>();

            Enemy alive = new Enemy("Alive", 'A', 10, 1, 1.0, 3, false, 5, 6);
            Enemy dead  = new Enemy("Dead", 'D', 10, 1, 1.0, 3, false, 5, 6);
            dead.takeDamage(999);

            GameState.enemies.add(dead);
            GameState.enemies.add(alive);

            assertEquals(alive, GameState.enemyAt(5, 6));
            assertNull(GameState.enemyAt(99, 99));
        } finally {
            GameState.enemies = original;
        }
    }

    @Test
    void itemAt_ignoresConsumedItems() {
        List<Potion> original = GameState.items;
        try {
            GameState.items = new ArrayList<>();

            HealthPotion consumed = new HealthPotion(2, 3);
            consumed.consume();
            HealthPotion active = new HealthPotion(2, 3);

            GameState.items.add(consumed);
            GameState.items.add(active);

            assertEquals(active, GameState.itemAt(2, 3));
            assertNull(GameState.itemAt(7, 7));
        } finally {
            GameState.items = original;
        }
    }

    @Test
    void totalPlayedMillis_includesCurrentSessionTime() {
        long originalPreviously = GameState.previouslyPlayed;
        long originalSession = GameState.sessionStart;
        try {
            GameState.previouslyPlayed = 1_000L;
            GameState.sessionStart = System.currentTimeMillis() - 2_000L;

            long total = GameState.totalPlayedMillis();
            assertTrue(total >= 3_000L);
        } finally {
            GameState.previouslyPlayed = originalPreviously;
            GameState.sessionStart = originalSession;
        }
    }
}
