package test; /**
 * Test for SaveManager behavior.
 */
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SaveManagerTest {

    @Test
    void saveExists_returnsFalseForMissingPath() throws IOException {
        Path tmpDir = Files.createTempDirectory("ris-saveexists-missing");
        Path missing = tmpDir.resolve("missing-save.json");

        assertFalse(SaveManager.saveExists(missing.toString()));
    }

    @Test
    void saveAndLoad_roundTripPreservesCoreState() throws IOException {
        Player originalPlayer = GameState.player;
        char[][] originalMap = GameState.map;
        boolean[][] originalExplored = GameState.explored;
        java.util.List<GameState.Room> originalRooms = GameState.rooms;
        java.util.List<Enemy> originalEnemies = GameState.enemies;
        java.util.List<Item> originalItems = GameState.items;
        java.util.List<String> originalLog = GameState.combatLog;

        Path tmpDir = Files.createTempDirectory("ris-save-roundtrip");
        Path saveFile = tmpDir.resolve("save-roundtrip.json");

        try {
            GameState.player = new Player("Tester", PlayerClass.SOLDIER, 1, 2);
            GameState.map = new char[GameState.WORLD_H][GameState.WORLD_W];
            GameState.explored = new boolean[GameState.WORLD_H][GameState.WORLD_W];
            for (int y = 0; y < GameState.WORLD_H; y++) {
                for (int x = 0; x < GameState.WORLD_W; x++) {
                    GameState.map[y][x] = GameState.TILE_EMPTY;
                }
            }
            GameState.rooms = new ArrayList<>();
            GameState.rooms.add(new GameState.Room(0, 0, 6, 6));
            GameState.enemies = new ArrayList<>();
            GameState.items = new ArrayList<>();
            GameState.combatLog = new ArrayList<>();

            SaveManager.save(saveFile.toString());
            assertTrue(Files.exists(saveFile));

            SaveManager.load(saveFile.toString());
            assertEquals("Tester", GameState.player.getName());
        } finally {
            GameState.player = originalPlayer;
            GameState.map = originalMap;
            GameState.explored = originalExplored;
            GameState.rooms = originalRooms;
            GameState.enemies = originalEnemies;
            GameState.items = originalItems;
            GameState.combatLog = originalLog;
        }
    }

    @Test
    void load_missingFile_throwsIOException() {
        assertThrows(IOException.class, () -> SaveManager.load("no-such-save-file.json"));
    }

    @Test
    void save_includesPlayerInventoryMapAndEnemies() throws IOException {
        Player originalPlayer = GameState.player;
        char[][] originalMap = GameState.map;
        boolean[][] originalExplored = GameState.explored;
        java.util.List<GameState.Room> originalRooms = GameState.rooms;
        java.util.List<Enemy> originalEnemies = GameState.enemies;
        List<Item> originalItems = GameState.items;
        java.util.List<String> originalLog = GameState.combatLog;

        Path tmpDir = Files.createTempDirectory("ris-save-json");
        Path saveFile = tmpDir.resolve("save-content.json");

        try {
            GameState.player = new Player("Saver", PlayerClass.MARINE, 4, 4);
            GameState.map = new char[GameState.WORLD_H][GameState.WORLD_W];
            GameState.explored = new boolean[GameState.WORLD_H][GameState.WORLD_W];
            for (int y = 0; y < GameState.WORLD_H; y++) {
                for (int x = 0; x < GameState.WORLD_W; x++) {
                    GameState.map[y][x] = GameState.TILE_EMPTY;
                }
            }
            GameState.rooms = new ArrayList<>();
            GameState.rooms.add(new GameState.Room(1, 1, 8, 8));
            GameState.enemies = new ArrayList<>();
            GameState.items = new ArrayList<>();
            GameState.combatLog = new ArrayList<>();

            SaveManager.save(saveFile.toString());
            String json = Files.readString(saveFile);

            assertTrue(json.contains("\"player\""));
            assertTrue(json.contains("\"inventory\""));
            assertTrue(json.contains("\"map\""));
            assertTrue(json.contains("\"enemies\""));
        } finally {
            GameState.player = originalPlayer;
            GameState.map = originalMap;
            GameState.explored = originalExplored;
            GameState.rooms = originalRooms;
            GameState.enemies = originalEnemies;
            GameState.items = originalItems;
            GameState.combatLog = originalLog;
        }
    }
}
