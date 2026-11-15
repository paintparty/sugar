# Style options

`sugar.core/styled` takes the style as a map, or as a dot-delimited string or keyword.

```clojure
(styled "hello" {:color :red :font-weight :bold :background-color :blue})
(styled "hello" "red.bold.bg-blue")
(styled "hello" :red.bold.bg-blue)
```

<br>

## Map

| Option              | Values                                                  |
| ------------------- | ------------------------------------------------------- |
| `:color`            | A [color](colors.md)                                    |
| `:background-color` | A [color](colors.md)                                    |
| `:font-weight`      | `:bold` `:dim` `:normal`                                |
| `:font-style`       | `:italic` `:normal`                                     |
| `:underline`        | `true`                                                  |
| `:underline-style`  | `:straight` `:double` `:wavy` `:dotted` `:dashed`. Needs `:underline true`. |
| `:strikethrough`    | `true`                                                  |
| `:reverse`          | `true`                                                  |
| `:hide`             | `true`                                                  |
| `:blink`            | `true`                                                  |
| `:validate-options` | Defaults to `true`. Warns about invalid values.         |
| `:print-warnings`   | Defaults to `true`. `false` silences the warnings.      |
| `:dev/print-profile`| `true` prints the detected color level and polarity.   |

A boolean option is on only when it is `true`. Values can be keywords or strings.

<br>

## String or keyword

Tokens are separated by `.`, in any order.

| Token                          | Example             |
| ------------------------------ | ------------------- |
| Font weight or style           | `bold` `italic`     |
| Boolean option                 | `underline` `reverse` |
| Underline style + `-underline` | `wavy-underline`    |
| Color                          | `red` `light-blue`  |
| `bg-` + color                  | `bg-blue`           |

An unknown token always prints a warning.
