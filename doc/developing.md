# Developing

## Running tests

```
$ bb test            # Clojure tests
$ bb test:bb         # Babashka tests
$ bb test:node       # ClojureScript tests under Node (shadow-cljs)
$ bb test:nbb        # ClojureScript tests under nbb
$ bb test:all        # all of the above
$ bb test:coverage   # Clojure tests with cloverage
$ bb test:snapshot   # rewrite the snapshots, then review the diff
$ bb test:generate   # regenerate the generated tests
```

<br />

## Looking at output

```
$ bb visual:palette     # palette grids and text decorations
$ bb visual:samples     # every generated sample
$ bb visual:warnings    # one warning box per kind of invalid option
$ bb visual:platforms   # detected profile on nbb, Node, Babashka and the JVM
```

<br />

## supports-color

```
$ bb deps:local       # use the checkout at ../../cljc/supports-color
$ bb deps:published   # use the published version
```

<br />

## Build and release

Run the CI pipeline and build a JAR:

```
$ bb ci
```

Update the version in `build.clj` first. Run `bb deps:published` first too, so the pom has no local path.

Install it locally (requires `ci` to have run first):

```
$ bb install
```

Run CI and deploy to Clojars (needs `CLOJARS_USERNAME` and `CLOJARS_PASSWORD` environment variables):

```
$ bb ci:deploy
```

Deploys to `io.github.paintparty/sugar` on clojars.org.

<br />

## Docs

```
$ bb docs:docstrings   # write docstrings in sugar.core from their metadata maps
$ bb docs:api          # write doc/api.md from those docstrings
```

<br />

## Regenerating git-deps.md

This doc needs to be regenerated after a new release is tagged. Run `bb docs:git-deps`, commit the regenerated `git-deps.md` and push to `main`.
