import java.util.Random;

// ─────────────────────────────────────────────────────────────────────────────
// PlayerClass  –  the six space-themed classes
// ─────────────────────────────────────────────────────────────────────────────
enum PlayerClass {
    //                   label           hp   atk  def   hit   sight
    SOLDIER  ("Soldier",                120,  14,   3,  0.75,   0),
    MARINE   ("Marine",                  80,  12,   2,  0.92,   1),
    SCIENTIST("Scientist",               70,   7,   2,  0.70,   4),
    ENGINEER ("Engineer",               100,   9,   7,  0.72,   0),
    PILOT    ("Pilot",                   90,  11,   3,  0.85,   2),
    MEDIC    ("Medic",                  130,   8,   4,  0.73,   1);

    final String label;
    final int    hp, atk, def;
    final double hit;
    final int    sightBonus;
    final String description;

    PlayerClass(String label, int hp, int atk, int def, double hit, int sightBonus) {
        this.label      = label;
        this.hp         = hp;
        this.atk        = atk;
        this.def        = def;
        this.hit        = hit;
        this.sightBonus = sightBonus;
        this.description = String.format(
                "HP:%-4d ATK:%-3d DEF:%-3d HIT:%-4s SIGHT:+%d  CAP:%.0fkg",
                hp, atk, def, (int)(hit * 100) + "%", sightBonus,
                (double)Inventory.capacityFor_static(label));
    }

    // Static helper so the enum constructor can call it before PlayerClass is fully built
    // (Inventory.capacityFor uses a switch on label strings to avoid a circular dependency)
}

// ─────────────────────────────────────────────────────────────────────────────
// Player
// ─────────────────────────────────────────────────────────────────────────────
public class Player {

    private static final Random rng = new Random();

    // ── Identity ──────────────────────────────────────────────────────────────
    private String      name;
    private PlayerClass playerClass;

    // ── Base stats ────────────────────────────────────────────────────────────
    private int    maxHealth;
    private int    currentHealth;
    private int    attack;        // bare-hand damage (used when no weapon equipped)
    private int    defense;
    private double hitChance;     // bare-hand accuracy
    private int    sightBonus;

    // ── Progression ───────────────────────────────────────────────────────────
    private int level;
    private int experience;
    private int experienceToNextLevel;

    // ── Economy ───────────────────────────────────────────────────────────────
    private int gold;

    // ── Inventory ─────────────────────────────────────────────────────────────
    private Inventory inventory;

    // ── World position ────────────────────────────────────────────────────────
    private int worldX;
    private int worldY;

    // ─────────────────────────────────────────────────────────────────────────

    public Player(String name, PlayerClass pc, int startX, int startY) {
        this.name                  = name;
        this.playerClass           = pc;
        this.maxHealth             = pc.hp;
        this.currentHealth         = pc.hp;
        this.attack                = pc.atk;
        this.defense               = pc.def;
        this.hitChance             = pc.hit;
        this.sightBonus            = pc.sightBonus;
        this.level                 = 1;
        this.experience            = 0;
        this.experienceToNextLevel = 100;
        this.gold                  = 0;
        this.worldX                = startX;
        this.worldY                = startY;
        this.inventory             = new Inventory(pc);
    }

    // ── Movement ──────────────────────────────────────────────────────────────

    public void move(int dx, int dy) { worldX += dx; worldY += dy; }
    public int  getWorldX()          { return worldX; }
    public int  getWorldY()          { return worldY; }
    public void setWorldX(int x)     { worldX = x; }
    public void setWorldY(int y)     { worldY = y; }

    // ── Health ────────────────────────────────────────────────────────────────

    public void takeDamage(int amount) {
        int effective = Math.max(0, amount - defense);
        currentHealth = Math.max(0, currentHealth - effective);
    }

    public void heal(int amount)      { currentHealth = Math.min(maxHealth, currentHealth + amount); }
    public boolean isAlive()          { return currentHealth > 0; }
    public int     getCurrentHealth() { return currentHealth; }
    public int     getMaxHealth()     { return maxHealth; }

    // ── Combat ────────────────────────────────────────────────────────────────

    /**
     * Attack an enemy.
     *
     * If a weapon is equipped, routes through Weapon.resolveAttack() which
     * applies the weapon's damage, hit chance, and synergy bonus.
     * If unarmed, uses the player's bare-hand stats.
     *
     * Returns damage dealt (0 = miss).
     */
    public int attackEnemy(Enemy enemy) {
        Weapon w = inventory.getActiveWeapon();
        if (w != null && !(w instanceof Flashbang) && !(w instanceof Chemical)) {
            return w.resolveAttack(playerClass, enemy);
        }
        // Bare hands
        if (rng.nextDouble() > hitChance) return 0;
        int variance = (int)(attack * 0.2);
        int dmg = attack + rng.nextInt(variance * 2 + 1) - variance;
        enemy.takeDamage(dmg);
        return dmg;
    }

    // ── Levelling ─────────────────────────────────────────────────────────────

    public void gainExperience(int amount) {
        experience += amount;
        while (experience >= experienceToNextLevel) {
            experience -= experienceToNextLevel;
            levelUp();
        }
    }

    private void levelUp() {
        level++;
        maxHealth             += 20;
        currentHealth          = maxHealth;
        attack                += 3;
        defense               += 1;
        hitChance              = Math.min(0.97, hitChance + 0.02);
        experienceToNextLevel  = (int)(experienceToNextLevel * 1.5);
    }

    // ── Economy ───────────────────────────────────────────────────────────────

    public void    addGold(int amount)   { gold += amount; }
    public boolean spendGold(int amount) { if (gold >= amount) { gold -= amount; return true; } return false; }
    public int     getGold()             { return gold; }

    // ── Sight ─────────────────────────────────────────────────────────────────

    public int  getSightBonus()           { return sightBonus; }
    public void addSightBonus(int amount)  { sightBonus += amount; }
    public void resetSightBonus()          { sightBonus = playerClass.sightBonus; }

    /** Total effective sight = Game.SIGHT + sightBonus. */
    public int getTotalSight(int baseSight) { return baseSight + sightBonus; }

    // ── Inventory access ──────────────────────────────────────────────────────

    public Inventory   getInventory()   { return inventory; }
    public PlayerClass getPlayerClass() { return playerClass; }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getName()        { return name; }
    public int    getAttack()      { return attack; }
    public int    getDefense()     { return defense; }
    public double getHitChance()   { return hitChance; }
    public int    getLevel()       { return level; }
    public int    getExperience()  { return experience; }
    public int    getExpToNext()   { return experienceToNextLevel; }

    // ── HUD ───────────────────────────────────────────────────────────────────

    public String getStatusBar() {
        Weapon w = inventory.getActiveWeapon();
        String weaponLabel = (w != null) ? w.getWeaponClass() : "Bare Hands";
        String ammoLabel   = (w instanceof LaserGun)
                ? " AMO:" + inventory.getAmmo().getCount()
                : "";
        return String.format(
                " [%s] %s | HP:%d/%d | DEF:%d | LVL:%d | XP:%d/%d | WPN:%s%s | WT:%.1f/%.0f",
                playerClass.label, name,
                currentHealth, maxHealth,
                defense,
                level, experience, experienceToNextLevel,
                weaponLabel, ammoLabel,
                inventory.currentWeight(), inventory.getMaxWeight());
    }
}