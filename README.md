# Clojure Playground

A Clojure-native browser app that introduces Clojure through interactive examples:

- data-first programming with vectors and maps
- thread-style transformation pipelines
- controlled state with atom-like transactions
- REPL-style experimentation

The application logic, HTML rendering, routing, and HTTP server are written in Clojure. It uses Java's built-in `HttpServer`, so there is no Node server and no handwritten JavaScript.

## Run

```powershell
clojure -M:run
```

Then open:

```text
http://127.0.0.1:5179/
```

You need the Clojure CLI installed and available on `PATH`.
