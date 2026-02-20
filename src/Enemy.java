import java.util.Random;

public abstract class Enemy {

    protected static final Random rng            = new Random();
    protected static final int    LOSE_AGGRO_TURNS = 5;

    // ── Identity ──────────────────────────────────────────────────────────────
    protected String  name;
    protected char    glyph;

    // ── Stats ─────────────────────────────────────────────────────────────────
    protected int    maxHealth;
    protected int    currentHealth;
    protected int    attackPower;
    protected double hitChance;
    protected int    sightRange;

    // ── Position ──────────────────────────────────────────────────────────────
    protected int worldX;
    protected int worldY;

    // ── AI state ──────────────────────────────────────────────────────────────
    protected boolean aggro           = false;
    protected int     turnsWithoutLOS = 0;
    protected boolean seesThroughDoors = false;

    // ─────────────────────────────────────────────────────────────────────────

    public Enemy(String name, char glyph,
                 int maxHealth, int attackPower, double hitChance,
                 int sightRange, int startX, int startY) {
        this.name          = name;
        this.glyph         = glyph;
        this.maxHealth     = maxHealth;
        this.currentHealth = maxHealth;
        this.attackPower   = attackPower;
        this.hitChance     = hitChance;
        this.sightRange    = sightRange;
        this.worldX        = startX;
        this.worldY        = startY;
    }

    // ── Health ────────────────────────────────────────────────────────────────

    public void takeDamage(int amount) {
        currentHealth = Math.max(0, currentHealth - amount);
    }

    public boolean isAlive() { return currentHealth > 0; }

    // ── Combat ────────────────────────────────────────────────────────────────

    public int attack(Player player) {
        if (rng.nextDouble() > hitChance) return 0;
        int variance = (int)(attackPower * 0.2);
        int dmg = attackPower + rng.nextInt(variance * 2 + 1) - variance;
        player.takeDamage(dmg);
        return dmg;
    }

    // ── Debuffs ───────────────────────────────────────────────────────────────

    /** Reduce sight range by amount, floored at 0. */
    public void debuffSight(int amount) {
        sightRange = Math.max(0, sightRange - amount);
    }

    /** Reduce hit chance by amount, floored at 0.0. */
    public void debuffHitChance(double amount) {
        hitChance = Math.max(0.0, hitChance - amount);
    }

    /** Hard-set hit chance to a specific value (used by Chemical blindness). */
    public void setHitChance(double value) {
        hitChance = Math.max(0.0, Math.min(1.0, value));
    }

    // ── Line of sight (Bresenham) ─────────────────────────────────────────────

    public boolean hasLineOfSight(Player player, char[][] map) {
        int x0 = worldX, y0 = worldY;
        int x1 = player.getWorldX(), y1 = player.getWorldY();

        int dist = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (dist > sightRange) return false;

        int dx  =  Math.abs(x1 - x0);
        int dy  = -Math.abs(y1 - y0);
        int sx  = x0 < x1 ? 1 : -1;
        int sy  = y0 < y1 ? 1 : -1;
        int err = dx + dy;

        int cx = x0, cy = y0;

        while (true) {
            if (cx == x1 && cy == y1) return true;

            if (!(cx == x0 && cy == y0)) {
                char tile = map[cy][cx];
                if (tile == '#') return false;
                if (tile == 'D' && !seesThroughDoors) return false;
            }

            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; cx += sx; }
            if (e2 <= dx) { err += dx; cy += sy; }
        }
    }

    // ── Aggro management ─────────────────────────────────────────────────────

    public void updateAggro(Player player, char[][] map) {
        if (!isAlive()) return;

        if (hasLineOfSight(player, map)) {
            aggro           = true;
            turnsWithoutLOS = 0;
        } else if (aggro) {
            turnsWithoutLOS++;
            if (turnsWithoutLOS >= LOSE_AGGRO_TURNS) {
                aggro           = false;
                turnsWithoutLOS = 0;
            }
        }
    }

    // ── Movement ──────────────────────────────────────────────────────────────

    public void stepToward(Player player, char[][] map,
                           java.util.List<Enemy> allEnemies) {
        if (!aggro || !isAlive()) return;

        int dx = player.getWorldX() - worldX;
        int dy = player.getWorldY() - worldY;

        if (Math.abs(dx) <= 1 && Math.abs(dy) <= 1) return;

        int stepX = Integer.signum(dx);
        int stepY = Integer.signum(dy);

        int[] tryX, tryY;
        if (Math.abs(dx) >= Math.abs(dy)) {
            tryX = new int[]{stepX, 0};
            tryY = new int[]{0,     stepY};
        } else {
            tryX = new int[]{0,     stepX};
            tryY = new int[]{stepY, 0};
        }

        for (int i = 0; i < 2; i++) {
            int nx = worldX + tryX[i];
            int ny = worldY + tryY[i];
            if (canMoveTo(nx, ny, map, allEnemies)) {
                worldX = nx;
                worldY = ny;
                return;
            }
        }
    }

    protected boolean canMoveTo(int nx, int ny, char[][] map,
                                java.util.List<Enemy> allEnemies) {
        if (nx < 0 || nx >= map[0].length || ny < 0 || ny >= map.length) return false;
        char tile = map[ny][nx];
        if (tile == ' ' || tile == '#') return false;
        for (Enemy e : allEnemies)
            if (e != this && e.isAlive() && e.worldX == nx && e.worldY == ny)
                return false;
        return true;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int     getWorldX()        { return worldX; }
    public int     getWorldY()        { return worldY; }
    public char    getGlyph()         { return glyph; }
    public String  getName()          { return name; }
    public int     getCurrentHealth() { return currentHealth; }
    public int     getMaxHealth()     { return maxHealth; }
    public boolean isAggro()          { return aggro; }
    public int     getSightRange()    { return sightRange; }

} // end Enemy


// ─────────────────────────────────────────────────────────────────────────────
// Zombie  –  'Z'  |  HP:40  ATK:8  HIT:55%  SIGHT:4
// ─────────────────────────────────────────────────────────────────────────────
class Zombie extends Enemy {
    public Zombie(int x, int y) {
        super("Zombie", 'Z', 40, 8, 0.55, 4, x, y);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mutant  –  'a'  |  HP:20  ATK:12  HIT:75%  SIGHT:7
// ─────────────────────────────────────────────────────────────────────────────
class Mutant extends Enemy {
    public Mutant(int x, int y) {
        super("Mutant", 'a', 20, 12, 0.75, 7, x, y);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Eye  –  'o'  |  HP:5  ATK:20  HIT:50%  SIGHT:20
// ─────────────────────────────────────────────────────────────────────────────
class Eye extends Enemy {
    public Eye(int x, int y) {
        super("Eye", 'o', 5, 20, 0.5, 20, x, y);
        seesThroughDoors = true;
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Skeleton  –  'S'  |  HP:15  ATK:6  HIT:90%  SIGHT:12
// ─────────────────────────────────────────────────────────────────────────────
class Snake extends Enemy {
    public Snake(int x, int y) {
        super("Snake", 'S', 15, 6, 0.90, 12, x, y);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Troll  –  'T'  |  HP:80  ATK:18  HIT:60%  SIGHT:5
// ─────────────────────────────────────────────────────────────────────────────
class Titan extends Enemy {
    public Titan(int x, int y) {
        super("Titan", 'T', 80, 18, 0.60, 5, x, y);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Ghost  –  'G'  |  HP:18  ATK:14  HIT:50%  SIGHT:9  (sees through doors)
// ─────────────────────────────────────────────────────────────────────────────
class Ghost extends Enemy {
    public Ghost(int x, int y) {
        super("Ghost", 'G', 18, 14, 0.50, 9, x, y);
        seesThroughDoors = true;
    }
}
