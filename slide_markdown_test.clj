(ns slide-markdown-test
  (:require [clojure.test :refer :all]
            [slide-markdown :as sm])) ; Assumes script is on classpath or loaded

;; --- Mocks e Stubs ---

;; Mock a função de 'parse-smd-file' para não precisar ler arquivos
(deftest parse-smd-file-test
  (let [smd-content
        (str "{:title \"Test\", :templates [{:slide-template \"default\", :elements [{:type \"text\"}]}]}\n"
             "END\n"
             "-*-*- Default Slide\n"
             "Hello\n"
             "\n"
             "-*-*- [other] Other Slide\n"
             "Title\n"
             "\n"
             "Subtitle")
        parsed (#'sm/parse-smd-file smd-content)]

    (is (= "Test" (-> parsed :meta :title)))
    (is (= 1 (count (-> parsed :meta :templates))))
    (is (= 2 (count (:slides parsed))))

    (let [slide1 (first (:slides parsed))
          slide2 (second (:slides parsed))]
      (is (nil? (:template-id slide1)))
      (is (= "Default Slide" (:title slide1)))
      (is (= ["Hello"] (:markdown-blocks slide1)))

      (is (= "other" (:template-id slide2)))
      (is (= "Other Slide" (:title slide2)))
      (is (= ["Title" "Subtitle"] (:markdown-blocks slide2))))))

(deftest parse-header-logic-test
  (is (= {:template-id "t1" :title "My Title"} (#'sm/parse-slide-header "[t1] My Title")))
  (is (= {:template-id "t1" :title nil} (#'sm/parse-slide-header "[t1]")))
  (is (= {:template-id nil :title "My Title"} (#'sm/parse-slide-header "My Title")))
  (is (= {:template-id nil :title nil} (#'sm/parse-slide-header ""))))

(deftest validation-test
  (let [valid-data {:meta {:templates [{:slide-template "t1"
                                        :elements [{:type "text"}]}]}
                    :slides [{:template-id "t1"
                              :title "Slide 1"
                              :markdown-blocks ["Block 1"]}]}

        invalid-templates {:meta {:templates [{:slide-template "t1"}
                                              {:slide-template "t1"}]} ; Duplicate ID
                           :slides []}

        invalid-slide-count {:meta {:templates [{:slide-template "t1"
                                                 :elements [{:type "text"} {:type "text"}]}]}
                             :slides [{:template-id "t1"
                                       :title "Slide 1"
                                       :markdown-blocks ["Only one block"]}]}] ; Mismatch

    (is (= valid-data (#'sm/validate-presentation-data valid-data))) ; Should pass

    (is (thrown? clojure.lang.ExceptionInfo
                 #"Template :slide-template IDs are not unique"
                 (#'sm/validate-presentation-data invalid-templates)))

    (is (thrown? clojure.lang.ExceptionInfo
                 #"expects 2 content block(s), but 1 were provided"
                 (#'sm/validate-presentation-data invalid-slide-count)))))

(deftest background-gradient-test
  (let [horizontal {:orientation "horizontal"
                    :layers [{:color "#FFF" :proportion "30%"}
                             {:color "#000" :proportion "70%"}]}
        vertical {:orientation "vertical"
                  :layers [{:color "red" :proportion "50%"}
                           {:color "blue" :proportion "50%"}]}]

    (is (= "linear-gradient(to bottom, #FFF 0.0% 30.0%, #000 30.0% 100.0%)"
           (#'sm/build-css-gradient horizontal)))

    (is (= "linear-gradient(to right, red 0.0% 50.0%, blue 50.0% 100.0%)"
           (#'sm/build-css-gradient vertical)))))