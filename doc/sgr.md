Here is the fully updated `sgr.md` document, rendering the diagrams with the cleaner Unicode box-drawing characters (`│`) and the sweeping `╰` terminations you requested. It creates a much more modern, polished look while retaining the old-school technical vibe.

***

# ANSI SGR (Select Graphic Rendition) Protocol

ANSI SGR (Select Graphic Rendition) is the sub-protocol within ANSI escape sequences responsible for text styling: colors, bold, underline, blink, reverse video, and more. Originally popularized in 1978 by the VT100 terminal, these sequences replaced a fragmented landscape of proprietary hardware codes with a unified standard (eventually codified as ECMA-48). 

Under the hood, every terminal styling library shares the exact same fundamental job: wrap a string with `\x1b[…m` sequences and emit a reset (`\x1b[0m`) at the end. While modern libraries diverge dramatically in API design, TTY auto-detection, NO_COLOR compliance, Windows Virtual Terminal handling, and overall scope, they all ultimately compile down to the same byte-level protocol outlined below.

## 1. The 3/4-Bit Syntax (16 Colors)

The original CRT monitors had three electron guns: Red, Green, and Blue. The initial 3-bit ANSI standard allowed you to toggle these guns on or off, resulting in 8 basic colors. The addition of a 4th bit introduced an "intensity" toggle, creating 8 additional "bright" variants, giving us the classic 16-color terminal palette. 



This syntax is universally supported across all terminal emulators. It utilizes dedicated ranges: codes `30-37` for standard foregrounds, `40-47` for standard backgrounds, `90-97` for bright foregrounds (often called `aixterm` colors), and `100-107` for bright backgrounds.

```text
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;;  SGR Escape Code Parsing (16 Colors + Modifiers) [ansi.txt §3/4-BIT SYNTAX]
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;;
;;         \033  [  1 ; 3 ; 4 ; 5 ; 7 ; 8 ; 9 ; 31 ; 105   m
;;           │   │  │ │ │   │   │   │   │   │   │     │    │
;;           │   │  │ │ │   │   │   │   │   │   │     │    ╰ SGR Command "m"
;;           │   │  │ │ │   │   │   │   │   │   │     ╰ Bg Bright Magenta (105)
;;           │   │  │ │ │   │   │   │   │   │   ╰ Fg Red (31)
;;           │   │  │ │ │   │   │   │   │   ╰ Strikethrough (9)
;;           │   │  │ │ │   │   │   │   ╰ Hide (8)
;;           │   │  │ │ │   │   │   ╰ Reverse (7)
;;           │   │  │ │ │   │   ╰ Blink (5)
;;           │   │  │ │ │   ╰ Underline (4)
;;           │   │  │ │ ╰ Italic (3)
;;           │   │  │ ╰ Separator
;;           │   │  ╰ Intensity, Bold or Dim (1, 2)
;;           │   ╰ CSI
;;           ╰ ESC
;;
;;  SGR parameters can be chained indefinitely within a single CSI sequence 
;;  by separating them with semicolons. Order is not strictly enforced by 
;;  the ANSI standard, but convention usually groups modifiers before colors.
;;
;;  SGR Code Glossary:
;;    1,2 = Intensity. Accepts a value of 1 (bold) or 2 (dim).
;;          NOTE: The ECMA-48 spec conflates two conceptually different concepts:
;;                font-weight (a typographic property) and intensity/opacity (a 
;;                color property). ¯\_(ツ)_/¯ Pick one or the other.
;;    3   = Italic / Slanted
;;    4   = Underline (Single)
;;    5   = Blink (Slow)
;;    7   = Reverse / Invert
;;    8   = Hide / Conceal
;;    9   = Crossed-out / Strikethrough
```

## 2. The 8-Bit Syntax (256 Colors)

As terminal applications like `vim` and `emacs` grew more complex in the late 1990s, the 16-color limit became a major constraint for theming and syntax highlighting. The `xterm` emulator pioneered the 256-color extension. 



This standard shifts from specific color codes to a lookup table using `38;5;n` (foreground) and `48;5;n` (background). The 256 indices are strictly mapped:
* **0-15:** The standard 16 basic ANSI colors.
* **16-231:** A 6 × 6 × 6 RGB color cube (216 colors), mathematically derived to cover a broad spectrum.
* **232-255:** A 24-step continuous grayscale ramp, ignoring pure black and pure white.

```text
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;;  SGR Escape Code Parsing (256 Colors + Modifiers)   [ansi.txt §8-BIT SYNTAX]
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;;
;;         \033  [  1 ; 3 ; 4 ; 5 ; 7 ; 8 ; 9 ; 38 ; 5 ; 214   m
;;           │   │  │ │ │   │   │   │   │   │   │    │    │    │
;;           │   │  │ │ │   │   │   │   │   │   │    │    │    ╰ SGR Command "m"
;;           │   │  │ │ │   │   │   │   │   │   │    │    ╰ Color Index (214)
;;           │   │  │ │ │   │   │   │   │   │   │    ╰ 8-bit Format ID (5)
;;           │   │  │ │ │   │   │   │   │   │   ╰ Extended Fg (38)
;;           │   │  │ │ │   │   │   │   │   ╰ Strikethrough (9)
;;           │   │  │ │ │   │   │   │   ╰ Hide (8)
;;           │   │  │ │ │   │   │   ╰ Reverse (7)
;;           │   │  │ │ │   │   ╰ Blink (5)
;;           │   │  │ │ │   ╰ Underline (4)
;;           │   │  │ │ ╰ Italic (3)
;;           │   │  │ ╰ Separator
;;           │   │  ╰ Intensity, Bold or Dim (1, 2)
;;           │   ╰ CSI
;;           ╰ ESC
;;
;;  For 8-bit colors, the parameters following the foreground (38) or 
;;  background (48) declaration become strictly positional, mapping to the 
;;  format ID (5) and the 256-color lookup table (0-255).
;;
;;  SGR Code Glossary:
;;    1,2 = Intensity. Accepts a value of 1 (bold) or 2 (dim).
;;          NOTE: The ECMA-48 spec conflates two conceptually different concepts:
;;                font-weight (a typographic property) and intensity/opacity (a 
;;                color property). ¯\_(ツ)_/¯ Pick one or the other.
;;    3   = Italic / Slanted
;;    4   = Underline (Single)
;;    5   = Blink (Slow)
;;    7   = Reverse / Invert
;;    8   = Hide / Conceal
;;    9   = Crossed-out / Strikethrough
```

## 3. The 24-Bit Syntax (Truecolor)

The modern era of terminal interfaces demands web-like styling capabilities. The 24-bit Truecolor syntax provides access to the full 16.7 million color RGB spectrum. 



This is achieved via `38;2;r;g;b` (foreground) and `48;2;r;g;b` (background), passing absolute values from 0-255 for the red, green, and blue channels. While supported by most modern terminal emulators (iTerm2, Windows Terminal, Kitty, GNOME Terminal, etc.), it is not universally safe for legacy environments. Modern CLI tools often rely on the `COLORTERM=truecolor` environment variable to safely detect and enable this capability at runtime.

```text
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;;  SGR Escape Code Parsing (Truecolor + Modifiers)   [ansi.txt §24-BIT SYNTAX]
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;;
;;         \033  [  1 ; 3 ; 4 ; 5 ; 7 ; 8 ; 9 ; 38 ; 2 ; 255 ; 100 ; 0   m
;;           │   │  │ │ │   │   │   │   │   │   │    │    │     │    │   │
;;           │   │  │ │ │   │   │   │   │   │   │    │    │     │    │   ╰ SGR Command "m"
;;           │   │  │ │ │   │   │   │   │   │   │    │    │     │    ╰ Blue (0)
;;           │   │  │ │ │   │   │   │   │   │   │    │    │     ╰ Green (100)
;;           │   │  │ │ │   │   │   │   │   │   │    │    ╰ Red (255)
;;           │   │  │ │ │   │   │   │   │   │   │    ╰ 24-bit ID (2)
;;           │   │  │ │ │   │   │   │   │   │   ╰ Extended Fg (38)
;;           │   │  │ │ │   │   │   │   │   ╰ Strikethrough (9)
;;           │   │  │ │ │   │   │   │   ╰ Hide (8)
;;           │   │  │ │ │   │   │   ╰ Reverse (7)
;;           │   │  │ │ │   │   ╰ Blink (5)
;;           │   │  │ │ │   ╰ Underline (4)
;;           │   │  │ │ ╰ Italic (3)
;;           │   │  │ ╰ Separator
;;           │   │  ╰ Intensity, Bold or Dim (1, 2)
;;           │   ╰ CSI
;;           ╰ ESC
;;
;;  For 24-bit Truecolor, the magic number changes to "2" to denote an RGB 
;;  sequence. It expects three exact integer parameters (0-255 each) 
;;  representing the Red, Green, and Blue channels respectively.
;;
;;  SGR Code Glossary:
;;    1,2 = Intensity. Accepts a value of 1 (bold) or 2 (dim).
;;          NOTE: The ECMA-48 spec conflates two conceptually different concepts:
;;                font-weight (a typographic property) and intensity/opacity (a 
;;                color property). ¯\_(ツ)_/¯ Pick one or the other.
;;    3   = Italic / Slanted
;;    4   = Underline (Single)
;;    5   = Blink (Slow)
;;    7   = Reverse / Invert
;;    8   = Hide / Conceal
;;    9   = Crossed-out / Strikethrough
```
