# Sugar

**Flexible ANSI SGR utility for styling text in console environments**<br>
<sub>Works with Clojure, ClojureScript, and Babashka</sub>

<br/>
<br/>

The Sugar API offers:

- **Styles text** with ANSI SGR codes: color, background color, bold, italic, underline styles, and more
- **Adapts to the user's terminal**: picks palette variants for a dark or light background, and downsamples colors to the supported color level for your terminal
- **Converts to CSS** for browser dev consoles

```clojure
(require '[sugar.core :refer [styled print-styled]])


(styled "hello" {:color :red :font-weight :bold :background-color :blue})
=> "\033[1;38;2;255;0;0;48;2;0;175;255mhello\033[m"


;; Same as above, using a tokenized style keyword
(styled "hello" :red.bold.bg-blue)
=> "\033[1;38;2;255;0;0;48;2;0;175;255mhello\033[m"
```

sugar does not sanitize the text. Escape codes already in it reach the terminal unchanged.

<br>

## Development Status

| Period           | Stage        | Release |
| ---------------- | ------------ | ------- |
| 2026 April       | Experimental |         |
| 2026 Nov-present | Alpha        | 0.1.0   |

<br />

## Quick comparison
Comparison with other libs from other languages that you may be familiar with:
|  | sugar | [chalk↗](https://github.com/chalk/chalk) | [lipgloss↗](https://github.com/charmbracelet/lipgloss) |
|---|---|---|---|
| Language | Clojure dialects | JavaScript, Node | Go |
| Universal palette for both dark and light backgrounds | ✓ | ✕ | ✕ |
| Palette variants for dark and light backgrounds | ✓ | ✕ | ✓ |
| Automatic chromatic background highlighting | ✓ | ✕ | ✕ |
| Downsamples to the supported color level | ✓ | ✓ | ✓ |
| Hex and RGB colors | ✓ | ✓ | ✓ |
| CSS stylemap syntax | ✓ | ✕ | ✕ |
| Browser dev console output (CSS) | ✓ | ✕ | ✕ |


<br />

## Requirements

Tested with Clojure 1.12. Also runs on Babashka and ClojureScript.

<br />

## Setup

Add as a dependency to your project:

Deps:

```clojure
io.github.paintparty/sugar {:mvn/version "0.1.0"}
```

Leiningen:

```clojure
[io.github.paintparty/sugar "0.1.0"]
```

Require it:

```Clojure
(require '[sugar.core :refer [styled]])
```

Or import into your namespace:

```Clojure
(ns myns.core
  (:require
    [sugar.core :refer [styled]]))
```

### Consuming via git

Using a tool without Maven support? See
[Consuming sugar as a git dependency](doc/git-deps.md).

<br />


## Documentation

**[API](doc/api.md)**<br>
The public api.

**[Style options](doc/style-options.md)**<br>
Every option in the map form and the string form.

**[Colors](doc/colors.md)**<br>
The named palette, hex, RGB, and 8-bit colors.

**[Browser dev consoles](doc/browser-consoles.md)**<br>
How styled text is converted to CSS for browser dev consoles.

**[How theme detection works](doc/how-theme-detection-works.md)**<br>
How sugar picks palette variants for a dark or light background.

**[ANSI SGR](doc/sgr.md)**<br>
ANSI SGR basics, with visual diagrams.

**[Developing](doc/developing.md)**<br>
Running the tests on each runtime, building and releasing, and regenerating the git-deps docs.

<br />

## Contributing

Issues for bugs, improvements, or features are very welcome.

Please file an issue for discussion before starting or submitting a PR.

If you want to submit a PR against an open issue, start by dropping a message in the open issue before doing any work or issuing a PR, in order to initiate human communication, and make sure everyone is on the same page.

<br />

## License

Copyright © 2026 Jeremiah Coyle

Distributed under the [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0)
