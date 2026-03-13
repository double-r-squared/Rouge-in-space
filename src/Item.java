// ─────────────────────────────────────────────────────────────────────────────
// Item  –  abstract base class for all consumable items
//
// Blueprint for any item in the game.  Subclasses must define:
//   • glyph       – the character rendered on the map  ('d' or 'b')
//   • name        – displayed in pickup / use messages
//   • use(Player) – the actual effect applied to the player
//
// Items sit on the world map without collision — the player walks over them
// freely and presses E to consume whatever is at their current tile.
//
// Each item tracks its world position and a consumed flag.  Once consumed
// it is removed from the active items list in Game.
// ─────────────────────────────────────────────────────────────────────────────
public abstract class Item {

    protected String name;
    protected char   glyph;
    protected int    worldX;
    protected int    worldY;
    protected boolean consumed = false;

    public Item(String name, char glyph, int x, int y) {
        this.name   = name;
        this.glyph  = glyph;
        this.worldX = x;
        this.worldY = y;
    }

    // ── Core contract ─────────────────────────────────────────────────────────

    /**
     * Apply this potion's effect to the player.
     * Called when the player presses E while standing on this item.
     * Returns a message string to display in the combat log.
     */
    public abstract String use(Player player);

    // ── Helpers ───────────────────────────────────────────────────────────────

    public void consume()        { consumed = true; }
    public boolean isConsumed()  { return consumed; }

    public int    getWorldX()    { return worldX; }
    public int    getWorldY()    { return worldY; }
    public char   getGlyph()     { return glyph; }
    public String getName()      { return name; }

}


// ─────────────────────────────────────────────────────────────────────────────
// HealthPotion  –  glyph 'd'
//
// Restores a fixed amount of HP when used.  The amount scales slightly with
// the player's max health so it stays useful at higher levels.
//
// Heal amount: 30% of player max HP, minimum 25.
// ─────────────────────────────────────────────────────────────────────────────
class HealthPotion extends Item {

    public HealthPotion(int x, int y) {
        super("Health Potion", 'd', x, y);
    }

    @Override
    public String use(Player player) {
        int amount = Math.max(25, (int)(player.getMaxHealth() * 0.30));
        int before = player.getCurrentHealth();
        player.heal(amount);
        int healed = player.getCurrentHealth() - before;
        consume();
        return "You drink the Health Potion and recover " + healed + " HP.  ("
                + player.getCurrentHealth() + "/" + player.getMaxHealth() + ")";
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// ArtifactPickup  –  glyph 'x'
// Picked up with E, adds artifact to the game state.
// ─────────────────────────────────────────────────────────────────────────────
class ArtifactPickup extends Item {
    public ArtifactPickup(int x, int y) {
        super("Artifact", 'x', x, y);
    }
    @Override
    public String use(Player player) {
        GameState.artifactsCollected = GameState.artifactsCollected + 1;
        consume();
        return "Picked up an Artifact. Total: " + GameState.artifactsCollected;
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// AmmePickup  –  glyph '%'
// Picked up with E; adds ammo to the player's inventory stack.
// ─────────────────────────────────────────────────────────────────────────────
class AmmoPickup extends Item {
    private final int qty;
    public AmmoPickup(int x, int y, int qty) {
        super("Ammo x" + qty, '%', x, y);
        this.qty = qty;
    }
    @Override
    public String use(Player player) {
        player.getInventory().addAmmo(qty);
        consume();
        return "Picked up " + qty + " ammo. Total: " + player.getInventory().getAmmo().getCount();
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DroppedWeapon  –  glyph is weapon specific
// A weapon lying on the floor.  Pressing E picks it up into inventory.
// ─────────────────────────────────────────────────────────────────────────────
class DroppedWeapon extends Item {
    private final Weapon weapon;
    public DroppedWeapon(int x, int y, Weapon weapon) {
        super(weapon.getWeaponClass(), weapon.getGlyph(), x, y);
        this.weapon = weapon;
    }
    @Override
    public String use(Player player) {
        if (player.getInventory().addWeapon(weapon)) {
            consume();
            return "Picked up: " + weapon.getWeaponClass() + ".";
        } else {
            return "Can't pick up " + weapon.getWeaponClass() + " — inventory too heavy!";
        }
    }

    public Weapon getWeapon() {
        return weapon; // whatever the field is named
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// VisionPotion  –  glyph 'b'
//
// Temporarily expands the player's sight radius for the remainder of the
// current level (resets on level regeneration).  Does not stack — drinking
// a second one while already active just refreshes the bonus.
//
// Vision boost: +4 tiles added to the player's sightBonus.
// ─────────────────────────────────────────────────────────────────────────────
class VisionPotion extends Item {

    static final int VISION_BOOST = 1;

    public VisionPotion(int x, int y) {
        super("Vision Potion", 'b', x, y);
    }

    @Override
    public String use(Player player) {
        player.addSightBonus(VISION_BOOST);
        consume();
        return "You drink the Vision Potion.  Your sight expands by " + VISION_BOOST + " tiles!";
    }
}