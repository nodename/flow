(ns fmnoise.flow
  #?(:clj (:import [fmnoise.flow Fail])))

(defprotocol Flow
  (?ok [this f] "if value is not an error, apply f to it, otherwise return value")
  (?err [this f] "if value is an error, apply f to it, otherwise return value")
  (?throw [this] "if value is an error, throw it, otherwise return value"))

#?(:clj
   (extend-protocol Flow
     java.lang.Object
     (?ok [this f] (f this))
     (?err [this f] this)
     (?throw [this] this)

     nil
     (?ok [this f] (f this))
     (?err [this f] this)
     (?throw [this] this)

     java.lang.Throwable
     (?ok [this f] this)
     (?err [this f] (f this))
     (?throw [this] (throw this)))

   :cljs
   (extend-protocol Flow
                    object
                    (?ok [this f] (f this))
                    (?err [this f] this)
                    (?throw [this] this)

                    nil
                    (?ok [this f] (f this))
                    (?err [this f] this)
                    (?throw [this] this)

                    js/Error
                    (?ok [this f] this)
                    (?err [this f] (f this))
                    (?throw [this] (throw this))))

(defprotocol Catch
  (caught [t] "defines how to process caught exception"))

#?(:clj
   (extend-protocol Catch
     java.lang.Throwable
     (caught [t] t))

   :cljs
   (extend-protocol Catch
                    js/Error
                    (caught [t] t)))

#?(:clj
   (defn fail-with
     "Constructs `Fail` with given options. Stacktrace is disabled by default"
     {:added "2.0"}
     [{:keys [msg data cause suppress? trace?] :or {data {} suppress? false trace? false} :as options}]
     {:pre [(or (nil? options) (map? options))]}
     (Fail. msg data cause suppress? trace?)))

#?(:clj
   (defn fail-with!
     "Constructs `Fail` with given options and throws it. Stacktrace is enabled by default."
     {:added "2.0"}
     [{:keys [trace?] :or {trace? true} :as options}]
     (throw (fail-with (assoc options :trace? trace?)))))

(defn fail?
  "Checks if given value is considered as failure"
  [t]
  #?(:clj
     (or (instance? java.lang.Throwable t)
         (instance? Fail (?err t (constantly (fail-with {})))))
     :cljs
     (or (instance? js/Error t)
         (instance? js/Error (?err t (constantly (js/Error.)))))))

(defn chain
  "Passes given value through chain of functions. If value is an error or any function in chain returns error, it's returned and rest of chain is skipped"
  {:added "4.0"}
  [v f & fs]
  (loop [res (?ok v f)
         chain fs]
    (if (seq chain)
      (recur (?ok res (first chain))
             (rest chain))
      res)))

(defn call
  "Calls given function with supplied args in `try/catch` block, then calls `Catch.caught` on caught exception. If no exception has caught during function call returns its result"
  [f & args]
  (try
    (apply f args)
    (catch #?(:clj java.lang.Throwable :cljs :default) t
      (caught t))))

(defn call-with
  "Calls given function with supplied args in `try/catch` block, then calls catch-handler on caught exception. If no exception has caught during function call returns its result"
  {:added "2.0"}
  [catch-handler f & args]
  (try
    (apply f args)
    (catch #?(:clj java.lang.Throwable :cljs :default) t
      (catch-handler t))))

;; functor

(defn then
  "If value is not an error, applies f to it, otherwise returns value"
  ([f] (partial then f))
  ([f value] (?ok value f)))

(defn then-call
  "If value is not an error, applies f to it wrapped to `call`, otherwise returns value"
  {:added "2.0"}
  ([f] (partial then-call f))
  ([f value] (?ok value (partial call f))))

(defn else
  "If value is an error, applies f to it, otherwise returns value"
  ([f] (partial else f))
  ([f value] (?err value f)))

(defn else-call
  "If value is an error, applies f to it wrapped to `call`, otherwise returns value"
  {:added "2.0"}
  ([f] (partial else-call f))
  ([f value] (?err value (partial call f))))

(defn thru
  "Applies f to value (for side effects). Returns value. Works similar to `doto`, but accepts function as first arg"
  ([f] (partial thru f))
  ([f value] (f value) value))

(defn thru-call
  "Applies f to value wrapped to `call` (for side effects). Returns value. Works similar to `doto`, but accepts function as first arg. Please not that exception thrown inside of function will be silently ignored by default"
  ([f] (partial thru-call f))
  ([f value] (call f value) value))

#?(:clj
   (defn else-if
     "If value is an error of err-class (or any of its parents), applies f to it, otherwise returns value"
     ([err-class f] (partial else-if err-class f))
     ([err-class f value] (if (isa? (class value) err-class) (?err value f) value))))

(defn handle
  "Accepts map with :ok, :err keys and a value. If value is an error, runs :err on it, else runs :ok. Both keys default to `identity`"
  ([opts] (partial handle opts))
  ([{:keys [ok err] :or {ok identity err identity}} value]
   (if (fail? value) (err value) (ok value))))

(defn ex-info!
  "Functional wrapper for creating and throwing ex-info"
  {:added "4.0"}
  [& args]
  (throw (apply ex-info args)))
