-- ─────────────────────────────────────────────────────────────────────────────
-- seed_monsters.sql  –  canonical monster definitions
--
-- Run with:  sqlite3 monsters.db < seed_monsters.sql
--
-- COLUMNS
-- ────────
-- id                 auto-assigned
-- name               display name shown in combat log
-- glyph              single ASCII char rendered on the map
-- max_health         starting and max HP
-- attack_power       base damage per hit
-- hit_chance         0.0 – 1.0  (probability of landing an attack)
-- sight_range        tiles the enemy can see (Chebyshev distance)
-- sees_through_doors 1 = ignores door tiles for LOS, 0 = blocked by doors
-- xp_value           XP awarded to the player on kill
-- drop_table         key used by Combat.rollDrop() — must match a case in the switch
-- description        flavour text (not used in gameplay)
--
-- DROP TABLE KEYS (defined in Combat.rollDrop)
-- ─────────────────────────────────────────────
-- zombie    70% potion | 15% melee  | 15% ammo
-- mutant    65% melee  | 20% potion | 15% throwable
-- ghost     100% chemical
-- skeleton  50% ammo   | 30% ranged | 20% potion
-- titan     60% ranged | 25% melee  | 15% potion
-- eye       70% chemical | 30% potion
-- snake     60% ammo   | 40% potion
-- default   50% potion | 50% ammo
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS monsters (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    name               TEXT    NOT NULL UNIQUE,
    glyph              TEXT    NOT NULL,
    max_health         INTEGER NOT NULL,
    attack_power       INTEGER NOT NULL,
    hit_chance         REAL    NOT NULL,
    sight_range        INTEGER NOT NULL,
    sees_through_doors INTEGER NOT NULL DEFAULT 0,
    xp_value           INTEGER NOT NULL,
    drop_table         TEXT    NOT NULL DEFAULT 'default',
    description        TEXT
);

-- Clear existing data before re-seeding
DELETE FROM monsters;

INSERT INTO monsters (name, glyph, max_health, attack_power, hit_chance, sight_range, sees_through_doors, xp_value, drop_table, description) VALUES
    ('Zombie',   'Z', 40,  8,  0.55, 4,  0, 15, 'zombie',   'A shambling corpse. Nearly blind, but relentless.'),
    ('Mutant',   'a', 20,  12, 0.75, 7,  0, 10, 'mutant',   'Twitchy and alert. Hits hard for its size.'),
    ('Ghost',    'G', 18,  14, 0.50, 9,  1, 12, 'ghost',    'Passes silently through doors. Drops only chemicals.'),
    ('Snake',    's', 25,  9,  0.80, 6,  0, 11, 'snake',    'Low profile, quick strike. Hard to spot in corridors.'),
    ('Titan',    'X', 120, 22, 0.65, 6,  0, 40, 'titan',    'Rare. Enormous. Do not engage without ranged weapons.'),
    ('Eye',      'o', 30,  11, 0.70, 10, 0, 18, 'eye',      'Hovering orb with wide vision. Favours chemical drops... of the blinding variety.');

-- ─────────────────────────────────────────────────────────────────────────────
-- To add a new monster, just append an INSERT here and re-run this script.
-- Example:
--
-- INSERT INTO monsters (name, glyph, max_health, attack_power, hit_chance,
--     sight_range, sees_through_doors, xp_value, drop_table, description)
-- VALUES ('Wraith', 'W', 35, 16, 0.65, 11, 1, 20, 'ghost',
--         'A spectral hunter that passes through doors and drops chemicals.');
-- ─────────────────────────────────────────────────────────────────────────────