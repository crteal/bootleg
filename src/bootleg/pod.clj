(ns bootleg.pod
  (:require [bencode.core :refer [read-bencode write-bencode]]
            [clojure.edn :as edn]
            [bootleg.glob]
            [bootleg.utils]
            [bootleg.markdown]
            [bootleg.mustache]
            [bootleg.html]
            [bootleg.hiccup]
            [bootleg.selmer]
            [bootleg.yaml]
            [bootleg.json]
            [bootleg.edn]
            [bootleg.file]
            [bootleg.enlive]

            [hickory.convert]
            [hickory.hiccup-utils]
            [hickory.render]
            [hickory.select]
            [hickory.utils]
            [hickory.zip]

            [net.cgrand.enlive-html]
            [net.cgrand.jsoup]
            [net.cgrand.tagsoup]
            [net.cgrand.xml]

            [selmer.filter-parser]
            [selmer.filters]
            [selmer.middleware]
            [selmer.node]
            [selmer.parser]
            [selmer.tags]
            [selmer.template-parser]
            [selmer.util]
            [selmer.validator]

            [clojure.repl]

            [clojure.java.io :as io]
            )
  (:import [java.io PushbackInputStream])
  )

(def stdin (PushbackInputStream. System/in))

(defn write [v]
  (write-bencode System/out v)
  (.flush System/out))

;; when these namespaces appear in macros, they will be transformed into
;; the pod prefixed namespace
(def translate-ns?
  #{"bootleg.utils"
    "bootleg.selmer"
    "bootleg.enlive"

    "hickory.convert"
    "hickory.hiccup-utils"
    "hickory.render"
    "hickory.select"
    "hickory.utils"
    "hickory.zip"

    "selmer.filter-parser"
    "selmer.filters"
    "selmer.middleware"
    "selmer.node"
    "selmer.parser"
    "selmer.tags"
    "selmer.template-parser"
    "selmer.util"
    "selmer.validator"

    "net.cgrand.enlive-html"
    "net.cgrand.jsoup"
    "net.cgrand.tagsoup"
    "net.cgrand.xml"
    })


(defn rename-second [form rename]
  (if (rename (second form))
    (concat (list (first form)
                  (rename (second form)))
            (drop 2 form))
    form))

#_ (rename-second '(def foo [] bar) {'foo 'baz})

(defmacro process-source [sym & [{:keys [ns-renames rename]
                                  :or {ns-renames {}
                                       rename {}
                                       }}]]
  (->
   (clojure.repl/source-fn sym)
   (edamame.core/parse-string  {:all true
                                :auto-resolve {:current (namespace sym)}})
   (rename-second rename)
   (->> (clojure.walk/postwalk
         (fn [form]
           (if (symbol? form)
             (let [ns (namespace form)
                   sym-name (name form)]
               (if (ns-renames ns)
                 (symbol (ns-renames ns) sym-name)
                 (if (translate-ns? ns)
                   (symbol (str "pod.retrogradeorbit." ns) sym-name)
                   (symbol ns sym-name))))
             form))))
   prn-str))

#_ (bootleg.utils/pprint (process-macro-source
                          net.cgrand.enlive-html/bodies
                          ;;bootleg.enlive/deftemplate
                          )
                         )

#_ (clojure.repl/source-fn 'net.cgrand.enlive-html/bodies)
#_ (clojure.repl/source-fn 'bootleg.enlive/deftemplate)

(defmacro make-lookup [ns]
  (->>
   (for [[k v] (ns-interns ns)]
     (let [{:keys [ns name macro private]} (meta v)]
       (when-not macro
         (if private
           ;; allow invoking private funcs if needed
           [(list 'quote (symbol (str "pod.retrogradeorbit." (str ns)) (str k)))
            `(fn [& args#] (apply (var ~(symbol v)) args#))
            ]
           ;; public func
           [(list 'quote (symbol (str "pod.retrogradeorbit." (str ns)) (str k)))
            (symbol v)
            ]))))
   (filter identity)
   (into {})))

#_ (meta ((ns-interns 'net.cgrand.enlive-html) 'bodies))
#_ (((make-lookup net.cgrand.enlive-html) 'pod.retrogradeorbit.net.cgrand.enlive-html/bodies) [[:a] :b])
#_ (meta ((ns-interns 'net.cgrand.enlive-html) 'pad-unless))

(defmacro make-namespace-def [namespace & [{:keys [predefs only]
                                            :or {predefs #{}
                                                 only (constantly true)}}]]
  (prn predefs)
  {"name" (str "pod.retrogradeorbit." (str namespace))
   "vars" (->>
           (let [interns (ns-interns namespace)
                 ;;privates (filter #(:private (meta (second %))) interns)
                 ;;publics (filter #(not (:private (meta (second %)))) interns)
                 first-set (filter #(let [name (:name (meta (second %)))]
                                      (prn name (predefs name))
                                      false ;;(#{'bodies} name)
                                      )
                                   interns)
                 only-set (filter #(and (only (:name (meta (second %))))
                                        (not (predefs (:name (meta (second %))))))
                                  interns)
                 ]
             [first-set only-set]
             #_ (for [[k v] (concat first-set only-set)]
               (let [{:keys [ns name macro private]} (meta v)]
                 {"name" (str name)
                  "code" nil #_`(process-macro-source ~(symbol v))}
                 )))
           #_(filter identity)
           #_(into []))})


(defmacro make-inlined-code-set [namespace syms & [{:keys [pre-declares rename]
                                                    :or {pre-declares []
                                                         rename {}}
                                                    :as opts}]]
  (let [interns (ns-interns namespace)]
    (into
     (if-not (empty? pre-declares)
       [{"name" "_pre-declares"
         "code" (->> pre-declares
                     (map #(format "(declare %s)" %))
                     (clojure.string/join " "))}]
       [])
     (for [sym syms]
       {"name" (str (get rename sym sym))
        "code" `(process-source ~(symbol (interns sym)) ~opts)}))))



#_
(macroexpand-1 '(make-inlined-set net.cgrand.enlive-html [bodies annotations]))

#_
(make-inlined-code-set net.cgrand.enlive-html [bodies pad-unless])
#_
(make-inlined-code-set net.cgrand.enlive-html [snippet*])

(defmacro make-inlined-namespace [namespace & body]
  `{"name" (str "pod.retrogradeorbit." ~(str namespace))
    "vars" (vec (apply concat (vector ~@body)))}
  )

#_
(macroexpand-1
 '(make-inlined-namespace
   net.cgrand.enlive-html
   (make-inlined-code-set net.cgrand.enlive-html [bodies pad-unless])
   (make-inlined-code-set net.cgrand.enlive-html [snippet*])))

#_
(make-inlined-namespace
   net.cgrand.enlive-html
   (make-inlined-code-set net.cgrand.enlive-html [bodies pad-unless])
   (make-inlined-code-set net.cgrand.enlive-html [snippet*]))


(defmacro make-inlined-code-set-macros [namespace & [{:keys [exclude]
                                                      :or {exclude #{}}}]]
  (let [interns (ns-interns namespace)]
    (into []
          (filter identity
                  (for [sym (keys interns)]
                    (when (and (:macro (meta (interns sym)))
                               (not (exclude sym)))
                      {"name" (str sym)
                       "code" `(process-source ~(symbol (interns sym)))}))))))

#_ (make-inlined-code-set-macros bootleg.utils)


(defmacro make-inlined-public-fns [namespace & [{:keys [exclude only include-private rename]
                                                 :or {exclude #{}
                                                      only (constantly true)
                                                      include-private false
                                                      rename {}}}]]
  (let [interns (if include-private
                  (ns-interns namespace)
                  (ns-publics namespace))]
    (into []
          (filter identity
                  (for [sym (keys interns)]
                    (when (and (not (exclude sym))
                               (not (:macro (meta (interns sym))))
                               (only sym))
                      {"name" (str (get rename sym sym))})))))
  )

#_ (macroexpand-1 (make-inlined-public-fns bootleg.utils {:exclude #{convert-to}}))
#_ (make-inlined-public-fns bootleg.utils {:exclude #{convert-to}})

(defmacro make-inlined-namespace-basic [namespace & [opts]]
  `(make-inlined-namespace
    ~namespace
    (make-inlined-public-fns ~namespace ~opts)
    (make-inlined-code-set-macros ~namespace ~opts)))


#_ "#'net.cgrand.enlive-html/bodies"

#_ (process-macro-source net.cgrand.enlive-html/bodies)

#_ (#{'foo} 'foo)

#_ (macroexpand-1 '(make-namespace-def net.cgrand.enlive-html {:predefs #{'bodies 'annotations} :only #{}}))

#_ (
    (make-namespace-def net.cgrand.enlive-html {:predefs #{'bodies} :only #{}})
    "vars")
#_ (make-namespace-def hickory.hiccup-utils)
#_ (macroexpand-1 '(make-namespace-def bootleg.utils))
#_ (macroexpand-1 '(make-namespace-def bootleg.glob))
#_ (macroexpand-1 '(make-namespace-def bootleg.selmer))
#_ (process-macro-source bootleg.selmer/with-escaping)
#_ (make-namespace-def bootleg.selmer)
#_ (bootleg.pod/process-macro-source (clojure.core/symbol #'bootleg.selmer/exception))
#_ (bootleg.pod/process-macro-source bootleg.selmer/exception)

(def lookup (merge
             (make-lookup bootleg.glob)
             (make-lookup bootleg.utils)
             (make-lookup bootleg.markdown)
             (make-lookup bootleg.mustache)
             (make-lookup bootleg.html)
             (make-lookup bootleg.hiccup)
             (make-lookup bootleg.selmer)
             (make-lookup bootleg.yaml)
             (make-lookup bootleg.json)
             (make-lookup bootleg.edn)
             (make-lookup bootleg.file)

             ;;enlive has HOFs
             (make-lookup bootleg.enlive)
             (make-lookup net.cgrand.enlive-html)

             (make-lookup hickory.convert)
             (make-lookup hickory.hiccup-utils)
             (make-lookup hickory.render)
             (make-lookup hickory.select)
             (make-lookup hickory.utils)
             (make-lookup hickory.zip)

             (make-lookup net.cgrand.jsoup)
             (make-lookup net.cgrand.tagsoup)
             (make-lookup net.cgrand.xml)

             (make-lookup selmer.filter-parser)
             (make-lookup selmer.filters)
             (make-lookup selmer.middleware)
             (make-lookup selmer.node)
             (make-lookup selmer.parser)
             (make-lookup selmer.tags)
             (make-lookup selmer.template-parser)
             (make-lookup selmer.util)
             (make-lookup selmer.validator)

             ))

(defn main []
  (try
    (loop []
      (let [{:strs [id op var args]} (read-bencode stdin)]

        #_ (with-open [wrtr (io/writer "./pod-in.txt" :append true)]
             (.write wrtr (prn-str (String. op))))

        (case (String. op)
          "describe"
          (do
            (write {"format" "edn"
                    "namespaces"
                    [
                     (make-inlined-namespace-basic bootleg.glob)
                     (make-inlined-namespace-basic bootleg.utils)
                     (make-inlined-namespace-basic bootleg.markdown)
                     (make-inlined-namespace-basic bootleg.mustache)
                     (make-inlined-namespace-basic bootleg.html)
                     (make-inlined-namespace-basic bootleg.hiccup)
                     (make-inlined-namespace-basic bootleg.selmer)
                     (make-inlined-namespace-basic bootleg.yaml)
                     (make-inlined-namespace-basic bootleg.json)
                     (make-inlined-namespace-basic bootleg.edn)
                     (make-inlined-namespace-basic bootleg.file)

                     (make-inlined-namespace
                      net.cgrand.xml
                      (make-inlined-code-set
                       net.cgrand.xml
                       [
                        document?
                        tag?
                        xml-zip]
                       {:ns-renames {"z" "clojure.zip"}
                        :pre-declares []}
                       )
                      )

                     ;; elive has HOFs
                     (make-inlined-namespace
                      net.cgrand.enlive-html
                      (make-inlined-code-set
                       net.cgrand.enlive-html
                       ;; the following are used in net.cgrand.enlive-html
                       ;; and also bootleg.enlive, so they have to come first
                       [pad-unless
                        static-selector?
                        cacheable
                        cacheable?
                        bodies
                        node?
                        append!
                        accept-key
                        children-locs
                        step
                        transform-loc
                        mapknitv
                        union
                        intersection
                        id=
                        tag=
                        attr-values
                        zip-pred
                        pred
                        any
                        as-nodes
                        attr-has
                        has-class
                        compile-keyword
                        compile-step
                        compile-chain
                        selector-chains
                        states
                        predset
                        make-state
                        lockstep-automaton*
                        memoized-lockstep-automaton*
                        lockstep-automaton
                        lockstep-transform
                        transform-fragment-locs
                        flatten-nodes-coll
                        transform-fragment
                        automaton*
                        memoized-automaton*
                        automaton
                        transform-node
                        fragment-selector?
                        node-selector?
                        transform
                        ]
                       {:ns-renames {"z" "clojure.zip"
                                     "xml" "pod.retrogradeorbit.net.cgrand.xml"
                                     }
                        :pre-declares ["pred"
                                       "mapknitv"
                                       "automaton*"
                                       "flatten-nodes-coll"
                                       "automaton"]}
                       )
                      (make-inlined-public-fns
                       net.cgrand.enlive-html
                       {:exclude #{cacheable
                                   cacheable?
                                   node?
                                   append!
                                   accept-key
                                   children-locs
                                   step
                                   transform-loc
                                   mapknitv
                                   union
                                   intersection
                                   id=
                                   tag=
                                   attr-values
                                   zip-pred
                                   pred
                                   any
                                   as-nodes
                                   attr-has
                                   has-class
                                   compile-keyword
                                   compile-step
                                   compile-chain
                                   selector-chains
                                   states
                                   pred-set
                                   make-state
                                   lockstep-automaton*
                                   memoized-lockstep-automaton*
                                   lockstep-automaton
                                   lockstep-transform
                                   transform-fragment-locs
                                   flatten-nodes-coll
                                   transform-fragment
                                   automaton*
                                   memoized-automaton*
                                   automaton
                                   transform-node
                                   fragment-selector?
                                   node-selector?
                                   transform

                                   }})
                      (make-inlined-code-set-macros bootleg.enlive))

                     ;; enlive has HOFs
                     (make-inlined-namespace
                      bootleg.enlive
                      (make-inlined-code-set
                       bootleg.enlive
                       [content append prepend after before substitute]
                       )
                      (make-inlined-code-set
                       net.cgrand.enlive-html
                       [set-attr]
                       )
                      (make-inlined-public-fns
                       bootleg.enlive
                       {:exclude #{content append prepend after before substitute}})
                      (make-inlined-code-set-macros bootleg.enlive))

                     (make-inlined-namespace-basic net.cgrand.jsoup)
                     (make-inlined-namespace-basic net.cgrand.tagsoup)



                     (make-inlined-namespace-basic hickory.convert)
                     (make-inlined-namespace-basic hickory.hiccup-utils)
                     (make-inlined-namespace-basic hickory.render)
                     (make-inlined-namespace-basic hickory.select)
                     (make-inlined-namespace-basic hickory.utils)
                     (make-inlined-namespace-basic hickory.zip)

                     (make-inlined-namespace-basic hickory.convert)
                     (make-inlined-namespace-basic hickory.hiccup-utils)
                     (make-inlined-namespace-basic hickory.render)
                     (make-inlined-namespace-basic hickory.select)
                     (make-inlined-namespace-basic hickory.utils)
                     (make-inlined-namespace-basic hickory.zip)

                     (make-inlined-namespace-basic selmer.filter-parser)
                     (make-inlined-namespace-basic selmer.filters)
                     (make-inlined-namespace-basic selmer.middleware)
                     (make-inlined-namespace-basic selmer.node)
                     (make-inlined-namespace-basic selmer.parser)
                     (make-inlined-namespace-basic selmer.tags)
                     (make-inlined-namespace-basic selmer.template-parser)
                                        ;(make-inlined-namespace-basic selmer.util)
                     (make-inlined-namespace-basic selmer.validator)

                     ]
                    "id" (String. id)})
            (recur))

          "invoke"
          (do
            (try
              (let [var (-> var
                            String.
                            symbol)
                    args (String. args)
                    _ (with-open [wrtr (io/writer "./pod-in.txt" :append true)]
                        (.write wrtr (prn-str 'invoke var args
                                              )))
                    args (edn/read-string args)]

                (if-let [f (lookup var)]
                  (let [value (pr-str (apply f args))
                        reply {"value" value
                               "id" id
                               "status" ["done"]}]
                    (with-open [wrtr (io/writer "./pod-in.txt" :append true)]
                      (.write wrtr (prn-str 'reply reply
                                            )))
                    (write reply))
                  (throw (ex-info (str "Var not found: " var) {})))
                )
              (catch Throwable e
                (binding [*out* *err*]
                  (println e))
                (let [reply {"ex-message" (.getMessage e)
                             "ex-data" (pr-str
                                        (assoc (ex-data e)
                                               :type (class e)))
                             "id" id
                             "status" ["done" "error"]}]
                  (write reply))))
            (recur))
          (do
            (write {"err" (str "unknown op:" (name op))})
            (recur)))))
    (catch java.io.EOFException _ nil)))
