#!/usr/bin/env bb
(ns slide-markdown
  "Converts a .smd (Slide Markdown) file into a self-contained HTML presentation."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [markdown.core :as md])
  (:import [java.util Base64]
           [java.io File FileInputStream ByteArrayOutputStream]
           [java.net URLEncoder]))

;; --- Helper: CDN Asset Caching ---

(defn- fetch-cdn-asset
  "Fetches a text asset from a URL. Caches it locally in .slide-cache"
  [url file-name]
  (let [cache-dir (io/file ".slide-cache")
        cache-file (io/file cache-dir file-name)]
    (when-not (.exists cache-dir)
      (println (str "Creating cache dir: " cache-dir))
      (.mkdirs cache-dir))
    (if (.exists cache-file)
      (slurp cache-file)
      (do
        (println (str "Downloading " file-name " from CDN..."))
        (let [content (slurp url)]
          (spit cache-file content)
          content)))))

;; --- Core Parsing Logic ---

(defn- parse-slide-header
  "Parses the slide header (e.g., '[template-id] Title')."
  [header-line]
  (let [pattern #"^\[([\w\d-]+)\]\s*(.*)$"
        match (re-find pattern header-line)]
    (if match
      {:template-id (second match)
       :title (when-not (str/blank? (nth match 2)) (nth match 2))}
      {:template-id nil
       :title (when-not (str/blank? header-line) header-line)})))

(defn- parse-slide-content
  "Parses a raw slide string into a structured map."
  [raw-slide]
  (let [lines (str/split-lines raw-slide)
        header-line (first lines)
        content-str (str/join "\n" (rest lines))
        blocks (->> (str/split content-str #"\n\s*\n")
                    (map str/trim)
                    (remove str/blank?)
                    (vec))]
    (assoc (parse-slide-header header-line)
           :markdown-blocks blocks)))

(defn parse-smd-file
  "Parses the .smd file content into a presentation map."
  [file-content]
  (let [parts (str/split file-content #"\nEND\n" 2)
        edn-header (first parts)
        slides-content (second parts)]
    (when-not slides-content
      (throw (ex-info "Invalid .smd file: 'END' separator not found." {})))

    {:meta (edn/read-string edn-header)
     :slides (->> (str/split slides-content #"-\*-\*-\s*")
                  (remove str/blank?)
                  (map parse-slide-content)
                  (vec))}))

;; --- Validation Logic ---

(defn- validate-templates
  "Validates the :templates block in the metadata."
  [templates]
  (when (or (nil? templates) (empty? templates))
    (throw (ex-info "Validation Failed: No templates found in EDN header." {})))

  (let [ids (map :slide-template templates)]
    (when (not (apply distinct? ids))
      (throw (ex-info "Validation Failed: Template :slide-template IDs are not unique."
                      {:ids ids})))
    (when (some str/blank? ids)
      (throw (ex-info "Validation Failed: Template :slide-template IDs cannot be blank." {})))))

(defn- get-template-map
  "Creates a lookup map for templates by their ID."
  [templates]
  (->> templates
       (map (juxt :slide-template identity))
       (into {})))

(defn validate-presentation-data
  "Validates the parsed presentation data."
  [{:keys [meta slides] :as presentation-data}]
  (validate-templates (:templates meta))

  (let [template-map (get-template-map (:templates meta))
        default-template-id (-> meta :templates first :slide-template)]

    (doseq [[i slide] (map-indexed vector slides)]
      (let [template-id (or (:template-id slide) default-template-id)
            template (get template-map template-id)]

        (when-not template
          (throw (ex-info (str "Validation Failed: Slide " (inc i)
                               " ('" (:title slide) "') uses non-existent template-id '"
                               template-id "'.")
                          {:slide (inc i) :template-id template-id})))

        (let [expected-count (count (:elements template))
              provided-count (count (:markdown-blocks slide))]
          (when (< provided-count expected-count) ; <-- MUDANÇA AQUI
            (throw (ex-info (str "Validation Failed: Slide " (inc i)
                                 " ('" (:title slide) "') for template '" (:template-name template)
                                 "' expects at least " expected-count " content block(s), but "
                                 provided-count " were provided.")
                            {:slide (inc i)
                             :template-name (:template-name template)
                             :expected expected-count
                             :provided provided-count})))))))
  presentation-data)

;; --- Media & Style Generation ---

(defn- guess-mime-type
  "Guesses MIME type from file path. Add more as needed."
  [path]
  (let [ext (second (re-find #"\.([a-zA-Z0-9]+)$" path))]
    (case (str/lower-case ext)
      "png" "image/png"
      "jpg" "image/jpeg"
      "jpeg" "image/jpeg"
      "gif" "image/gif"
      "svg" "image/svg+xml"
      "webp" "image/webp"
      "mp4" "video/mp4"
      "webm" "video/webm"
      "ogg" "video/ogg"
      "application/octet-stream"))) ; Default

(defn- encode-file-to-base64
  "Reads a file and returns its Base64-encoded string."
  [base-dir file-path]
  (let [file (io/file base-dir file-path)]
    (if-not (.exists file)
      (throw (ex-info (str "Media file not found: " file-path)
                      {:path (str file)})))
    (with-open [in (FileInputStream. file)
                out (ByteArrayOutputStream.)]
      (io/copy in out)
      (.encodeToString (Base64/getEncoder) (.toByteArray out)))))

(defn- extract-image-path
  "Extracts path from markdown image syntax ![alt](path)."
  [markdown-block]
  (or (second (re-find #"!\[.*\]\((.*?)\)" markdown-block))
      markdown-block)) ; Fallback if just a path

(defn- build-css-gradient
  "Creates a linear-gradient CSS string from template background."
  [{:keys [orientation layers]}]
  (let [direction (if (= "vertical" orientation) "to right" "to bottom")
        stops (loop [remaining layers
                     current-pos 0.0
                     acc []]
                (if-let [layer (first remaining)]
                  (let [prop (Double/parseDouble (str/replace (:proportion layer) "%" ""))
                        next-pos (+ current-pos prop)]
                    (recur (rest remaining)
                           next-pos
                           (conj acc (str (:color layer) " " current-pos "% " next-pos "%"))))
                  acc))]
    (str "linear-gradient(" direction ", " (str/join ", " stops) ")")))

(defn- build-position-style
  "Creates CSS position styles from a template element."
  [element]
  (let [pos (:position element)
        font (:style element)]
    (str "position: absolute; "
         (when (:x pos) (str "left: " (:x pos) "; "))
         (when (:y pos) (str "top: " (:y pos) "; "))
         (when (:color font) (str "color: " (:color font) "; "))
         (when (:alignment font) (str "text-align: " (:alignment font) "; ")))))

;; --- HTML Generation ---

(defn- generate-slide-content
  "Generates HTML for the content elements of a single slide."
  [template-elements markdown-blocks base-dir]
  (let [elements template-elements
        blocks markdown-blocks
        element-count (count elements)
        block-count (count blocks)
        pairs (cond
                (>= block-count element-count)
                (let [last-element-index (dec element-count)
                      head-elements (take last-element-index elements)
                      head-blocks (take last-element-index blocks)
                      head-pairs (map vector head-elements head-blocks)
                      last-element (nth elements last-element-index)
                      remaining-blocks (drop last-element-index blocks)
                      last-block (str/join "\n\n" remaining-blocks)
                      last-pair [last-element last-block]]
                  (vec (conj (vec head-pairs) last-pair)))
                :else
                (map vector elements blocks))]

    (str/join "\n"
              (map (fn [[element block]]
                     (when block
                       (let [style (build-position-style element)]
                         (case (:type element)
                           "text"
                           (let [raw-html (md/md-to-html-string block :spec :gfm)
                                 fixed-html (str/replace raw-html
                                                         #"<code class=\"(\w+)\">"
                                                         "<code class=\"language-$1\">")]
                             (str "<div class=\"content-element text-content\" style=\"" style "\">"
                                  fixed-html
                                  "</div>"))

                           "image"
                           (let [path (extract-image-path block)
                                 mime (guess-mime-type path)
                                 b64 (encode-file-to-base64 base-dir path)]
                             (str "<div class=\"content-element image-content\" style=\"" style "\">"
                                  "<img src=\"data:" mime ";base64," b64 "\" alt=\"Embedded Image\">"
                                  "</div>"))

                           "video"
                           (let [path block
                                 mime (guess-mime-type path)
                                 b64 (encode-file-to-base64 base-dir path)
                                 controls (if (false? (:controls element)) "" "controls")
                                 autoplay (if (:autoplay element) "autoplay muted" "")]
                             (str "<div class=\"content-element video-content\" style=\"" style "\">"
                                  "<video " controls " " autoplay " src=\"data:" mime ";base64," b64 "\">"
                                  "Your browser does not support the video tag."
                                  "</video></div>"))

                           (str "<div style=\"" style "\">Unsupported element type: " (:type element) "</div>")))))
                   pairs))))

(defn- generate-slide-html
  "Generates HTML for a single slide."
  [slide index template-map default-template-id base-dir]
  (let [template-id (or (:template-id slide) default-template-id)
        template (get template-map template-id)
        background-style (build-css-gradient (:background template))]
    (str "<div class=\"slide\" id=\"slide-" index "\" style=\"background: " background-style ";\">"
         (generate-slide-content (:elements template) (:markdown-blocks slide) base-dir)
         "</div>")))

(defn- generate-slide-options
  "Generates <option> tags for the slide navigation dropdown."
  [slides]
  (str/join "\n"
            (map-indexed
             (fn [i slide]
               (let [title (or (:title slide) (str "Slide " (inc i)))
                     label (if (:title slide)
                             (str (inc i) " - " title)
                             (str (inc i)))]
                 (str "<option value=\"" i "\">" label "</option>")))
             slides)))

(defn- generate-css
  "Generates the embedded CSS for the presentation."
  []
  (let [prism-css (fetch-cdn-asset "https://cdnjs.cloudflare.com/ajax/libs/prism/1.30.0/themes/prism-okaidia.min.css"
                                   "prism-okaidia.min.css")]
    (str "
    <style>
      " prism-css " /* CSS do Prism.js embutido */

      /* Seus estilos originais */
      body, html { margin: 0; padding: 0; height: 100%; width: 100%; overflow: hidden; background-color: #111; }
      #presentation-container {
        display: flex;
        justify-content: center;
        align-items: center;
        width: 100%;
        height: 100%;
      }
      #viewport {
        width: 100vw;
        height: 56.25vw; /* 16:9 aspect ratio */
        max-height: 100vh;
        max-width: 177.78vh; /* 16:9, 100vh * 16/9 */
        position: relative;
        overflow: hidden;
        background-color: #000;
        box-shadow: 0 0 20px rgba(0,0,0,0.5);
      }
      .slide {
        width: 100%;
        height: 100%;
        font-size: 4.2vh;
        position: absolute;
        top: 0;
        left: 0;
        opacity: 0;
        visibility: hidden;
        transition: opacity 0.5s ease-in-out, visibility 0.5s;
      }
      .slide.active {
        opacity: 1;
        visibility: visible;
        z-index: 1;
      }
      .content-element {
        box-sizing: border-box;
        padding: 1em; /* Mantém o padding lateral e inferior */
        padding-top: 0; /* CORREÇÃO: Remove o padding superior */
      }
      .text-content h1, .text-content h2, .text-content h3, .text-content h4, .text-content h5, .text-content p, .text-content ul, .text-content ol {
        margin: 0.5em 0; /* Mantém a margem inferior para espaçar parágrafos */
        margin-top: 0; /* CORREÇÃO: Remove a margem superior */
      }
      /* Ensure embedded media scales correctly */
      .content-element img, .content-element video {
        max-width: 100%;
        max-height: 100%;
        height: auto;
        width: auto;
        display: block;
      }
      /* Navigation Menu */
      #nav-menu {
        position: fixed;
        top: 15px;
        left: 15px;
        z-index: 100;
        background-color: rgba(0,0,0,0.7);
        border-radius: 5px;
        padding: 8px;
        display: flex;
        gap: 5px;
        opacity: 1;
        transition: opacity 0.3s ease-in-out;
      }
      #nav-menu.hidden {
        opacity: 0;
        pointer-events: none;
      }
      #nav-menu button, #nav-menu select {
        background-color: #444;
        color: white;
        border: none;
        border-radius: 3px;
        padding: 5px 8px;
        cursor: pointer;
        font-size: 16px;
      }
      #nav-menu button:hover, #nav-menu select:hover {
        background-color: #666;
      }
      
      /* Ajuste opcional para o fundo do código */
      pre[class*=\"language-\"] {
        background: transparent; /* Remove o fundo do Prism para usar o fundo do slide */
        margin: 0;
      }
    </style>
  ")))

(defn- generate-js
  "Generates the embedded JavaScript for navigation."
  [slide-count]
  (let [prism-core-js (fetch-cdn-asset "https://cdnjs.cloudflare.com/ajax/libs/prism/1.30.0/prism.min.js"
                                       "prism-core.min.js")
        prism-clojure-js (fetch-cdn-asset "https://cdnjs.cloudflare.com/ajax/libs/prism/1.30.0/components/prism-clojure.min.js"
                                          "prism-clojure.min.js")]
    (str
     ;; Libs
     "<script>"
     prism-core-js
     "\n"
     prism-clojure-js
     "</script>"

     ;; Slide logic
     "<script>
      let currentSlide = 0;
      const totalSlides = " slide-count ";
      const slides = document.querySelectorAll('.slide');
      const slideSelect = document.getElementById('slide-select');
      const navMenu = document.getElementById('nav-menu');
      let hideMenuTimer;

      function showSlide(index) {
        if (index < 0 || index >= totalSlides) return;
        
        slides[currentSlide].classList.remove('active');
        currentSlide = index;
        slides[currentSlide].classList.add('active');
        
        if (slideSelect) {
          slideSelect.value = currentSlide;
        }
      }

      function nextSlide() { showSlide(currentSlide + 1); }
      function prevSlide() { showSlide(currentSlide - 1); }

      function toggleFullscreen() {
        if (!document.fullscreenElement) {
          document.documentElement.requestFullscreen();
        } else if (document.exitFullscreen) {
          document.exitFullscreen();
        }
      }

      function showMenu() {
        navMenu.classList.remove('hidden');
        clearTimeout(hideMenuTimer);
        hideMenuTimer = setTimeout(() => {
          navMenu.classList.add('hidden');
        }, 5000); // Hide after 5 seconds of inactivity
      }

      // Event Listeners
      document.addEventListener('keydown', (e) => {
        if (e.key === 'ArrowRight') nextSlide();
        if (e.key === 'ArrowLeft') prevSlide();
      });

      document.addEventListener('mousemove', showMenu);
      
      document.getElementById('btn-prev').addEventListener('click', prevSlide);
      document.getElementById('btn-next').addEventListener('click', nextSlide);
      document.getElementById('btn-fullscreen').addEventListener('click', toggleFullscreen);
      
      slideSelect.addEventListener('change', (e) => {
        showSlide(parseInt(e.target.value, 10));
      });

      // Initial setup
      showSlide(0);
      showMenu();
      Prism.highlightAll(); // <-- NOVO: Roda o coloridor
    </script>
  ")))

(defn generate-html
  "Generates the final self-contained HTML file."
  [{:keys [meta slides]} base-dir]
  (let [template-map (get-template-map (:templates meta))
        default-template-id (-> meta :templates first :slide-template)]
    (str "<!DOCTYPE html>"
         "<html lang=\"en\">"
         "<head>"
         "<meta charset=\"UTF-8\">"
         "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
         "<title>" (or (:title meta) "Presentation") "</title>"
         (generate-css)
         "</head>"
         "<body>"
         "<div id=\"nav-menu\">"
         "<button id=\"btn-prev\" title=\"Previous Slide\">&#9664;</button>"
         "<button id=\"btn-next\" title=\"Next Slide\">&#9654;</button>"
         "<select id=\"slide-select\">"
         (generate-slide-options slides)
         "</select>"
         "<button id=\"btn-fullscreen\" title=\"Toggle Fullscreen\">&#x26F6;</button>"
         "</div>"
         "<div id=\"presentation-container\">"
         "<div id=\"viewport\">"
         (str/join "\n"
                   (map-indexed
                    (fn [i slide]
                      (generate-slide-html slide i template-map default-template-id base-dir))
                    slides))
         "</div>"
         "</div>"
         (generate-js (count slides))
         "</body>"
         "</html>")))

;; --- Main Execution ---

(defn -main
  "Main entry point for the script."
  [& args]
  (let [input-file (first args)]
    (if-not input-file
      (println "Usage: ./slide-markdown.clj <input.smd>")
      (try
        (let [file (io/file input-file)]
          (if-not (.exists file)
            (println (str "Error: File not found: " input-file))
            (let [input-path (.getCanonicalPath file)
                  base-dir (.getParent (io/file input-path))
                  output-file (str/replace input-file #"\.smd$" ".html")
                  file-content (slurp input-path)]

              (println (str "Parsing " input-file "..."))
              (let [presentation-data (-> file-content
                                          (parse-smd-file)
                                          (validate-presentation-data))]

                (println "Generating HTML...")
                (let [html-output (generate-html presentation-data base-dir)]
                  (spit output-file html-output)
                  (println (str "Success! Wrote presentation to " output-file)))))))
        (catch clojure.lang.ExceptionInfo e
          (println (str "Error: " (.getMessage e)))
          (println "Data:" (ex-data e)))
        (catch Exception e
          (println (str "An unexpected error occurred: " (.getMessage e)))
          (.printStackTrace e))))))

;; Run -main if script is executed
(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))