/**
 * Test for Inventory behavior.
 */
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InventoryTest {

    @Test
    void addWeapon_respectsCapacityRules() {
        Inventory inv = new Inventory(PlayerClass.SCIENTIST); // max weight 12

        assertTrue(inv.addWeapon(new RayGun())); // 10.0
        assertFalse(inv.addWeapon(new RayGun())); // would exceed capacity
        assertEquals(1, inv.getWeapons().size());
    }

    @Test
    void equipWeapon_switchesActiveIndex() {
        Inventory inv = new Inventory(PlayerClass.ENGINEER);

        assertTrue(inv.addWeapon(new Knife()));
        assertTrue(inv.addWeapon(new Sword()));
        assertEquals(0, inv.getActiveIndex()); // auto-equip first

        assertTrue(inv.equipWeapon(1));
        assertEquals(1, inv.getActiveIndex());
        assertFalse(inv.equipWeapon(99));
    }

    @Test
    void addPotion_increasesStoredPotions() {
        Inventory inv = new Inventory(PlayerClass.ENGINEER);
        Player p = new Player("Pilot", PlayerClass.PILOT, 0, 0);

        assertTrue(inv.stashPotion(new HealthPotion(0, 0)));
        assertEquals(1, inv.getPotions().size());

        String msg = inv.usePotion(p);
        assertEquals(0, inv.getPotions().size());
        assertTrue(msg.contains("Health Potion") || msg.contains("recover"));
    }

    @Test
    void ammoOperations_neverGoNegative() {
        Inventory inv = new Inventory(PlayerClass.MARINE);

        assertFalse(inv.consumeAmmo());
        assertEquals(0, inv.getAmmo().getCount());

        inv.addAmmo(1);
        assertTrue(inv.consumeAmmo());
        assertFalse(inv.consumeAmmo());
        assertEquals(0, inv.getAmmo().getCount());
        assertNull(inv.dropWeapon(0)); // no weapons added in this test
    }
}
