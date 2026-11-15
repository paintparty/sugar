# Browser dev consoles

In a browser, `sugar.core/print-styled` prints to the dev console with CSS in place of the escape codes.

```clojure
(print-styled "hello" :bold.red)
```

<br>

## How it works

`sugar.browser-dev-console/ansi-sgr-string->browser-dev-console-array` turns a styled string into a format string with a `%c` per escape code, followed by one CSS string per `%c`.

```clojure
(ansi-sgr-string->browser-dev-console-array "\u001b[1mhi\u001b[m")
;; => ["%chi%c" "font-weight: bold; line-height: 1.5" "line-height: 1.5"]
```

`sugar.core/print-styled-for-browser-console` passes that array to `console.log`.

<br>

## Notes

- The color level is always 3.
- The background polarity comes from `prefers-color-scheme`.
- `%` in the text is escaped as `%%`.
- `:reverse` swaps the colors. Without colors it uses `Canvas` and `CanvasText`.
- `:hide` makes the text transparent.
- `:blink` is dropped.
