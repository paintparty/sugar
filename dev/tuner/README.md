# Palette tuner

The palette tuner is a browser page for tuning sugar's 91 colors by eye, on dark and light backgrounds, in truecolor and 8-bit. It edits `palette.json` in this directory. sugar's own colors in `src/sugar/data.cljc` change only when you push.

## Run

From the repository root:

```bash
bb tuner
```

This serves the page at http://localhost:9631 and opens it. Ctrl-C stops it. Opening `index.html` directly does not work, because the page loads `palette.json` from the server.

## Buttons

- **Save** writes your changes to `palette.json`.
- **Reset** discards changes since the last save.

## Update sugar

1. Tune and save.
2. `bb tuner:push` writes `palette.json` into the color maps in `src/sugar/data.cljc`.
3. `bb test:snapshot`, then review the diff of `test/sugar/snapshots.cljc`.

If you edit `data.cljc` by hand, `bb tuner:pull` copies it back to `palette.json`.

## palette.json

One entry per color, with its truecolor RGB and its 8-bit index:

```json
"medium-cyan": {"truecolor": [18, 199, 163], "8bit": 43}
```

Save and push reject a file that adds, drops or misspells a color, or has a value outside 0 to 255.

## Requirements

Babashka, and an internet connection: the page loads Tailwind, Alpine and iro.js from CDNs.
