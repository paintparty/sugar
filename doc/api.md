# Table of contents
-  [`sugar.core`](#sugar.core)  - Top-level api namespace for public functions which assist in styling text with ANSI SGR, for output to terminal environments.
    -  [`?sgr`](#sugar.core/?sgr) - Prints <code>s</code> with its SGR escape codes shown as text, for debugging.
    -  [`print-styled`](#sugar.core/print-styled) - Prints <code>x</code> styled per <code>style</code>, followed by a newline.
    -  [`print-styled-for-browser-console`](#sugar.core/print-styled-for-browser-console) - Prints <code>s</code> to the browser dev console, with its SGR escape codes converted to CSS.
    -  [`sgr`](#sugar.core/sgr) - Returns the opening ANSI SGR escape sequence for <code>style</code>, without any text or reset.
    -  [`styled`](#sugar.core/styled) - Returns <code>x</code> wrapped in ANSI SGR escape codes for <code>style</code>.

-----
# <a name="sugar.core">sugar.core</a>


Top-level api namespace for public functions which assist in styling text
   with ANSI SGR, for output to terminal environments.

   Supports output for system terminal emulator, as well development consoles in
   web browser dev tool environments.




## <a name="sugar.core/?sgr">`?sgr`</a>
``` clojure

(?sgr s)
```
Function.

Prints `s` with its SGR escape codes shown as text, for debugging.
   Returns `s`.
   
   Each code prints as readable text on a gray background, followed by
   the code itself, so the text keeps its styling.
   
   Show the codes that styled produced
   ```clojure
   (?sgr (styled "hello" :bold.red))
   ```
<p><sub><a href="https://github.com/paintparty/sugar/blob/main/src/sugar/core.cljc#L364-L390">Source</a></sub></p>

## <a name="sugar.core/print-styled">`print-styled`</a>
``` clojure

(print-styled x style)
(print-styled x style supports-color)
```
Function.

Prints `x` styled per `style`, followed by a newline. Returns nil.
   
   Takes the same args as [`styled`](#sugar.core/styled). In a terminal, it is
   `(println (styled x style))`. In a browser, it prints to the dev
   console with the escape codes converted to CSS, through
   [`print-styled-for-browser-console`](#sugar.core/print-styled-for-browser-console).
   
   Print bold red text
   ```clojure
   (print-styled "hello" :bold.red)
   ```
<p><sub><a href="https://github.com/paintparty/sugar/blob/main/src/sugar/core.cljc#L1278-L1303">Source</a></sub></p>

## <a name="sugar.core/print-styled-for-browser-console">`print-styled-for-browser-console`</a>
``` clojure

(print-styled-for-browser-console s)
```
Function.

Prints `s` to the browser dev console, with its SGR escape codes
   converted to CSS.
   
   ClojureScript only. `s` is usually the result of [`styled`](#sugar.core/styled). A value
   that is not a string prints as `print-str` shows it, as `println`
   does in a terminal.
   
   Print styled text in a browser
   ```clojure
   (print-styled-for-browser-console (styled "hello" :bold.red))
   ```
<p><sub><a href="https://github.com/paintparty/sugar/blob/main/src/sugar/core.cljc#L1254-L1276">Source</a></sub></p>

## <a name="sugar.core/sgr">`sgr`</a>
``` clojure

(sgr style)
(sgr style supports-color)
```
Function.

Returns the opening ANSI SGR escape sequence for `style`, without
   any text or reset.
   
   `style` and the optional 2nd arg, `supports-color`, are the same as
   the 2nd and 3rd args of [`styled`](#sugar.core/styled). Use it to style text that is
   built in pieces, and end it with the SGR reset sequence, `ESC[m`.
   
   At level 0, or when `style` produces no codes, returns an empty
   string.
   
   An options map
   ```clojure
   (sgr {:color :red, :font-weight :bold})
   ```
   
   A dot-delimited string or keyword
   ```clojure
   (sgr :bold.red.bg-blue)
   ```
<p><sub><a href="https://github.com/paintparty/sugar/blob/main/src/sugar/core.cljc#L1210-L1250">Source</a></sub></p>

## <a name="sugar.core/styled">`styled`</a>
``` clojure

(styled x style)
(styled x style supports-color)
```
Function.

Returns `x` wrapped in ANSI SGR escape codes for `style`.
   
   `style` is an options map, or a dot-delimited string or keyword
   such as `"bold.red.bg-blue"` or `:bold.red.bg-blue`. In a string
   or keyword, each token is a font weight or style, the name of a
   boolean option, an underline style followed by `-underline`
   (`wavy-underline`), a color, or `bg-` followed by a color.
   
   Colors are downsampled to the color level the terminal supports.
   At level 0, or when `style` produces no codes, `x` is returned
   unchanged.
   
   `x` is not sanitized. Escape codes in it reach the terminal as they
   are, so strip them first when `x` comes from untrusted input.
   
   The optional 3rd arg, `supports-color`, is a supports-color
   :context map (`{:env {...} :runtime {...}}`), for mocking in tests.
   Its entries override the live process reads, and anything it
   leaves out still comes from the real process. An env var missing
   from :env is read from the real environment, so a test that needs
   a fixed context sets every other key in
   `supports-color.core/env-var-keys` to nil. The color level and the
   mood are resolved from it for this call only. A mocked context
   never queries the real terminal, unless it supplies :runtime :tty
   itself.
   
   In browsers the level is always 3, and the mood follows
   prefers-color-scheme.
   
   An options map
   ```clojure
   (styled "hello" {:color :red, :underline true})
   ```
   
   A dot-delimited string or keyword
   ```clojure
   (styled "hello" "bold.red.bg-blue")
   
   (styled "hello" :bold.red.bg-blue)
   ```
   
   Hex, rgb and 8-bit colors
   ```clojure
   (styled "hello" {:color "#ff5f00"})
   
   (styled "hello" {:color [255 95 0]})
   
   (styled "hello" {:color 202})
   ```
   
   A mocked context, for tests: 256 colors on a dark background
   ```clojure
   (styled "hello" :red {:env {"FORCE_COLOR" "2", "CLITHEME" "dark"}})
   ```
   
   Options, when `style` is a map. A boolean option is on only when it is `true`:
   
   * **`:color`**
       - `[:or :string :keyword [:tuple :int :int :int] :int]`
       - Optional.
       - A palette name such as `:red` or `"light-blue"`, a hex string such as `"#ff5f00"`, an rgb vector such as `[255 95 0]`, or an 8-bit color id from 0 to 255. A plain name such as `:red` resolves to a variant that reads well on the terminal's dark or light background. The `user-` names, such as `:user-red`, use the terminal's own 16 colors. A `subtle-` name, such as `:subtle-blue`, is a low-contrast version of the color: the shadow variant on a dark background and the highlight variant on a light one. It is left out when the background is not detected. `:max` is white on a dark background and black on a light one. `:neutral` is the terminal's own text color.
   
   * **`:background-color`**
       - `[:or :string :keyword [:tuple :int :int :int] :int]`
       - Optional.
       - A color, in any form that `:color` accepts, resolved the same way. Use a `subtle-` name, such as `:subtle-blue`, for a low-contrast background behind the terminal's own text color. `:max` and `:neutral` leave the background unchanged.
   
   * **`:font-weight`**
       - `#{:bold :dim :normal}`
       - Optional.
   
   * **`:font-style`**
       - `#{:normal :italic}`
       - Optional.
   
   * **`:underline`**
       - `boolean?`
       - Optional.
   
   * **`:underline-style`**
       - `#{:double :wavy :straight :dashed :dotted}`
       - Optional.
       - Defaults to `:straight`.
       - Applies only when `:underline` is `true`.
   
   * **`:strikethrough`**
       - `boolean?`
       - Optional.
   
   * **`:reverse`**
       - `boolean?`
       - Optional.
       - Swaps the text and background colors.
   
   * **`:hide`**
       - `boolean?`
       - Optional.
       - Hides the text. It still takes up its space.
   
   * **`:blink`**
       - `boolean?`
       - Optional.
   
   * **`:validate-options`**
       - `boolean?`
       - Optional.
       - Defaults to `true`.
       - Prints a warning for each invalid value. Invalid values are dropped either way.
   
   * **`:print-warnings`**
       - `boolean?`
       - Optional.
       - Defaults to `true`.
       - Set to `false` to silence the warnings while validation is on. An unknown token in a string or keyword style always warns.
   
   * **`:dev/print-profile`**
       - `boolean?`
       - Optional.
       - Prints the resolved color level, polarity and palette variants.
<p><sub><a href="https://github.com/paintparty/sugar/blob/main/src/sugar/core.cljc#L986-L1207">Source</a></sub></p>
