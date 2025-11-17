# SlideMD (Slide Markdown)

**SlideMD** is a lightweight, command-line tool written in Clojure (running on Babashka) that converts a custom text format (`.smd`) into a **self-contained, single-file HTML presentation**.

It is designed for developers who want to write presentations in Markdown, use custom layouts defined as data (EDN), and share a single HTML file that works offline without dependencies.

## ✨ Features

* **Self-Contained Output:** Images, CSS, and JavaScript are embedded directly into the HTML. No external folders required to present.
* **Markdown Support:** Full GFM (GitHub Flavored Markdown) support, including tables, lists, and images.
* **Syntax Highlighting:** Automatic syntax highlighting for code blocks using Prism.js (cached locally).
* **Custom Templates:** Define complex slide layouts using EDN (JSON-like data structure).
* **Responsive:** Slides scale automatically using viewport units (`vh`), looking good on any screen size.
* **Developer Friendly:** Written in Clojure, powered by Babashka.

## 🚀 Prerequisites

You need **Babashka** installed to run this tool.

* [Install Babashka](https://github.com/babashka/babashka#installation)
    * macOS: `brew install borkdude/brew/babashka`
    * Linux: `bash < <(curl -s https://raw.githubusercontent.com/babashka/babashka/master/install)`
    * Windows: `scoop install babashka`

## 🏃 Usage

1.  Create your `.smd` file (e.g., `presentation.smd`).
2.  Run the script:

```bash
# If you set up a bb.edn (Recommended)
bb slide_markdown.clj presentation.smd

# Or running directly
./slide_markdown.clj presentation.smd
```

3.  Open the generated `presentation.html` in your browser.

**Note:** On the first run, the tool requires an internet connection to download the Prism.js themes and scripts to a local `.slide-cache` folder. Subsequent runs work offline.

## 📄 The `.smd` File Format

The `.smd` file is split into two parts separated by the keyword `END`.

1.  **Header (EDN):** Configuration and Template definitions.
2.  **Body (Markdown):** The actual slide content.

### Structure Example

```clojure
{:title "My Awesome Talk"
 :templates [
   {:slide-template "default"
    :background {:type "solid_layers"
                 :orientation "horizontal"
                 :layers [{:color "#333" :proportion "100%"}]}
    :elements [{:type "text" :role "#" :position {:x "5%" :y "10%"}}
               {:type "text" :position {:x "5%" :y "30%"}}]}]}
END
-*-*- [default] First Slide Title
# Hello World

This is the body content.
```

---

## 🎨 Creating Templates (Header)

Templates are defined in the `:templates` vector in the header.

### Template Properties
* `:slide-template`: Unique ID used to reference this template in the slides (e.g., `"split-view"`).
* `:background`: Defines the background style.
    * Currently supports `solid_layers` (CSS gradients).
    * `:orientation`: `"horizontal"` or `"vertical"`.
    * `:layers`: List of colors and their proportions.
* `:elements`: A list of content slots.
    * `:type`: `"text"`, `"image"`, or `"video"`.
    * `:position`: `:x` and `:y` coordinates (CSS percentages).
    * `:style`: Optional CSS overrides (color, alignment).

### The "Greedy" Element Rule
The script maps Markdown blocks to Template elements in order.
* If you have **2 elements** defined in the template...
* ...and you provide **4 blocks** of Markdown content...
* Block 1 goes to Element 1.
* **Blocks 2, 3, and 4 are combined** and put into Element 2.

---

## ✍️ Writing Slides (Body)

Slides are separated by the marker `-*-*-`.

### Slide Header Syntax
Each slide starts with the separator, followed by the template ID and the slide title.

```text
-*-*- [template-id] Optional Slide Title
```

* `[template-id]`: Must match a `:slide-template` from the header. If omitted, it defaults to the first template defined.

### Slide Content
Content is written in standard Markdown. Separate distinct "blocks" (that map to different template elements) with **blank lines**.

**Example:**

```markdown
-*-*- [split-view] Comparison Slide
# Left Column Content

This paragraph goes to the first available slot (Left).

<-- Blank Line separates blocks -->

# Right Column Content

This paragraph goes to the second available slot (Right).
```

### Code Blocks
Use triple backticks for code. Syntax highlighting is applied automatically.

```clojure
(defn hello [name]
  (println "Hello," name))
```

---

## ⌨️ Keyboard Shortcuts (Presentation Mode)

* `Arrow Right` / `Space`: Next Slide
* `Arrow Left`: Previous Slide
* `F` / Button: Toggle Fullscreen

## 🛠 Troubleshooting

* **Colors/Theme not showing?**
    Try clearing the cache if you suspect a corrupted download: `rm -r .slide-cache`.
* **Validation Errors?**
    The script validates that you provided enough content blocks for the chosen template. Ensure you use blank lines to separate your Markdown content correctly.