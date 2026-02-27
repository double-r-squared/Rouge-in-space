import os
import sys
import cv2
import tkinter as tk
from tkinter import ttk, filedialog, messagebox
from ascii_magic import AsciiArt
from PIL import Image, ImageTk
import numpy as np
import tempfile
import threading
import random


# ──────────────────────────────────────────────
# Core image processing
# ──────────────────────────────────────────────

def load_image(image_path):
    img = cv2.imread(image_path)
    return img  # None if failed


def adjust_contrast(img, contrast_value):
    """contrast_value in range [-127, 127]"""
    f = 131 * (contrast_value + 127) / (127 * (131 - contrast_value))
    return cv2.addWeighted(img, f, img, 0, 127 * (1 - f))


def adjust_brightness(img, brightness):
    """brightness in range [-127, 127]"""
    return cv2.convertScaleAbs(img, alpha=1, beta=brightness)


def adjust_sharpness(img, amount):
    """amount: 0 = none, 5 = strong"""
    if amount == 0:
        return img
    kernel = np.array([[-1, -1, -1],
                       [-1,  9 + amount, -1],
                       [-1, -1, -1]])
    return cv2.filter2D(img, -1, kernel)


def resize_for_ascii(img, scale_pct):
    h, w = img.shape[:2]
    new_w = max(50, int(w * scale_pct / 100))
    new_h = max(30, int(h * scale_pct / 100))
    return cv2.resize(img, (new_w, new_h), interpolation=cv2.INTER_AREA)


def convert_to_ascii(img, columns, monochrome):
    with tempfile.NamedTemporaryFile(suffix='.png', delete=False) as tmp:
        tmp_path = tmp.name
        cv2.imwrite(tmp_path, img)
    try:
        my_art = AsciiArt.from_image(tmp_path)
        output = my_art.to_ascii(columns=columns, monochrome=monochrome)
    finally:
        os.remove(tmp_path)
    return output


def apply_space_density(ascii_text, density_pct):
    if density_pct <= 0:
        return ascii_text
    chance = density_pct / 100.0
    result = []
    for ch in ascii_text:
        if ch not in ('\n', ' ') and random.random() < chance:
            result.append(' ')
        else:
            result.append(ch)
    return ''.join(result)


def save_ascii_art(ascii_output, filename):
    with open(filename, 'w') as f:
        f.write(ascii_output)


def process_image(img, contrast, brightness, sharpness, scale,
                  columns, mono, invert, space_density):
    img = img.copy()
    img = adjust_contrast(img, contrast)
    img = adjust_brightness(img, brightness)
    img = adjust_sharpness(img, sharpness)
    if invert:
        img = cv2.bitwise_not(img)
    img = resize_for_ascii(img, scale)
    result = convert_to_ascii(img, int(columns), mono)
    return apply_space_density(result, space_density)


# ──────────────────────────────────────────────
# Slider + sweep range specs
# ──────────────────────────────────────────────

SLIDER_SPECS = [
    # (label,          key,             lo,    hi,  default, res,  is_int)
    ("Contrast",       "contrast",      -127,  127,  0,      1,    True),
    ("Brightness",     "brightness",    -127,  127,  0,      1,    True),
    ("Sharpness",      "sharpness",        0,    5,  0,      0.5,  False),
    ("Pre-scale %",    "scale",           10,  200,  100,    5,    True),
    ("Columns",        "columns",         40,  400,  200,    10,   True),
    ("Space Density",  "space_density",    0,  100,  0,      1,    True),
]


# ──────────────────────────────────────────────
# Sweep range row widget
# ──────────────────────────────────────────────

class RangeRow(tk.Frame):
    """Checkbox + from/to entries for batch sweep of one property."""

    def __init__(self, parent, key, lo, hi, default, is_int, bg):
        super().__init__(parent, bg=bg)
        self.key    = key
        self.lo     = lo
        self.hi     = hi
        self.is_int = is_int

        self.enabled  = tk.BooleanVar(value=False)
        self.from_var = tk.StringVar(value=str(default))
        self.to_var   = tk.StringVar(value=str(default))

        tk.Checkbutton(self, variable=self.enabled, bg=bg,
                       fg="#89dceb", activebackground=bg,
                       selectcolor="#313244",
                       text="Sweep", font=("Helvetica", 9)
                       ).pack(side=tk.LEFT)

        tk.Label(self, text="from", bg=bg, fg="#6c7086",
                 font=("Helvetica", 9)).pack(side=tk.LEFT, padx=(8, 2))
        tk.Entry(self, textvariable=self.from_var, width=6,
                 bg="#313244", fg="#cdd6f4", insertbackground="#cdd6f4",
                 relief=tk.FLAT, font=("Helvetica", 9)
                 ).pack(side=tk.LEFT)

        tk.Label(self, text="to", bg=bg, fg="#6c7086",
                 font=("Helvetica", 9)).pack(side=tk.LEFT, padx=(6, 2))
        tk.Entry(self, textvariable=self.to_var, width=6,
                 bg="#313244", fg="#cdd6f4", insertbackground="#cdd6f4",
                 relief=tk.FLAT, font=("Helvetica", 9)
                 ).pack(side=tk.LEFT)

    def get_range(self):
        """Returns (from_val, to_val) clamped to bounds, or None if disabled."""
        if not self.enabled.get():
            return None
        try:
            a = float(self.from_var.get())
            b = float(self.to_var.get())
        except ValueError:
            return None
        a = max(self.lo, min(self.hi, a))
        b = max(self.lo, min(self.hi, b))
        if self.is_int:
            return (int(a), int(b))
        return (round(a, 2), round(b, 2))

    def interpolate(self, t):
        """Value at position t (0.0–1.0) between from and to."""
        r = self.get_range()
        if r is None:
            return None
        a, b = r
        v = a + (b - a) * t
        return int(round(v)) if self.is_int else round(v, 2)


# ──────────────────────────────────────────────
# Main app
# ──────────────────────────────────────────────

class ASCIIArtStudio(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("ASCII Art Studio")
        self.configure(bg="#1e1e2e")
        self.minsize(1200, 720)

        self.image_path    = None
        self.original_img  = None
        self.current_ascii = ""
        self._preview_job  = None
        self._processing   = False

        self._build_ui()

    # ─────────────────────────────────────────
    # Layout
    # ─────────────────────────────────────────

    def _build_ui(self):
        # Top bar
        top = tk.Frame(self, bg="#181825", pady=8)
        top.pack(fill=tk.X)

        tk.Button(top, text="📂  Open Image", command=self._open_image,
                  bg="#cba6f7", fg="#1e1e2e", font=("Helvetica", 11, "bold"),
                  relief=tk.FLAT, padx=12, pady=4, cursor="hand2"
                  ).pack(side=tk.LEFT, padx=12)

        self.file_label = tk.Label(top, text="No image loaded",
                                   bg="#181825", fg="#a6adc8",
                                   font=("Helvetica", 10))
        self.file_label.pack(side=tk.LEFT, padx=6)

        tk.Button(top, text="💾  Export Frame", command=self._export,
                  bg="#a6e3a1", fg="#1e1e2e", font=("Helvetica", 11, "bold"),
                  relief=tk.FLAT, padx=12, pady=4, cursor="hand2"
                  ).pack(side=tk.RIGHT, padx=12)

        self.status_label = tk.Label(top, text="",
                                     bg="#181825", fg="#f38ba8",
                                     font=("Helvetica", 10))
        self.status_label.pack(side=tk.RIGHT, padx=6)

        # Main split
        main = tk.PanedWindow(self, orient=tk.HORIZONTAL,
                              bg="#1e1e2e", sashwidth=6, sashrelief=tk.FLAT)
        main.pack(fill=tk.BOTH, expand=True)

        # ── Left panel (scrollable) ──
        ctrl_outer = tk.Frame(main, bg="#181825", width=340)
        ctrl_outer.pack_propagate(False)
        main.add(ctrl_outer, minsize=300)

        canvas = tk.Canvas(ctrl_outer, bg="#181825", highlightthickness=0)
        sb = tk.Scrollbar(ctrl_outer, orient=tk.VERTICAL, command=canvas.yview)
        canvas.configure(yscrollcommand=sb.set)
        sb.pack(side=tk.RIGHT, fill=tk.Y)
        canvas.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)

        ctrl_frame = tk.Frame(canvas, bg="#181825")
        ctrl_win = canvas.create_window((0, 0), window=ctrl_frame, anchor="nw")

        def _on_frame_configure(e):
            canvas.configure(scrollregion=canvas.bbox("all"))
        def _on_canvas_configure(e):
            canvas.itemconfig(ctrl_win, width=e.width)

        ctrl_frame.bind("<Configure>", _on_frame_configure)
        canvas.bind("<Configure>", _on_canvas_configure)
        canvas.bind_all("<MouseWheel>",
                        lambda e: canvas.yview_scroll(int(-1*(e.delta/120)), "units"))

        # ── Section: Preview Controls ──
        self._section(ctrl_frame, "Preview Controls")

        self.slider_vars = {}
        self.range_rows  = {}

        for label, key, lo, hi, default, res, is_int in SLIDER_SPECS:
            self._add_slider_with_range(ctrl_frame, label, key,
                                        lo, hi, default, res, is_int)

        # Toggles
        self._add_toggle(ctrl_frame, "Monochrome",    "mono")
        self._add_toggle(ctrl_frame, "Invert Colors", "invert")

        tk.Button(ctrl_frame, text="↺  Reset All", command=self._reset,
                  bg="#313244", fg="#cdd6f4", font=("Helvetica", 10),
                  relief=tk.FLAT, padx=10, pady=3, cursor="hand2"
                  ).pack(pady=10, padx=18, fill=tk.X)

        self.progress = ttk.Progressbar(ctrl_frame, mode="indeterminate",
                                        length=280)
        self.progress.pack(pady=(0, 4), padx=18)

        # ── Section: Batch Export ──
        tk.Frame(ctrl_frame, bg="#313244", height=1
                 ).pack(fill=tk.X, padx=14, pady=8)
        self._section(ctrl_frame, "Batch Export")

        tk.Label(ctrl_frame,
                 text='Enable "Sweep" on any property above and set\n'
                      'from/to values. Those properties interpolate\n'
                      'across frames; everything else stays fixed.',
                 bg="#181825", fg="#6c7086",
                 font=("Helvetica", 9), justify=tk.LEFT
                 ).pack(padx=18, anchor="w", pady=(0, 8))

        iter_row = tk.Frame(ctrl_frame, bg="#181825")
        iter_row.pack(fill=tk.X, padx=18, pady=4)
        tk.Label(iter_row, text="Frames", bg="#181825", fg="#cdd6f4",
                 font=("Helvetica", 10)).pack(side=tk.LEFT)
        self.iter_var = tk.IntVar(value=10)
        tk.Spinbox(iter_row, from_=1, to=500, textvariable=self.iter_var,
                   width=6, bg="#313244", fg="#cdd6f4",
                   buttonbackground="#45475a", relief=tk.FLAT
                   ).pack(side=tk.RIGHT)

        tk.Button(ctrl_frame, text="⚡  Batch Export Frames",
                  command=self._batch_export,
                  bg="#fab387", fg="#1e1e2e", font=("Helvetica", 10, "bold"),
                  relief=tk.FLAT, padx=10, pady=6, cursor="hand2"
                  ).pack(pady=8, padx=18, fill=tk.X)

        # ── Right: ASCII preview ──
        preview_frame = tk.Frame(main, bg="#11111b")
        main.add(preview_frame, minsize=600)

        txt_frame = tk.Frame(preview_frame, bg="#11111b")
        txt_frame.pack(fill=tk.BOTH, expand=True, padx=6, pady=6)

        self.txt = tk.Text(txt_frame, bg="#11111b", fg="#cdd6f4",
                           font=("Courier", 5), wrap=tk.NONE,
                           state=tk.DISABLED, relief=tk.FLAT, cursor="arrow")
        vsb2 = tk.Scrollbar(txt_frame, orient=tk.VERTICAL,  command=self.txt.yview)
        hsb2 = tk.Scrollbar(txt_frame, orient=tk.HORIZONTAL, command=self.txt.xview)
        self.txt.configure(yscrollcommand=vsb2.set, xscrollcommand=hsb2.set)
        vsb2.pack(side=tk.RIGHT, fill=tk.Y)
        hsb2.pack(side=tk.BOTTOM, fill=tk.X)
        self.txt.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        self.txt.tag_configure("placeholder", foreground="#45475a",
                               font=("Helvetica", 16))
        self._show_placeholder()

    # ─────────────────────────────────────────
    # Widget helpers
    # ─────────────────────────────────────────

    def _section(self, parent, text):
        tk.Label(parent, text=text, bg="#181825", fg="#cdd6f4",
                 font=("Helvetica", 12, "bold")).pack(pady=(10, 2))

    def _add_slider_with_range(self, parent, label, key,
                                lo, hi, default, res, is_int):
        var = tk.DoubleVar(value=default)
        self.slider_vars[key] = var

        row = tk.Frame(parent, bg="#181825")
        row.pack(fill=tk.X, padx=18, pady=(6, 0))
        tk.Label(row, text=label, bg="#181825", fg="#cdd6f4",
                 font=("Helvetica", 10), width=13, anchor="w").pack(side=tk.LEFT)
        val_lbl = tk.Label(row, bg="#181825", fg="#89b4fa",
                           font=("Helvetica", 10), width=7)
        val_lbl.pack(side=tk.RIGHT)

        def fmt(*_, v=var, lbl=val_lbl, ii=is_int):
            val = v.get()
            lbl.configure(text=f"{int(val)}" if ii else f"{val:.1f}")
            self._schedule_preview()
        var.trace_add("write", fmt)
        fmt()

        tk.Scale(parent, variable=var, from_=lo, to=hi,
                 resolution=res, orient=tk.HORIZONTAL,
                 bg="#181825", fg="#cdd6f4", troughcolor="#313244",
                 activebackground="#cba6f7", highlightthickness=0,
                 showvalue=False, sliderlength=16, length=280
                 ).pack(padx=18)

        rr = RangeRow(parent, key, lo, hi, default, is_int, bg="#181825")
        rr.pack(fill=tk.X, padx=26, pady=(0, 2))
        self.range_rows[key] = rr

    def _add_toggle(self, parent, label, key):
        frame = tk.Frame(parent, bg="#181825")
        frame.pack(fill=tk.X, padx=18, pady=(4, 0))
        tk.Label(frame, text=label, bg="#181825", fg="#cdd6f4",
                 font=("Helvetica", 10)).pack(side=tk.LEFT)
        var = tk.BooleanVar(value=False)
        setattr(self, f"{key}_var", var)
        tk.Checkbutton(frame, variable=var, bg="#181825", fg="#cdd6f4",
                       activebackground="#181825", selectcolor="#313244",
                       command=self._schedule_preview
                       ).pack(side=tk.RIGHT)

    # ─────────────────────────────────────────
    # Helpers
    # ─────────────────────────────────────────

    def _show_placeholder(self):
        self.txt.configure(state=tk.NORMAL)
        self.txt.delete("1.0", tk.END)
        self.txt.insert("1.0",
                        "\n\n\n\n\n       Open an image to see ASCII art preview here.",
                        "placeholder")
        self.txt.configure(state=tk.DISABLED)

    def _set_status(self, msg, color="#f38ba8"):
        self.status_label.configure(text=msg, fg=color)

    def _reset(self):
        defaults = {"contrast": 0, "brightness": 0, "sharpness": 0,
                    "scale": 100, "columns": 200, "space_density": 0}
        for k, v in defaults.items():
            self.slider_vars[k].set(v)
        self.mono_var.set(False)
        self.invert_var.set(False)
        for rr in self.range_rows.values():
            rr.enabled.set(False)

    def _get_params(self, t=None):
        """
        Build parameter dict.
        t=None  → use current slider values (for live preview).
        t=0..1  → interpolate enabled sweep ranges; use slider value for the rest.
        """
        p = {}
        int_keys = {"contrast", "brightness", "scale", "columns", "space_density"}
        for _, key, *_ in SLIDER_SPECS:
            rr = self.range_rows[key]
            if t is not None and rr.enabled.get():
                p[key] = rr.interpolate(t)
            else:
                v = self.slider_vars[key].get()
                p[key] = int(v) if key in int_keys else v
        p["mono"]   = self.mono_var.get()
        p["invert"] = self.invert_var.get()
        return p

    # ─────────────────────────────────────────
    # Image loading
    # ─────────────────────────────────────────

    def _open_image(self):
        path = filedialog.askopenfilename(
            title="Select Image",
            filetypes=[("Images", "*.png *.jpg *.jpeg *.bmp *.gif *.tiff *.webp"),
                       ("All files", "*.*")])
        if not path:
            return
        img = load_image(path)
        if img is None:
            messagebox.showerror("Error", f"Could not load image:\n{path}")
            return
        self.image_path   = path
        self.original_img = img
        self.file_label.configure(text=os.path.basename(path))
        self._schedule_preview()

    # ─────────────────────────────────────────
    # Preview
    # ─────────────────────────────────────────

    def _schedule_preview(self, *_):
        if self._preview_job:
            self.after_cancel(self._preview_job)
        self._preview_job = self.after(400, self._run_preview)

    def _run_preview(self):
        if self.original_img is None:
            return
        if self._processing:
            return
        self._processing = True
        self.progress.start(10)
        self._set_status("Rendering…", "#f9e2af")
        threading.Thread(target=self._render_preview, daemon=True).start()

    def _render_preview(self):
        try:
            p = self._get_params()
            ascii_out = process_image(
                self.original_img,
                p["contrast"], p["brightness"], p["sharpness"],
                p["scale"], p["columns"], p["mono"], p["invert"],
                p["space_density"]
            )
            self.current_ascii = ascii_out
            self.after(0, self._update_text_widget, ascii_out)
            self.after(0, self._set_status, "Ready", "#a6e3a1")
        except Exception as e:
            self.after(0, self._set_status, f"Error: {e}", "#f38ba8")
        finally:
            self._processing = False
            self.after(0, self.progress.stop)

    def _update_text_widget(self, text):
        self.txt.configure(state=tk.NORMAL)
        self.txt.delete("1.0", tk.END)
        self.txt.insert("1.0", text)
        self.txt.configure(state=tk.DISABLED)

    # ─────────────────────────────────────────
    # Export
    # ─────────────────────────────────────────

    def _export(self):
        if not self.current_ascii:
            messagebox.showwarning("Nothing to export",
                                   "Generate a preview first.")
            return
        path = filedialog.asksaveasfilename(
            defaultextension=".txt",
            filetypes=[("Text files", "*.txt"), ("All files", "*.*")])
        if path:
            save_ascii_art(self.current_ascii, path)
            self._set_status(f"Saved → {os.path.basename(path)}", "#a6e3a1")

    def _batch_export(self):
        if self.original_img is None:
            messagebox.showwarning("No image", "Please open an image first.")
            return

        active = [k for k, rr in self.range_rows.items() if rr.enabled.get()]
        if not active:
            messagebox.showwarning(
                "No sweeps enabled",
                'Enable "Sweep" on at least one property and set from/to values.')
            return

        folder = filedialog.askdirectory(title="Select Output Folder")
        if not folder:
            return

        iterations = self.iter_var.get()
        base = os.path.splitext(os.path.basename(self.image_path))[0]

        def run():
            self.after(0, self.progress.start, 10)
            self.after(0, self._set_status, "Batch exporting…", "#f9e2af")
            for i in range(iterations):
                t = i / max(iterations - 1, 1)
                p = self._get_params(t)
                ascii_out = process_image(
                    self.original_img,
                    p["contrast"], p["brightness"], p["sharpness"],
                    p["scale"], p["columns"], p["mono"], p["invert"],
                    p["space_density"]
                )
                out_path = os.path.join(folder, f"{base}_{i + 1:03d}.txt")
                save_ascii_art(ascii_out, out_path)
                self.after(0, self._set_status,
                           f"Saved {i+1}/{iterations}…", "#f9e2af")
            self.after(0, self.progress.stop)
            self.after(0, self._set_status,
                       f"Done! {iterations} frames saved.", "#a6e3a1")
            self.after(0, messagebox.showinfo, "Batch Export Complete",
                       f"Exported {iterations} frames to:\n{folder}"
                       f"\n\nSwept properties: {', '.join(active)}")

        threading.Thread(target=run, daemon=True).start()


# ──────────────────────────────────────────────

if __name__ == "__main__":
    app = ASCIIArtStudio()
    app.mainloop()