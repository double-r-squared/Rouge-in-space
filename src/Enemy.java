import java.util.Random;

// ─────────────────────────────────────────────────────────────────────────────
// Enemy  –  a single concrete class for all monster types
//
// Monster stats come from EnemyFactory, which reads them from monsters.db.
// There are no subclasses — all variation is data-driven.
//
// To add a new monster type, insert a row into the monsters table.
// No Java changes needed.
// ─────────────────────────────────────────────────────────────────────────────
public class Enemy {

    protected static final Random rng             = new Random();
    protected static final int    LOSE_AGGRO_TURNS = 5;

    protected String  name;
    protected char    glyph;
    protected int     maxHealth;
    protected int     currentHealth;
    protected int     attackPower;
    protected double  hitChance;
    protected int     sightRange;
    protected int     worldX;
    protected int     worldY;
    protected boolean aggro            = false;
    protected int     turnsWithoutLOS  = 0;
    protected boolean seesThroughDoors = false;

    public Enemy(String name, char glyph,
                 int maxHealth, int attackPower, double hitChance,
                 int sightRange, boolean seesThroughDoors,
                 int startX, int startY) {
        this.name             = name;
        this.glyph            = glyph;
        this.maxHealth        = maxHealth;
        this.currentHealth    = maxHealth;
        this.attackPower      = attackPower;
        this.hitChance        = hitChance;
        this.sightRange       = sightRange;
        this.seesThroughDoors = seesThroughDoors;
        this.worldX           = startX;
        this.worldY           = startY;
    }

    public void    takeDamage(int amount)  { currentHealth = Math.max(0, currentHealth - amount); }
    public boolean isAlive()               { return currentHealth > 0; }

    public int attack(Player player) {
        if (rng.nextDouble() > hitChance) return 0;
        int variance = (int)(attackPower * 0.2);
        int dmg = attackPower + rng.nextInt(variance * 2 + 1) - variance;
        player.takeDamage(dmg);
        return dmg;
    }

    public void debuffSight(int amount)       { sightRange = Math.max(0, sightRange - amount); }
    public void debuffHitChance(double amount){ hitChance  = Math.max(0.0, hitChance - amount); }
    public void setHitChance(double value)    { hitChance  = Math.max(0.0, Math.min(1.0, value)); }

    public boolean hasLineOfSight(Player player, char[][] map) {
        int x0 = worldX, y0 = worldY;
        int x1 = player.getWorldX(), y1 = player.getWorldY();
        if (Math.max(Math.abs(x1-x0), Math.abs(y1-y0)) > sightRange) return false;
        int dx = Math.abs(x1-x0), dy = -Math.abs(y1-y0);
        int sx = x0<x1?1:-1, sy = y0<y1?1:-1;
        int err = dx+dy, cx = x0, cy = y0;
        while (true) {
            if (cx==x1 && cy==y1) return true;
            if (!(cx==x0 && cy==y0)) {
                char tile = map[cy][cx];
                if (tile=='#') return false;
                if (tile=='+' && !seesThroughDoors) return false;
            }
            int e2 = 2*err;
            if (e2>=dy){err+=dy; cx+=sx;}
            if (e2<=dx){err+=dx; cy+=sy;}
        }
    }

    public void updateAggro(Player player, char[][] map) {
        if (!isAlive()) return;
        if (hasLineOfSight(player, map)) {
            aggro=true; turnsWithoutLOS=0;
        } else if (aggro) {
            if (++turnsWithoutLOS >= LOSE_AGGRO_TURNS) { aggro=false; turnsWithoutLOS=0; }
        }
    }

    public void stepToward(Player player, char[][] map, java.util.List<Enemy> all) {
        if (!aggro || !isAlive()) return;
        int dx = player.getWorldX()-worldX, dy = player.getWorldY()-worldY;
        if (Math.abs(dx)<=1 && Math.abs(dy)<=1) return;
        int sx = Integer.signum(dx), sy = Integer.signum(dy);
        int[] tryX, tryY;
        if (Math.abs(dx)>=Math.abs(dy)) { tryX=new int[]{sx,0}; tryY=new int[]{0,sy}; }
        else                             { tryX=new int[]{0,sx}; tryY=new int[]{sy,0}; }
        for (int i=0; i<2; i++) {
            int nx=worldX+tryX[i], ny=worldY+tryY[i];
            if (canMoveTo(nx,ny,map,all)) { worldX=nx; worldY=ny; return; }
        }
    }

    protected boolean canMoveTo(int nx, int ny, char[][] map, java.util.List<Enemy> all) {
        if (nx<0||nx>=map[0].length||ny<0||ny>=map.length) return false;
        char tile=map[ny][nx];
        if (tile==' '||tile=='#') return false;
        for (Enemy e : all) if (e!=this&&e.isAlive()&&e.worldX==nx&&e.worldY==ny) return false;
        return true;
    }

    public int     getWorldX()        { return worldX; }
    public int     getWorldY()        { return worldY; }
    public char    getGlyph()         { return glyph; }
    public String  getName()          { return name; }
    public int     getCurrentHealth() { return currentHealth; }
    public int     getMaxHealth()     { return maxHealth; }
    public boolean isAggro()          { return aggro; }
    public int     getSightRange()    { return sightRange; }
}