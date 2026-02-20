import java.util.List;

// ─────────────────────────────────────────────────────────────────────────────
// Chemical  –  abstract base class for science weapons
//
// Chemicals are consumable, single-use Science-type weapons.  They affect
// all adjacent enemies (range 1) simultaneously rather than a single target,
// making them area tools rather than single-target attacks.
//
// Architecture note: Chemicals are designed for easy extensibility.
// To add a new chemical, extend this class and implement applyEffect().
// The use() method in the base class handles adjacency checking, iteration,
// and messaging — subclasses only need to define what happens per enemy.
//
// Key: 'q' (use active chemical from inventory via Q key in Game)
// ─────────────────────────────────────────────────────────────────────────────
public abstract class Chemical extends Weapon {

    public Chemical(String name, double weight) {
        // range 1, Science type, always hits (effect-based not accuracy-based)
        super(name, 'q', 0, 1.0, weight, WeaponType.SCIENCE, 1);
    }

    @Override public boolean isConsumable() { return true; }

    /**
     * Apply this chemical's unique effect to one enemy.
     * Called once per adjacent enemy.
     *
     * @param target  the enemy to affect
     * @return        a short description of what happened to this enemy
     */
    public abstract String applyEffect(Enemy target);

    /**
     * Use this chemical on all adjacent living enemies.
     *
     * @param adjEnemies  enemies within range 1 of the player
     * @return            combined result message for the combat log
     */
    public String use(List<Enemy> adjEnemies) {
        if (adjEnemies.isEmpty()) {
            return weaponClass + " fizzles — no enemies in range.";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < adjEnemies.size(); i++) {
            if (i > 0) sb.append("  ");
            sb.append(applyEffect(adjEnemies.get(i)));
        }
        return sb.toString();
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// BlindingChemical  –  sets adjacent enemy sight to 0 and hit chance to 10%
//
// Represents a corrosive aerosol that blinds enemies caught in the cloud.
// Useful for disabling dangerous groups before engaging.
// ─────────────────────────────────────────────────────────────────────────────
class BlindingChemical extends Chemical {

    public BlindingChemical() {
        super("Blinding Chemical", 0.3);
    }

    @Override
    public String applyEffect(Enemy target) {
        target.debuffSight(target.getSightRange());   // reduce to 0
        target.setHitChance(0.10);
        return target.getName() + " is blinded! (sight=0, acc=10%)";
    }
}