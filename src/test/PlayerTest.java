/**
 * Test for Player behavior.
 */
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PlayerTest {

    @Test
    void constructor_assignsClassBaseStats() {
        Player p = new Player("Rhea", PlayerClass.MARINE, 10, 20);

        assertEquals("Rhea", p.getName());
        assertEquals(PlayerClass.MARINE, p.getPlayerClass());
        assertEquals(80, p.getMaxHealth());
        assertEquals(80, p.getCurrentHealth());
        assertEquals(12, p.getAttack());
        assertEquals(2, p.getDefense());
        assertEquals(0.92, p.getHitChance(), 0.0001);
        assertEquals(10, p.getWorldX());
        assertEquals(20, p.getWorldY());
    }

    @Test
    void gainExperience_levelsUpWhenThresholdReached() {
        Player p = new Player("Rhea", PlayerClass.MARINE, 0, 0);
        p.gainExperience(100); // exact threshold for level 1 -> 2

        assertEquals(2, p.getLevel());
        assertEquals(100, p.getMaxHealth());   // 80 + 20
        assertEquals(100, p.getCurrentHealth());
        assertEquals(15, p.getAttack());       // 12 + 3
        assertEquals(3, p.getDefense());       // 2 + 1
        assertEquals(0.94, p.getHitChance(), 0.0001);
        assertEquals(0, p.getExperience());
        assertEquals(150, p.getExpToNext());   // 100 * 1.5
    }

    @Test
    void takeDamage_neverDropsBelowZero() {
        Player p = new Player("Tank", PlayerClass.SOLDIER, 0, 0);

        p.takeDamage(10); // Soldier DEF=3 => effective 7
        assertEquals(113, p.getCurrentHealth());

        p.takeDamage(10_000);
        assertEquals(0, p.getCurrentHealth());
        assertFalse(p.isAlive());
    }

    @Test
    void healing_neverExceedsMaxHealth() {
        Player p = new Player("Medic", PlayerClass.MEDIC, 0, 0);

        p.takeDamage(20); // DEF=4 => effective 16
        assertTrue(p.getCurrentHealth() < p.getMaxHealth());

        p.heal(1_000);
        assertEquals(p.getMaxHealth(), p.getCurrentHealth());
    }
}
