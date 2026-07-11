(ns clojure-showcase.core
  ;; :gen-class lets this namespace be compiled into a Java class with a
  ;; conventional static main method. That is what `clojure -M:run` calls.
  (:gen-class)
  (:import
   ;; This small demo intentionally uses Java's built-in HTTP server instead
   ;; of a framework, so the only runtime dependency is Clojure itself.
   [com.sun.net.httpserver HttpExchange HttpHandler HttpServer]
   [java.net InetSocketAddress URLDecoder]
   [java.nio.charset StandardCharsets]))

;; Clojure programs commonly model application data as plain maps and vectors.
;; No classes are needed here: each order is just a map with keyword keys.
(def orders
  [{:id 101 :region "North" :customer "Ada" :total 144 :paid true}
   {:id 102 :region "South" :customer "Rich" :total 89 :paid false}
   {:id 103 :region "West" :customer "Grace" :total 231 :paid true}
   {:id 104 :region "East" :customer "Alan" :total 177 :paid true}
   {:id 105 :region "North" :customer "Edsger" :total 63 :paid true}
   {:id 106 :region "West" :customer "Barbara" :total 318 :paid false}
   {:id 107 :region "South" :customer "Leslie" :total 205 :paid true}
   {:id 108 :region "East" :customer "Jean" :total 152 :paid true}])

;; Most Clojure values are immutable. An atom is a managed reference to a value
;; that can change over time. Updating it with `swap!` produces a new map rather
;; than mutating the old one in place.
(def account
  (atom {:balance 100
         :log [{:op "open-account" :amount 100 :balance 100}]}))

;; This map drives the REPL panel. The UI is intentionally data-driven: adding
;; another example means adding another key/value pair here.
(def repl-forms
  {"map" {:label "(map inc [1 2 3])"
          :result "(2 3 4)"}
   "assoc" {:label "(assoc {:name \"Ada\"} :active true)"
            :result "{:name \"Ada\", :active true}"}
   "frequencies" {:label "(frequencies \"clojure\")"
                  :result "{\\c 1, \\l 1, \\o 1, \\j 1, \\u 1, \\r 1, \\e 1}"}
   "reduce" {:label "(reduce + [10 20 30])"
             :result "60"}})

;; CSS is embedded so the project stays as a single Clojure source file plus
;; deps.edn. A larger app would usually serve static files from resources.
(def css
  "body{margin:0;font-family:Inter,ui-sans-serif,system-ui,Segoe UI,sans-serif;background:#f7f6f0;color:#15201d}
*{box-sizing:border-box}a{color:inherit}.shell{width:min(1180px,calc(100% - 32px));margin:0 auto;padding:28px 0 48px}
.hero{min-height:58vh;display:grid;grid-template-columns:minmax(0,1fr) minmax(320px,.78fr);gap:34px;align-items:center;border-bottom:1px solid #d9ddd4}
.kicker{color:#246b4b;font-weight:800;text-transform:uppercase;letter-spacing:.08em;font-size:.8rem}h1{font-size:clamp(3rem,8vw,7rem);line-height:.92;margin:.1em 0}p{line-height:1.65;color:#5e6b66}
.code,.output pre,.repl{background:#14201d;color:#e7f6eb;border-radius:8px;padding:22px;white-space:pre-wrap;word-break:break-word;font:600 .95rem/1.65 Cascadia Code,Consolas,monospace}
.tabs{position:sticky;top:0;background:rgba(247,246,240,.94);backdrop-filter:blur(14px);display:flex;gap:8px;padding:16px 0;overflow-x:auto}.tab,.button,select{min-height:42px;border:1px solid #d9ddd4;border-radius:8px;padding:0 16px;background:white;font:inherit;font-weight:800;text-decoration:none;display:inline-flex;align-items:center}.active,.button{background:#246b4b;color:white;border-color:#246b4b}
.panel{padding:38px 0 48px}.intro{display:grid;grid-template-columns:minmax(180px,.42fr) minmax(0,1fr);gap:24px;margin-bottom:18px}.intro h2,.why h2{font-size:clamp(1.8rem,4vw,3.5rem);line-height:1;margin:0}
.workspace{background:rgba(255,255,255,.72);border:1px solid #d9ddd4;border-radius:8px;padding:18px;box-shadow:0 12px 28px rgba(20,32,29,.08)}.two{display:grid;grid-template-columns:minmax(240px,.55fr) minmax(0,1fr);gap:18px}.tool,.output,.card{background:white;border:1px solid #d9ddd4;border-radius:8px;padding:18px}
label,.metric-label{display:block;color:#5e6b66;font-weight:800;font-size:.82rem;text-transform:uppercase;letter-spacing:.08em;margin-bottom:8px}select{width:100%;font-weight:600}.metrics{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;margin-top:16px}.metric{border-left:4px solid #2b5f92;background:#f3f7f8;padding:14px;border-radius:6px}.metric strong{display:block;font-size:clamp(1.5rem,3vw,2.3rem)}
.pipeline{display:grid;grid-template-columns:repeat(5,minmax(130px,1fr));gap:10px;margin-bottom:18px}.step{background:white;border:1px solid #d9ddd4;border-radius:8px;padding:14px;min-height:104px}.step strong{display:block;color:#2b5f92;margin-bottom:8px}.buttons{display:flex;gap:10px;flex-wrap:wrap}.secondary{background:white;color:#246b4b}.features{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px}.card h3{color:#246b4b}
@media(max-width:880px){.hero,.intro,.two,.features{grid-template-columns:1fr}.hero{min-height:auto;padding:48px 0 26px}.pipeline{grid-template-columns:1fr}}@media(max-width:520px){.shell{width:min(100% - 20px,1180px);padding-top:12px}.workspace,.tool,.output,.card{padding:12px}.metrics{grid-template-columns:1fr}.tab{flex:1 0 92px}}")

;; A tiny HTML escaping helper. Any value rendered as text passes through this
;; function so angle brackets and quotes are displayed instead of interpreted.
(defn esc [value]
  (-> (str value)
      (clojure.string/replace "&" "&amp;")
      (clojure.string/replace "<" "&lt;")
      (clojure.string/replace ">" "&gt;")
      (clojure.string/replace "\"" "&quot;")))

;; Convert a map like {:class "tab" :href "/"} into HTML attributes.
(defn attrs [m]
  (apply str
         (for [[k v] m
               :when v]
           (str " " (name k) "=\"" (esc v) "\""))))

(declare html)

;; A minimal Hiccup-style renderer:
;;   [:p {:class "note"} "Hello"] becomes <p class="note">Hello</p>
;; This keeps the UI declarative and data-shaped, which is idiomatic Clojure.
(defn html [node]
  (cond
    (nil? node) ""
    (string? node) (esc node)
    (number? node) (str node)
    (keyword? node) (name node)
    (seq? node) (apply str (map html node))
    (vector? node) (let [[tag maybe-attrs & children] node
                         [attr-map children] (if (map? maybe-attrs)
                                               [maybe-attrs children]
                                               [{} (cons maybe-attrs children)])]
                     (str "<" (name tag) (attrs attr-map) ">"
                          (apply str (map html children))
                          "</" (name tag) ">"))
    :else (esc node)))

;; HttpExchange exposes the raw query string. This turns `?panel=data` into
;; {:panel "data"}, using keywords because they are the normal Clojure choice
;; for stable map keys.
(defn query-params [^HttpExchange exchange]
  (let [query (some-> exchange .getRequestURI .getRawQuery)]
    (if (clojure.string/blank? query)
      {}
      (into {}
            (for [pair (clojure.string/split query #"&")
                  :let [[k v] (clojure.string/split pair #"=" 2)]]
              [(keyword (URLDecoder/decode k StandardCharsets/UTF_8))
               (URLDecoder/decode (or v "") StandardCharsets/UTF_8)])))))

;; `cond->>` threads a value through transformations only when conditions are
;; true. Here every call filters paid orders, and the region filter is optional.
(defn paid-orders [region]
  (cond->> orders
    true (filter :paid)
    (not= region "all") (filter #(= region (:region %)))))

(defn money [n]
  (str "$" n))

;; UI helpers return data, not strings. The `html` function later renders these
;; vectors into markup.
(defn tab-link [active label panel]
  [:a {:class (str "tab" (when (= active panel) " active"))
       :href (str "/?panel=" panel)}
   label])

(defn hero []
  [:section.hero
   [:div
    [:p.kicker "Functional programming on the JVM"]
    [:h1 "Clojure Playground"]
    [:p "Explore immutable data, threaded transformations, REPL-driven design, controlled state, and data-shaped thinking through Clojure running on the server."]]
   [:pre.code "(->> orders\n     (filter :paid)\n     (group-by :region)\n     (map summarize)\n     (sort-by :revenue >))"]])

(defn data-panel [params]
  ;; `let` gives names to intermediate values. These values are immutable and
  ;; local to this request.
  (let [region (get params :region "all")
        selected (paid-orders region)
        revenue (reduce + (map :total selected))]
    [:section.panel
     [:div.intro
      [:h2 "Data First"]
      [:p "Clojure favors simple maps, vectors, sets, and sequences that are easy to transform and inspect."]]
     [:div.workspace.two
      [:form.tool {:method "get" :action "/"}
       [:input {:type "hidden" :name "panel" :value "data"}]
       [:label {:for "region"} "Filter orders by region"]
       [:select {:id "region" :name "region"}
        (for [option ["all" "North" "South" "West" "East"]]
          [:option {:value option :selected (= option region)}
           (if (= option "all") "All regions" option)])]
       [:div {:style "margin-top:12px"} [:button.button {:type "submit"} "Apply filter"]]
       [:div.metrics
        [:div.metric [:span.metric-label "Orders"] [:strong (count selected)]]
        [:div.metric [:span.metric-label "Revenue"] [:strong (money revenue)]]]]
      [:div.output
       [:h3 "Returned data"]
       [:pre (pr-str (vec selected))]]]]))

(defn threads-panel []
  ;; The thread-last macro (`->>`) places each previous result as the final
  ;; argument to the next form, which reads naturally for sequence pipelines.
  (let [summaries (->> (paid-orders "all")
                       (group-by :region)
                       (map (fn [[region rows]]
                              {:region region
                               :orders (count rows)
                               :revenue (reduce + (map :total rows))}))
                       (sort-by :revenue >))]
    [:section.panel
     [:div.intro
      [:h2 "Threaded Pipelines"]
      [:p "The thread-last macro makes a chain of sequence operations read like a data pipeline."]]
     [:div.workspace
      [:div.pipeline
       (for [[name text] [["orders" "A plain vector of maps"]
                          ["filter :paid" "Keep completed work"]
                          ["group-by :region" "Shape data around a question"]
                          ["map summarize" "Compute totals per group"]
                          ["sort-by :revenue" "Return useful results"]]]
         [:div.step [:strong name] [:span text]])]
      [:div.output [:h3 "Pipeline result"] [:pre (pr-str (vec summaries))]]]]))

(defn state-panel []
  ;; Dereferencing an atom with `@account` reads its current value.
  (let [{:keys [balance log]} @account]
    [:section.panel
     [:div.intro
      [:h2 "Controlled State"]
      [:p "Atoms hold changeable references while values remain immutable. Submit transactions and watch derived state update."]]
     [:div.workspace.two
      [:div.tool
       [:div.buttons
        [:form {:method "post" :action "/transact"}
         [:input {:type "hidden" :name "op" :value "deposit"}]
         [:button.button {:type "submit"} "Deposit $25"]]
        [:form {:method "post" :action "/transact"}
         [:input {:type "hidden" :name "op" :value "withdraw"}]
         [:button.button.secondary {:type "submit"} "Withdraw $10"]]]
       [:div.metric {:style "margin-top:18px"}
        [:span.metric-label "Atom balance"]
        [:strong (money balance)]]]
      [:div.output
       [:h3 "Transaction log"]
       [:pre "(swap! account update-balance transaction)\n\n" (pr-str log)]]]]))

(defn repl-panel [params]
  (let [k (get params :form "map")
        {:keys [label result]} (get repl-forms k (get repl-forms "map"))]
    [:section.panel
     [:div.intro
      [:h2 "REPL Thinking"]
      [:p "Experiment in tiny steps. Pick a form to see how data and functions compose."]]
     [:div.workspace.two
      [:form.tool {:method "get" :action "/"}
       [:input {:type "hidden" :name "panel" :value "repl"}]
       [:label {:for "form"} "Evaluate a form"]
       [:select {:id "form" :name "form"}
        (for [[value form] repl-forms]
          [:option {:value value :selected (= value k)} (:label form)])]
       [:div {:style "margin-top:12px"} [:button.button {:type "submit"} "Evaluate"]]]
      [:pre.repl "user=> " label "\n" result]]]))

(defn why []
  ;; `for` is a lazy sequence comprehension. The renderer accepts sequences, so
  ;; we can generate repeated cards directly from data.
  [:section.why
   [:h2 "What This Shows"]
   [:div.features
    (for [[title body] [["Expressive syntax" "Small forms compose into clear transformations without custom classes for every shape."]
                        ["Immutable defaults" "Programs are easier to reason about because most operations return new values."]
                        ["JVM reach" "Clojure can use Java libraries while keeping a compact, interactive language model."]
                        ["REPL workflow" "You can grow systems by evaluating real code against a live running program."]]]
      [:article.card [:h3 title] [:p body]])]])

(defn page [params]
  ;; `case` selects which panel to render. Each panel is just a function that
  ;; returns Hiccup-style data.
  (let [panel (get params :panel "data")]
    (str "<!doctype html>"
         (html
          [:html {:lang "en"}
           [:head
            [:meta {:charset "utf-8"}]
            [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
            [:title "Clojure Playground"]
            [:style css]]
           [:body
            [:main.shell
             (hero)
             [:nav.tabs
              (tab-link panel "Data" "data")
              (tab-link panel "Threads" "threads")
              (tab-link panel "State" "state")
              (tab-link panel "REPL" "repl")]
             (case panel
               "threads" (threads-panel)
               "state" (state-panel)
               "repl" (repl-panel params)
               (data-panel params))
             (why)]]]))))

(defn read-body [^HttpExchange exchange]
  (slurp (.getRequestBody exchange)))

;; HTML forms submit bodies in `key=value&key=value` format. This mirrors
;; `query-params`, but parses the POST request body instead of the URL.
(defn form-params [body]
  (if (clojure.string/blank? body)
    {}
    (into {}
          (for [pair (clojure.string/split body #"&")
                :let [[k v] (clojure.string/split pair #"=" 2)]]
            [(keyword (URLDecoder/decode k StandardCharsets/UTF_8))
             (URLDecoder/decode (or v "") StandardCharsets/UTF_8)]))))

;; POST-redirect-GET avoids repeated transactions if the user refreshes after a
;; deposit or withdrawal.
(defn redirect! [^HttpExchange exchange location]
  (.getResponseHeaders exchange)
  (.add (.getResponseHeaders exchange) "Location" location)
  (.sendResponseHeaders exchange 303 -1)
  (.close exchange))

;; HttpServer writes bytes to an output stream. Everything above this point is
;; normal Clojure data; this function is where it becomes an HTTP response.
(defn response! [^HttpExchange exchange status body]
  (let [bytes (.getBytes body StandardCharsets/UTF_8)]
    (.add (.getResponseHeaders exchange) "Content-Type" "text/html; charset=utf-8")
    (.sendResponseHeaders exchange status (alength bytes))
    (with-open [out (.getResponseBody exchange)]
      (.write out bytes))))

(defn transact! [op]
  ;; `swap!` retries safely if another request changes the atom at the same
  ;; time. The function receives the old account value and returns the new one.
  (let [amount (if (= op "deposit") 25 10)]
    (swap! account
           (fn [{:keys [balance log]}]
             (let [next-balance ((if (= op "deposit") + -) balance amount)]
               {:balance next-balance
                :log (conj log {:op op :amount amount :balance next-balance})})))))

;; `reify` creates an object that implements Java's HttpHandler interface. This
;; is normal Clojure/Java interop: no adapter library is required.
(defn handler []
  (reify HttpHandler
    (handle [_ exchange]
      ;; The server is deliberately tiny, so routing is just a `cond` over the
      ;; request method and path.
      (try
        (let [method (.getRequestMethod exchange)
              path (.getPath (.getRequestURI exchange))]
          (cond
            (and (= method "GET") (= path "/"))
            (response! exchange 200 (page (query-params exchange)))

            (and (= method "POST") (= path "/transact"))
            (let [params (form-params (read-body exchange))]
              (transact! (:op params))
              (redirect! exchange "/?panel=state"))

            :else
            (response! exchange 404 "<h1>Not found</h1>")))
        (catch Exception ex
          (response! exchange 500 (str "<h1>Server error</h1><pre>" (esc (.getMessage ex)) "</pre>")))))))

(defn -main [& _]
  ;; `-main` is the process entry point. It creates the server, attaches the
  ;; handler to all paths, and starts listening on localhost.
  (let [server (HttpServer/create (InetSocketAddress. "127.0.0.1" 5179) 0)]
    (.createContext server "/" (handler))
    (.setExecutor server nil)
    (.start server)
    (println "Clojure Playground running at http://127.0.0.1:5179/")
    (println "Press Ctrl+C to stop.")))
