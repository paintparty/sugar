# Colors

`:color` and `:background-color` take a palette name, a hex string, an rgb vector, or an 8-bit id.

```clojure
(styled "hello" {:color :red})
(styled "hello" {:color "#ff5f00"})
(styled "hello" {:color [255 95 0]})
(styled "hello" {:color 202})
```

<br>

## Palette

Base colors: `red` `orange` `yellow` `olive` `green` `cyan` `blue` `purple` `magenta` `gray` `black` `white`.

A base name picks a variant for the terminal's background. See [How theme detection works](how-theme-detection-works.md).

Name a variant to use it directly:

| Prefix         | Example            |
| -------------- | ------------------ |
| `extra-light-` | `extra-light-blue` |
| `light-`       | `light-blue`       |
| `medium-`      | `medium-blue`      |
| `dark-`        | `dark-blue`        |
| `extra-dark-`  | `extra-dark-blue`  |
| `soft-`        | `soft-blue` (no `soft-gray`) |
| `shadow-`      | `shadow-blue`      |
| `highlight-`   | `highlight-blue`   |

Run `bb visual:palette` to see them all.

<br>

## Subtle colors

A `subtle-` name, such as `subtle-blue`, is a low-contrast version of a base color. It resolves to `shadow-blue` on a dark background and `highlight-blue` on a light one, and is left out when the background is not detected. Use it for a background behind the terminal's own text color:

```clojure
(styled "hello" {:background-color :subtle-blue})
```

There is a subtle name for every base color except `black` and `white`.

<br>

## Traditional 16-color terminal palettes

Terminals have their own palette of 16 colors, set by the terminal theme. A theme can make "red" any color, so the same code looks different from one terminal to the next.

sugar's palette uses fixed rgb values instead, so a color looks the same in every terminal, and its variants are tuned for dark and light backgrounds.

You can still use the terminal's 16 colors, with the `user-` names:

`user-black` `user-red` `user-green` `user-yellow` `user-blue` `user-magenta` `user-cyan` `user-white`, and a `user-bright-` version of each.

There is no orange, olive or purple among them.

At color level 1, sugar's colors fall back to the nearest of these 16.

<br>

## Special names

| Name       | Result                                                             |
| ---------- | ------------------------------------------------------------------ |
| `user-red`, `user-bright-red`, ... | The terminal's own 16 colors.              |
| `max`      | Text only. White on a dark background, black on a light one.       |
| `neutral`  | The terminal's own color. Emits nothing.                           |

<br>

## Hex, rgb and 8-bit

- Hex: 3, 4, 6 or 8 digits. The `#` is optional. Alpha is ignored.
- rgb: `[r g b]`, each from 0 to 255.
- 8-bit: an id from 0 to 255.

<br>

## Color levels

| Level | Output                          |
| ----- | ------------------------------- |
| 3     | Truecolor                       |
| 2     | Nearest of the 256 8-bit colors |
| 1     | Nearest of the 16 user colors   |
| 0     | No codes. Text is unchanged.    |
