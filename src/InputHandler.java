import java.io.IOException;
import java.util.Scanner;

// ─────────────────────────────────────────────────────────────────────────────
// InputHandler  –  all terminal I/O except rendering
//
// Covers:
//   • Terminal size detection
//   • Raw mode enable / restore
//   • Low-level key reading (including arrow key escape sequences)
//   • Pre-game screens: class selection and name entry
// ─────────────────────────────────────────────────────────────────────────────
public class InputHandler {

    // ── Terminal size ─────────────────────────────────────────────────────────

    static void detectTerminalSize() {
        int termW = GameState.BORDER_FILE_W;   // default: assume border size
        int termH = GameState.BORDER_FILE_H;
        try {
            Process p = Runtime.getRuntime().exec(
                    new String[]{"sh", "-c", "stty size </dev/tty"});
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            String[] parts = out.split(" ");
            if (parts.length == 2) {
                termH = Integer.parseInt(parts[0]);
                termW = Integer.parseInt(parts[1]);
            }
        } catch (Exception ignored) {}

        // Warn if terminal is smaller than the border file — content will clip.
        // Larger terminals are fine; the border will be left-top aligned and
        // the status bar / log will appear below it as normal.
        if (termW < GameState.BORDER_FILE_W || termH < GameState.BORDER_FILE_H) {
            System.err.println("[WARNING] Terminal is " + termW + "x" + termH +
                    " — border requires " + GameState.BORDER_FILE_W +
                    "x" + GameState.BORDER_FILE_H + ". Resize your terminal.");
        }

        // The world viewport lives inside the border walls.
        // VIEWPORT_ROW/COL are where the world region starts on screen (0-based).
        GameState.VIEWPORT_ROW = GameState.BORDER_TOP;
        GameState.VIEWPORT_COL = GameState.BORDER_LEFT;

        // VIEW_W/H are the inner dimensions — all gameplay code uses these.
        GameState.VIEW_W  = GameState.BORDER_FILE_W
                - GameState.BORDER_LEFT
                - GameState.BORDER_RIGHT;
        GameState.VIEW_H  = GameState.BORDER_FILE_H
                - GameState.BORDER_TOP
                - GameState.BORDER_BOTTOM;

        // Ensure odd dimensions so the player sits on a true centre cell
        if (GameState.VIEW_H % 2 == 0) GameState.VIEW_H--;
        if (GameState.VIEW_W % 2 == 0) GameState.VIEW_W--;

        GameState.VIEW_CX = GameState.VIEW_W / 2;
        GameState.VIEW_CY = GameState.VIEW_H / 2;
    }

    // ── Raw mode ──────────────────────────────────────────────────────────────

    static void enableRawMode() throws IOException {
        System.out.print("\033[?25l\033[2J\033[H");
        System.out.flush();
        try {
            Runtime.getRuntime().exec(
                            new String[]{"sh", "-c", "stty -echo -icanon min 1 </dev/tty"})
                    .waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    static void restoreTerminal() throws IOException {
        System.out.print("\033[?25h");
        System.out.flush();
        try {
            Runtime.getRuntime().exec(
                            new String[]{"sh", "-c", "stty echo icanon </dev/tty"})
                    .waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // ── Cursor / screen helpers ───────────────────────────────────────────────

    static void clearScreen() {
        System.out.print("\033[2J\033[H");
        System.out.flush();
    }

    static void moveCursor(int row, int col) {
        System.out.printf("\033[%d;%dH", row + 1, col + 1);
        System.out.flush();
    }

    // ── Key reading ───────────────────────────────────────────────────────────

    /**
     * Read one keypress from stdin.
     * Arrow keys are decoded from their 3-byte ESC sequences and returned
     * as the sentinel values 1000-1003 (up/down/left/right).
     * Plain ESC returns 27.
     */
    static int readKey() throws IOException {
        int c = System.in.read();
        if (c == 27) {
            if (System.in.available() > 0) {
                int c2 = System.in.read();
                if (c2 == '[' && System.in.available() > 0) {
                    switch (System.in.read()) {
                        case 'A': return 1000;
                        case 'B': return 1001;
                        case 'D': return 1002;
                        case 'C': return 1003;
                    }
                }
            }
            return 27;
        }
        return c;
    }

    // ── Pre-game screens ──────────────────────────────────────────────────────

    static PlayerClass selectClass() throws IOException {
        Scanner sc = new Scanner(System.in);
        PlayerClass[] classes = PlayerClass.values();

        while (true) {
            clearScreen();
            System.out.println("  ROGUE IN SPACE  —  SELECT YOUR CLASS");
            System.out.println();
            System.out.println("  You are adrift on a derelict station.");
            System.out.println("  Something has gone very wrong.  Choose who you are.");
            System.out.println();
            System.out.println("  #   Class          Stats");
            System.out.println("  -   -----          -----");
            for (int i = 0; i < classes.length; i++)
                System.out.printf("  %d   %-12s   %s%n",
                        i + 1, classes[i].label, classes[i].description);
            System.out.println();
            System.out.println("  SOLDIER   – Heavy armour, high HP, charges in head-first.");
            System.out.println("  MARINE    – Precision fighter, rarely misses, light on HP.");
            System.out.println("  SCIENTIST – Poor fighter but sees further than anyone else.");
            System.out.println("  ENGINEER  – Built-in shielding, highest defense, reliable.");
            System.out.println("  PILOT     – Fast reflexes, balanced stats, good hit chance.");
            System.out.println("  MEDIC     – Most HP of all, sustains long fights, weak hits.");
            System.out.println();
            System.out.print("  Enter a number (1-6): ");

            try {
                int choice = Integer.parseInt(sc.nextLine().trim());
                if (choice >= 1 && choice <= classes.length)
                    return classes[choice - 1];
            } catch (NumberFormatException ignored) {}

            System.out.println("  Invalid choice. Press ENTER to try again.");
            sc.nextLine();
        }
    }

    static String enterName() {
        Scanner sc = new Scanner(System.in);
        clearScreen();
        System.out.println("  ROGUE IN SPACE  —  YOUR NAME");
        System.out.println();
        System.out.print("  Enter your name (or press ENTER for 'Unknown'): ");
        String name = sc.nextLine().trim();
        return name.isEmpty() ? "Unknown" : name;
    }
}