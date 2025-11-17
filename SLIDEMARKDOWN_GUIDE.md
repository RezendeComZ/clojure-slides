# Slide Markdown (.smd) Format Guide

The `.smd` format is a custom file structure designed to create self-contained HTML presentations. It consists of two main parts separated by an `END` marker:

1.  **The EDN Header**: Defines metadata and templates.
2.  **The Slide Content**: Markdown blocks for each slide.

---

## 1. The EDN Header

The file **must** begin with a Clojure EDN (Extensible Data Notation) map. This map defines the presentation's overall title and, most importantly, the slide templates.

```clojure
{:title "My Awesome Presentation"
 :templates [
   {;; --- Template 1: Title Slide ---
    :slide-template "title"
    :template-name "TitleSlide_HorizontalSplit"
    :background {:type "solid_layers"
                 :orientation "horizontal"
                 :layers [{:color "#0F9B8E" :proportion "33.3%"}
                          {:color "#000000" :proportion "66.7%"}]}
    :elements [{:type "text"
                :role "#"
                :style {:color "#FFFFFF", :alignment "left"}
                :position {:x "5%", :y "40%"}}
               
               {:type "text"
                :role "###"
                :style {:color "#FFFFFF", :alignment "left"}
                :position {:x "5%", :y "50%"}}]}

   {;; --- Template 2: Two-Column Media ---
    :slide-template "media-right"
    :template-name "TwoColumn_VerticalSplit_Media"
    :background {:type "solid_layers"
                 :orientation "vertical"
                 :layers [{:color "#FDFBF4" :proportion "50%"}
                          {:color "#000000" :proportion "50%"}]}
    :elements [{:type "text" ; Element 1
                :role "#"
                :style {:color "#0F9B8E", :alignment "left"}
                :position {:x "5%", :y "15%"}}
               
               {:type "image" ; Element 2
                :position {:x "5%", :y "25%"}}
               
               {:type "video" ; Element 3
                :autoplay false
                :controls true
                :position {:x "55%", :y "15%"}}]}]}
```

### Template Keys:

* `:slide-template`: A **unique string ID** for this template.
* `:template-name`: A human-readable name.
* `:background`: Defines the background. Currently supports `:type "solid_layers"` with `:orientation ("horizontal" | "vertical")` and a vector of `:layers` (color and proportion).
* `:elements`: A **vector** of content placeholders. The **order** of elements here is critical.
    * `:type`: Can be `"text"`, `"image"`, or `"video"`.
    * `:style`: (For `text`) Defines CSS properties like `:color` and `:alignment`.
    * `:position`: Defines the absolute position using `:x` and `:y` (CSS percentages).
    * `(video keys)`: `:autoplay` (boolean), `:controls` (boolean).

### Default Template

The **first** template in the `:templates` vector (e.g., `:slide-template "title"` above) is the **default template**. It will be used for any slide that does not specify a template ID.

---

## 2. The `END` Separator

After the EDN block, you **must** place `END` on its own line to separate the header from the slide content.

```clojure
} ; ... end of EDN map
END
```

---

## 3. The Slide Content

Slides are separated by `-*-*-` on its own line.

```markdown
-*-*- [template-id] Optional Slide Title
```

* `[template-id]`: **Optional**. This is the `:slide-template` ID from the header (e.g., `[media-right]`). If omitted, the **default template** is used.
* `Optional Slide Title`: **Optional**. If provided, this text is used in the navigation dropdown menu (e.g., "1 - Introduction").

### Content Block Mapping

The content for each slide is standard Markdown. The parser separates your content into **blocks** (separated by one or more blank lines).

These blocks are mapped **sequentially** to the `:elements` vector in the slide's template.

**You must provide exactly one block for each element defined in the template.**

#### Example:

Given the `"title"` template (which expects 2 text elements):

```clojure
:elements [{:type "text"}
           {:type "text"}]
```

The slide content **must** provide 2 blocks:

```markdown
-*-*- [title] Introduction
# Density Experiment
### By Your Name
```

* `# Density Experiment` is **Block 1**. It maps to `Element 1`.
* `### By Your Name` is **Block 2**. It maps to `Element 2`.

---

Given the `"media-right"` template (expects `text`, `image`, `video`):

```clojure
:elements [{:type "text"}
           {:type "image"}
           {:type "video"}]
```

The slide content **must** provide 3 blocks:

```markdown
-*-*- [media-right] The Experiment
# Materials
Found around the house!

![Alt text for the image](./assets/my-image.png)

./assets/my-video.mp4
```

* **Block 1 (Text):** `# Materials\nFound around the house!`
* **Block 2 (Image):** `![Alt text for the image](./assets/my-image.png)`
    * *Note: For images, you can use Markdown syntax `![alt](path)` or just the `path`.*
* **Block 3 (Video):** `./assets/my-video.mp4`
    * *Note: For videos, just provide the file path.*

All media paths are relative to the location of the `.smd` file and will be "
"embedded (Base64) into the final HTML.