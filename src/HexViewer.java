import java.io.*;
import java.nio.file.*;


public class HexViewer {

    private static final int BYTES_PER_LINE         = 16;
    private static final int HEX_COL_VISIBLE_WIDTH  = 49;
    private static final int ASCII_COL_VISIBLE_WIDTH = 16;

    private byte[] data;
    private int fileSize;
    private int offset    = 0;
    private int cursorRow = 0;
    private int cursorCol = 0;
    private String filename;

    // ── Edit mode ─────────────────────────────────────────────────────────────
    // Press ENTER to toggle edit mode on the highlighted byte.
    // In edit mode, UP arrow increments the byte value (+1, wraps 255→0),
    // DOWN arrow decrements it (-1, wraps 0→255). LEFT/RIGHT do nothing in
    // edit mode so you can't accidentally move off the byte you're changing.
    // Press Q to exit — if any bytes were modified the file is written first.
    private boolean editMode = false;
    private boolean dirty    = false;   // true once any byte has been changed

    // ── ANSI codes ───────────────────────────────────────────────────────────
    private static final String RESET       = "\033[0m";
    private static final String BOLD_WHITE  = "\033[1;37m";
    private static final String BOLD_YELLOW = "\033[1;33m";
    private static final String BOLD_CYAN   = "\033[1;36m";
    private static final String BOLD_GREEN  = "\033[1;32m";
    private static final String BOLD_GRAY   = "\033[1;30m";
    private static final String BOLD_RED    = "\033[1;31m";
    private static final String CURSOR_BG   = "\033[7m";
    // Edit mode highlight — red background so it's visually distinct from navigation
    private static final String EDIT_BG     = "\033[41m";

    // ────────────────────────────────────────────────────────────────────────

    public HexViewer(String filename) throws IOException {
        this.filename = filename;
        this.data     = Files.readAllBytes(Paths.get(filename));
        this.fileSize = data.length;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String color(String ansi, String text) { return ansi + text + RESET; }

    private int visibleLines() { return GameState.VIEW_H - 8; }

    private int cursorByteIndex() {
        return offset + cursorRow * BYTES_PER_LINE + cursorCol;
    }

    // ── Drawing ──────────────────────────────────────────────────────────────

    private void draw() {
        int row  = 0;
        int base = GameState.VIEWPORT_COL / 2;

        System.out.print(Renderer.centredInViewport("", base + row++));
        System.out.print(Renderer.centredInViewport("", base + row++));
        System.out.print(Renderer.centredInViewport("You are dead, however it is not dark...                                           ", base + row++));
        System.out.print(Renderer.centredInViewport("", base + row++));
        System.out.print(Renderer.centredInViewport("                                        you see an array of letters and numbers...", base + row++));
        System.out.print(Renderer.centredInViewport("", base + row++));

        int lines = visibleLines();
        for (int i = 0; i < lines; i++) {
            int     rowOffset   = offset + i * BYTES_PER_LINE;
            boolean isCursorRow = (i == cursorRow);
            String  line;

            if (rowOffset >= fileSize) {
                line = color(BOLD_WHITE, "│") + " " + "        " + " "
                        + color(BOLD_WHITE, "│") + " " + " ".repeat(HEX_COL_VISIBLE_WIDTH) + " "
                        + color(BOLD_WHITE, "│") + " " + " ".repeat(ASCII_COL_VISIBLE_WIDTH) + " "
                        + color(BOLD_WHITE, "│");
            } else {
                String        addrStr    = String.format("%08x", rowOffset);
                StringBuilder hexColored = new StringBuilder();
                StringBuilder hexVisible = new StringBuilder();
                StringBuilder asciiCol   = new StringBuilder();

                for (int j = 0; j < BYTES_PER_LINE; j++) {
                    int     pos      = rowOffset + j;
                    boolean isCursor = isCursorRow && (j == cursorCol);

                    if (pos < fileSize) {
                        byte   b   = data[pos];
                        String hex = String.format("%02x", b & 0xFF);
                        String fg; char glyph;
                        if (b == '\n' || b == '\r' || b == '\t') { fg = BOLD_YELLOW; glyph = '.'; }
                        else if (isPrintable(b))                  { fg = BOLD_CYAN;   glyph = (char) b; }
                        else if (b == 0)                          { fg = BOLD_GRAY;   glyph = '.'; }
                        else if (isJsonSpecial(b))                { fg = BOLD_YELLOW; glyph = (char) b; }
                        else                                      { fg = BOLD_RED;    glyph = '.'; }

                        // Edit mode: red bg. Normal cursor: reverse video. Otherwise: plain fg.
                        String prefix = isCursor ? (editMode ? EDIT_BG : CURSOR_BG) + fg : fg;
                        hexColored.append(prefix).append(hex).append(RESET).append(" ");
                        asciiCol.append(prefix).append(glyph).append(RESET);
                        hexVisible.append(hex).append(" ");
                    } else {
                        hexColored.append("   ");
                        hexVisible.append("   ");
                        asciiCol.append(' ');
                    }
                    if (j == 7) { hexColored.append(" "); hexVisible.append(" "); }
                }

                int hexPad = HEX_COL_VISIBLE_WIDTH - hexVisible.length();
                if (hexPad > 0) hexColored.append(" ".repeat(hexPad));
                int asciiPad = ASCII_COL_VISIBLE_WIDTH - Math.min(BYTES_PER_LINE, Math.max(0, fileSize - rowOffset));
                if (asciiPad > 0) asciiCol.append(" ".repeat(asciiPad));

                line = color(BOLD_GREEN, addrStr) + " "
                        + color(BOLD_WHITE, "│") + " " + hexColored
                        + color(BOLD_WHITE, "│") + " " + asciiCol + "  ";
            }
            System.out.print(Renderer.centredInViewport(line, base + row++));
        }

        System.out.print(Renderer.centredInViewport("", base + row++));
        System.out.print(Renderer.centredInViewport("You have the ability to change things, don't screw it up or you're gone for good...", base + row));
        System.out.flush();
    }

    // ── Edit helpers ─────────────────────────────────────────────────────────

    /** Increment the highlighted byte by 1, wrapping 255 → 0. Marks file dirty. */
    private void incrementByte() {
        int idx = cursorByteIndex();
        if (idx < fileSize) { data[idx] = (byte)((data[idx] & 0xFF) + 1); dirty = true; }
    }

    /** Decrement the highlighted byte by 1, wrapping 0 → 255. Marks file dirty. */
    private void decrementByte() {
        int idx = cursorByteIndex();
        if (idx < fileSize) { data[idx] = (byte)((data[idx] & 0xFF) - 1); dirty = true; }
    }

    /** Write in-memory data back to the original file. Called on Q if dirty. */
    private void saveFile() throws IOException {
        Files.write(Paths.get(filename), data);
    }

    // ── Cursor movement ──────────────────────────────────────────────────────

    private boolean isPrintable(byte b)   { return b >= 32 && b <= 126; }

    private boolean isJsonSpecial(byte b) {
        return b == '{' || b == '}' || b == '[' || b == ']' ||
                b == ':' || b == ',' || b == '"' || b == '\n' || b == '\r' || b == '\t';
    }

    private void moveCursorUp() {
        if (cursorRow > 0) cursorRow--;
        else if (offset > 0) offset -= BYTES_PER_LINE;
        clampCursorToFile();
    }

    private void moveCursorDown() {
        if (offset + (cursorRow + 1) * BYTES_PER_LINE >= fileSize) return;
        if (cursorRow < visibleLines() - 1) cursorRow++;
        else offset += BYTES_PER_LINE;
        clampCursorToFile();
    }

    private void moveCursorLeft() {
        if (cursorCol > 0) {
            cursorCol--;
        } else if (cursorRow > 0) {
            cursorRow--; cursorCol = BYTES_PER_LINE - 1;
        } else if (offset > 0) {
            offset -= BYTES_PER_LINE; cursorCol = BYTES_PER_LINE - 1;
        }
        clampCursorToFile();
    }

    private void moveCursorRight() {
        if (cursorByteIndex() >= fileSize - 1) return;
        if (cursorCol < BYTES_PER_LINE - 1) {
            cursorCol++;
        } else {
            cursorCol = 0;
            if (cursorRow < visibleLines() - 1) cursorRow++;
            else offset += BYTES_PER_LINE;
        }
        clampCursorToFile();
    }

    private void clampCursorToFile() {
        if (fileSize == 0) return;
        if (offset < 0) offset = 0;
        int maxOffset = ((fileSize - 1) / BYTES_PER_LINE) * BYTES_PER_LINE;
        if (offset > maxOffset) offset = maxOffset;
        int maxRow = (fileSize - 1 - offset) / BYTES_PER_LINE;
        if (cursorRow > maxRow) {
            cursorRow = maxRow;
            cursorCol = Math.min(cursorCol, (fileSize - 1 - offset) - cursorRow * BYTES_PER_LINE);
        }
        if (cursorRow < 0) cursorRow = 0;
        if (cursorCol < 0) cursorCol = 0;
    }

    // ── Main loop ────────────────────────────────────────────────────────────

    private void handleInput() throws IOException, InterruptedException {
        try {
            new ProcessBuilder("/bin/sh", "-c", "stty raw -echo").start().waitFor();

            while (true) {
                draw();

                int ch = System.in.read();

                // ENTER — toggle edit mode on the currently highlighted byte
                if (ch == '\r' || ch == '\n') {
                    editMode = !editMode;

                } else if (ch == 'q' || ch == 'Q') {
                    if (dirty) saveFile();   // persist changes before leaving
                    break;

                } else if (ch == 'g' && !editMode) {
                    offset = 0; cursorRow = 0; cursorCol = 0;

                } else if ((ch == 'e' || ch == 'E') && !editMode) {
                    int lastByte     = fileSize - 1;
                    int lastLine     = lastByte / BYTES_PER_LINE;
                    int firstVisible = Math.max(0, lastLine - visibleLines() + 1);
                    offset    = firstVisible * BYTES_PER_LINE;
                    cursorRow = lastLine - firstVisible;
                    cursorCol = lastByte % BYTES_PER_LINE;

                } else if (ch == 27) {
                    int next1 = System.in.read();
                    int next2 = System.in.read();
                    if (next1 == 91) {
                        if (editMode) {
                            // Arrow keys change the byte value, not the cursor position
                            switch (next2) {
                                case 65: incrementByte(); break;  // ↑ → +1
                                case 66: decrementByte(); break;  // ↓ → -1
                                // LEFT / RIGHT intentionally ignored in edit mode
                            }
                        } else {
                            switch (next2) {
                                case 65: moveCursorUp();    break;
                                case 66: moveCursorDown();  break;
                                case 67: moveCursorRight(); break;
                                case 68: moveCursorLeft();  break;
                                case 53:
                                    System.in.read();
                                    offset = Math.max(0, offset - visibleLines() * BYTES_PER_LINE);
                                    clampCursorToFile(); break;
                                case 54:
                                    System.in.read();
                                    int maxOff = ((fileSize - 1) / BYTES_PER_LINE) * BYTES_PER_LINE;
                                    offset = Math.min(maxOff, offset + visibleLines() * BYTES_PER_LINE);
                                    clampCursorToFile(); break;
                            }
                        }
                    }
                }
            }
        } finally {
            new ProcessBuilder("/bin/sh", "-c", "stty sane echo").start().waitFor();
        }
    }

    public void run() throws IOException {
        try {
            handleInput();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}