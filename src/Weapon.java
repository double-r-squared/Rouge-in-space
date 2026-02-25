import java.util.List;
import java.util.Random;

// ─────────────────────────────────────────────────────────────────────────────
// WeaponType  –  the four categories of weapon
//
// Each type defines which player classes receive a synergy bonus (+15% hit
// chance) and what the base range is.  RANGED is special — its range equals
// the player's current sight stat at fire time, passed in dynamically.
// ─────────────────────────────────────────────────────────────────────────────
enum WeaponType {
    MELEE     ("Melee",     1,  new PlayerClass[]{PlayerClass.MARINE,    PlayerClass.SOLDIER}),
    THROWABLE ("Throwable", 1,  new PlayerClass[]{PlayerClass.ENGINEER,  PlayerClass.PILOT}),
    SCIENCE   ("Science",   1,  new PlayerClass[]{PlayerClass.SCIENTIST, PlayerClass.MEDIC}),
    RANGED    ("Ranged",    -1, new PlayerClass[]{PlayerClass.MARINE,    PlayerClass.SOLDIER, PlayerClass.PILOT});
    // RANGED range = -1 sentinel — Game uses player sight at fire time instead

    final String        label;
    final int           baseRange;      // -1 = dynamic (sight-based)
    final PlayerClass[] synergyClasses;

    WeaponType(String label, int baseRange, PlayerClass[] synergyClasses) {
        this.label          = label;
        this.baseRange      = baseRange;
        this.synergyClasses = synergyClasses;
    }

    /** Returns true if the given class gets a synergy bonus with this weapon type. */
    public boolean hasSynergy(PlayerClass pc) {
        for (PlayerClass c : synergyClasses) if (c == pc) return true;
        return false;
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Weapon  –  abstract base class
//
// All weapons share: damage, hitChance, weight, range, type, weaponClass name.
// Consumable weapons (Flashbang, Chemical) override isConsumable() → true and
// are removed from the inventory after a single use.
//
// Synergy bonus: if the player's class matches the weapon type's synergyClasses,
// effective hit chance is boosted by SYNERGY_BONUS when resolving attacks.
// ─────────────────────────────────────────────────────────────────────────────
public abstract class Weapon {

    protected static final Random rng          = new Random();
    protected static final double SYNERGY_BONUS = 0.15;

    // ── Core stats ────────────────────────────────────────────────────────────
    protected String     weaponClass;   // e.g. "Knife", "Laser Gun"
    protected char       glyph;
    protected int        damage;
    protected double     hitChance;
    protected double     weight;
    protected WeaponType type;
    protected int        range;         // tiles; RANGED weapons override dynamically

    public Weapon(String weaponClass, char glyph, int damage, double hitChance,
                  double weight, WeaponType type, int range) {
        this.weaponClass = weaponClass;
        this.glyph       = glyph;
        this.damage      = damage;
        this.hitChance   = hitChance;
        this.weight      = weight;
        this.type        = type;
        this.range       = range;
    }

    // ── Overrides ─────────────────────────────────────────────────────────────

    /** Consumable weapons return true; they are removed after use. */
    public boolean isConsumable() { return false; }

    /**
     * Resolve this weapon's attack against a target.
     *
     * @param playerClass  used to calculate synergy bonus
     * @param target       the enemy being attacked
     * @return             damage dealt (0 on miss)
     */
    public int resolveAttack(PlayerClass playerClass, Enemy target) {
        double effective = effectiveHitChance(playerClass);
        if (rng.nextDouble() > effective) return 0;
        int variance = (int)(damage * 0.2);
        int dmg = damage + rng.nextInt(variance * 2 + 1) - variance;
        target.takeDamage(dmg);
        return dmg;
    }

    /** Hit chance + synergy bonus if applicable. */
    public double effectiveHitChance(PlayerClass pc) {
        return type.hasSynergy(pc) ? Math.min(0.99, hitChance + SYNERGY_BONUS) : hitChance;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String     getWeaponClass() { return weaponClass; }
    public char       getGlyph()       { return glyph; }
    public int        getDamage()      { return damage; }
    public double     getHitChance()   { return hitChance; }
    public double     getWeight()      { return weight; }
    public WeaponType getType()        { return type; }
    public int        getRange()       { return range; }

    /** One-line description for inventory display. */
    public String getDisplayName() {
        return String.format("%s [%s] DMG:%d HIT:%d%% WT:%.1f RNG:%s",
                weaponClass, type.label, damage, (int)(hitChance * 100), weight,
                range == -1 ? "sight" : String.valueOf(range));
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// KNIFE  –  basic melee weapon
// Modest damage, modest accuracy.  The starter fallback weapon.
// ─────────────────────────────────────────────────────────────────────────────
class Knife extends Weapon {
    public Knife() {
        super("Knife", '1', 8, 0.80, 1.0, WeaponType.MELEE, 1);
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// SWORD  –  upgraded melee weapon
// More damage than a knife, slightly heavier.
// ─────────────────────────────────────────────────────────────────────────────
class Sword extends Weapon {
    public Sword() {
        super("Sword", 't' ,16, 0.75, 3.0, WeaponType.MELEE, 1);
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// FLASHBANG  –  throwable, consumable
//
// On use: debuffs the target enemy's hit chance and sight range.
// Misuse condition: player is Scientist or Medic, OR no adjacent enemy exists.
//   → In that case the player debuffs themselves instead.
//
// Sight reduction: random int in [1 .. enemy's current sightRange].
// Hit chance reduction: flat -30%.
// ─────────────────────────────────────────────────────────────────────────────
class Flashbang extends Weapon {

    private static final double HIT_DEBUFF    = 0.30;

    public Flashbang() {
        super("Flashbang",'i',0,1.0,0.5, WeaponType.THROWABLE, 1);
    }

    @Override public boolean isConsumable() { return true; }

    /**
     * Use the flashbang.
     *
     * @param playerClass   for misuse check
     * @param adjEnemies    all living enemies adjacent to the player (range 1)
     * @param player        needed for self-debuff path
     * @return              result message
     */
    public String use(PlayerClass playerClass, List<Enemy> adjEnemies, Player player) {
        boolean wrongClass = (playerClass == PlayerClass.SCIENTIST
                || playerClass == PlayerClass.MEDIC);
        boolean noTarget   = adjEnemies.isEmpty();

        if (wrongClass || noTarget) {
            // Self-debuff
            int sightHit = 1 + rng.nextInt(Math.max(1, player.getSightBonus() + 1));
            player.addSightBonus(-sightHit);
            return "The flashbang goes wrong! You lose " + sightHit + " sight.";
        }

        // Hit the first adjacent enemy
        Enemy target = adjEnemies.get(0);
        int sightHit = 1 + rng.nextInt(Math.max(1, target.getSightRange()));
        target.debuffSight(sightHit);
        target.debuffHitChance(HIT_DEBUFF);
        return "Flashbang! " + target.getName() + " loses " + sightHit
                + " sight and -30% accuracy.";
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// LASER GUN  –  ranged weapon
//
// Range = player's current sight stat (passed in at fire time).
// Requires Ammo in inventory.  Attacks the closest enemy in LOS.
// Soldiers and Marines equip it for free (no tick cost — handled in Game).
// ─────────────────────────────────────────────────────────────────────────────
class LaserGun extends Weapon {
    public LaserGun() {
        // range = -1 sentinel; Game substitutes player sight at fire time
        super("Laser Gun", 'L', 20, 0.78, 4.0, WeaponType.RANGED, 12);
    }
}

class RayGun extends Weapon {
    public RayGun() {
       super("Ray Gun", 'F', 40, 0.25, 10.0, WeaponType.RANGED, -1);
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// AMMO  –  stackable inventory item (weight 0)
//
// Not a Weapon subclass — stored separately in inventory as a stackable count.
// Required to fire the LaserGun.
// ─────────────────────────────────────────────────────────────────────────────
class Ammo {
    private int count;

    public Ammo(int count) { this.count = count; }

    public boolean consume()   { if (count <= 0) return false; count--; return true; }
    public void    add(int n)  { count += n; }
    public int     getCount()  { return count; }
    public boolean hasAmmo()   { return count > 0; }

    public String getDisplayName() { return "Ammo x" + count + " [WT:0]"; }
}
