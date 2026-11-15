# How theme detection works

sugar picks palette variants from the terminal's background polarity, dark or light. It reads no env vars itself. supports-color does the detection.

<br>

## Color level

`supports-color.core/profile` gives the color level, 0 to 3, when sugar loads. It reads env vars and TTY status. It does not query the terminal.

<br>

## Polarity

`supports-color.core/theme` gives the polarity on the first `styled` or `sgr` call that uses a named color above level 0, so requiring sugar never touches the terminal.

- `CLITHEME` set: used as is. No terminal query.
- Otherwise: the terminal is queried, with supports-color's default timeout of 2000 ms. A local terminal usually answers in about 35 to 110 ms.
- Browser: `prefers-color-scheme`.

<br>

## Variants

A base color name such as `red` resolves to the same variant for `:color` and `:background-color`. A `subtle-` name such as `subtle-red` resolves to a low-contrast variant:

| Polarity     | `red`        | `subtle-red`    |
| ------------ | ------------ | --------------- |
| dark         | `light-red`  | `shadow-red`    |
| light        | `dark-red`   | `highlight-red` |
| not detected | `medium-red` | No color        |

With no polarity detected, sugar can't tell whether shadow or highlight is the low-contrast one, so a `subtle-` color is left out. As a background, that leaves the terminal's own background behind the text.

<br>

## Example: a CLI app

sugar works out the color level and the polarity once per process and reuses them for every call. This example follows a CLI app that prints log lines with `(sgr :bold.red)` and `(styled msg :green)`.

1. **The app requires `sugar.core`.** sugar reads env vars and checks whether output goes to a terminal to get the color level, 0 to 3. It does not talk to the terminal.
2. **The app makes its first call with a named color.** sugar needs the polarity to pick the right red. If `CLITHEME` is set to `dark` or `light`, sugar uses it, which takes under a millisecond on Babashka and about 5 ms on a cold JVM. Otherwise it asks the terminal for its background color and waits up to 2000 ms for an answer (usually about 35 to 110 ms). This happens once.
3. **The app makes every call after that.** sugar reuses the level and polarity from steps 1 and 2. It also caches the escape codes for each style, so the second `(sgr :bold.red)` is a lookup, which usually takes 100 to 200 nanoseconds.

An app that uses only fixed hex, rgb or 8-bit colors, or user colors (the colors defined by the user's terminal theme), never asks the terminal for its background.

<br>

## Mocking

Pass a supports-color `:context` map as the 3rd arg to `styled`. See `styled` in the [API](api.md).
