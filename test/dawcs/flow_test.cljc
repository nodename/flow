(ns dawcs.flow-test
  (:require [clojure.test :refer [deftest testing is]]
            [dawcs.flow :as f]))

(deftest fail?--test
  (testing "with non-exception argument"
    (is (not (f/fail? 42))))

  (testing "with exception argument"
    (is (f/fail? (Exception. "oops")))))

(deftest fail-with--test
  (testing "with empty map"
    (let [e (f/fail-with {})]
      (is (= dawcs.flow.Fail (class e)))
      (is (instance? clojure.lang.IExceptionInfo e) "implements IExceptionInfo")
      (is (instance? RuntimeException e) "extends RuntimeException")
      (is (empty? (.getStackTrace (f/fail-with {}))) "stacktrace is disabled by default")
      (is (nil? (.getMessage e)))
      (is (= {} (ex-data e)))))

  (testing "with non-empty map"
    (is (= "oops" (.getMessage (f/fail-with {:msg "oops"}))))
    (is (= {:id 1} (ex-data (f/fail-with {:data {:id 1}}))))
    (let [err (Exception. "uh-oh")]
      (is (= err (.getCause (f/fail-with {:cause err})))))
    (testing "with enabled stacktrace"
      (is (not (empty? (.getStackTrace (f/fail-with {:trace? true}))))))))

(deftest fail-with!--test
  (is (thrown? dawcs.flow.Fail (f/fail-with! {})))
  (testing "stacktrace is enabled by default"
    (is (not (empty? (.getStackTrace (try (f/fail-with! {}) (catch Throwable e e)))))))
  (testing "with stacktrace disabled"
    (is (empty? (.getStackTrace (try (f/fail-with! {:trace? false}) (catch Throwable e e)))))))

(deftest call--test
  (testing "without exception"
    (is (= 42 (f/call #(+ 1 41)))))

  (testing "with exception"
    (is (f/fail? (f/call #(throw (Exception. "oops")))))))

(deftest call-with--test
  (testing "without exception thrown inside function"
    (is (= 42 (f/call-with identity #(+ 1 41)))))

  (testing "with exception thrown inside function"
    (testing "and non-throwing handler"
      (is (f/fail? (f/call-with identity #(throw (Exception. "oops"))))))

    (testing "and throwing handler"
      (is (thrown? Exception (f/call-with #(throw %) #(throw (Exception. "oops"))))))))

(deftest then--test
  (testing "with non-exception argument"
    (is (= 43 (f/then inc 42))))

  (testing "with exception argument"
    (let [err (Exception. "oops")]
      (is (= err (f/then (constantly "ok") err)))))

  (testing "with exception thrown inside of function "
    (testing "and non-exception argument"
      (let [err (Exception. "oops")]
        (is (thrown? Exception (f/then (fn [_] (throw err)) 21)))))

    (testing "and exception argument"
      (let [err (Exception. "uh-oh")]
        (is (= err (f/then (fn [_] (throw (Exception. "oops"))) err)))))))

(deftest then-call--test
  (testing "with non-exception argument"
    (is (= 43 (f/then-call inc 42))))

  (testing "with exception argument"
    (let [err (Exception. "oops")]
      (is (= err (f/then-call (constantly "ok") err)))))

  (testing "with exception thrown inside of function "
    (testing "and non-exception argument"
      (let [err (Exception. "oops")]
        (is (= err (f/then-call (fn [_] (throw err)) 21)))))

    (testing "and exception argument"
      (let [err (Exception. "uh-oh")]
        (is (= err (f/then-call (fn [_] (throw (Exception. "oops"))) err)))))))

(deftest else--test
  (testing "with non-exception argument"
    (is (= 42 (f/else (constantly "caught") 42))))

  (testing "with exception argument"
    (is (= "caught" (f/else (constantly "caught") (Exception. "oops")))))

  (testing "with exception thrown inside of function"
    (testing "and non-exception argument"
      (let [err (Exception. "oops")]
        (is (= 21 (f/else (fn [_] (throw err)) 21)))))

    (testing "and exception argument"
      (let [err (RuntimeException. "uh-oh")]
        (is (thrown? Exception (f/else (fn [_] (throw (Exception. "oops"))) err)))))))

(deftest else-call--test
  (testing "with non-exception argument"
    (is (= 42 (f/else-call (constantly "caught") 42))))

  (testing "with exception argument"
    (is (= "caught" (f/else-call (constantly "caught") (Exception. "oops")))))

  (testing "with exception thrown inside of function "
    (testing "and non-exception argument"
      (let [err (Exception. "oops")]
        (is (= 21 (f/else-call (fn [_] (throw err)) 21)))))

    (testing "and exception argument"
      (let [err (RuntimeException. "uh-oh")]
        (is (= err (f/else-call (fn [_] (throw err)) (Exception. "oops"))))))))

(deftest thru--test
  (testing "with non-exception argument"
    (let [state (atom nil)
          side-fx #(reset! state (inc %))
          res (f/thru side-fx 42)]
      (is (= 42 res))
      (is (= 43 @state))))

  (testing "with exception argument"
    (let [last-err (atom nil)
          err (Exception. "oops")
          side-fx #(reset! last-err %)
          res (f/thru side-fx err)]
      (is (= res err))
      (is (= @last-err err))))

  (testing "with exception thrown inside of function"
    (is (thrown? Exception (f/thru #(throw %) (Exception. "oops"))))))

(deftest thru-call--test
  (testing "with non-exception argument"
    (let [state (atom nil)
          side-fx #(reset! state (inc %))
          res (f/thru-call side-fx 42)]
      (is (= 42 res))
      (is (= 43 @state))))

  (testing "with exception argument"
    (let [last-err (atom nil)
          err (Exception. "oops")
          side-fx #(reset! last-err %)
          res (f/thru-call side-fx err)]
      (is (= res err))
      (is (= @last-err err))))

  (testing "with exception thrown inside of function"
    (is (= 21 (f/thru-call (fn [_] (throw (Exception. "oops"))) 21)))))

(deftest else-if--test
  (testing "with non-exception argument"
    (is (= 42 (f/else-if NullPointerException (constantly "caught") 42))))

  (testing "with exception argument"
    (testing "and class specification equal to exception class"
      (is (= "caught" (f/else-if NullPointerException
                                 (constantly "caught")
                                 (NullPointerException. "oops")))))

    (testing "and class specification non-equal to exception class"
      (let [err (UnsupportedOperationException. "oops")]
        (is (= err (f/else-if NullPointerException
                              (constantly "caught")
                              err))))))

  (testing "with wrong exeption argument"
    (is (thrown? AssertionError
                 (f/else-if String
                          (constantly "caught")
                          (UnsupportedOperationException. "oops"))))))

(deftest flet--test
  (testing "with no exception"
    (is (= 6 (f/flet [x (+ 1 2), y (+ x 3)] y))))

  (testing "with exception in bindings"
    (is (f/fail? (f/flet [x (+ 1 2), y (/ x 0)] y))))

  (testing "with exception in body"
    (is (f/fail? (f/flet [x (+ 1 2), y 0] (/ x y)))))

  (testing "with custom handler"
    (let [handler #(throw %)]
      (testing "with no exception"
        (is (= 6 (f/flet [:caught handler
                          x (+ 1 2)
                          y (+ x 3)]
                   y))))

      (testing "with exception in bindings"
        (is (thrown? ArithmeticException (f/flet [:caught handler
                                                  x (+ 1 2)
                                                  y (/ x 0)]
                                           y))))

      (testing "with exception in body"
        (is (thrown? ArithmeticException (f/flet [:caught handler
                                                  x (+ 1 2)
                                                  y 0]
                                           (/ x y))))))))

(deftest catch-protocol--test
  (extend-protocol f/Catch
    NullPointerException
    (caught [t] (throw t)))
  (is (f/fail? (f/call 1)))
  (is (thrown? NullPointerException (f/call + 1 nil)))
  (is (thrown? NullPointerException (f/flet [x 1 y nil] (+ x y)))))

(defrecord Left [error])
(defrecord Right [value])

(deftest flow-protocol--test
  (extend-protocol f/Flow
    Left
    (on-success [this _] this)
    (on-failure [this f] (f (ex-info "Either.Left" this)))

    Right
    (on-success [this f] (f (:value this)))
    (on-failure [this _] this))

  (is (= 2 (f/on-success (Right. 1) inc)))
  (is (= (Right. 1) (f/on-failure (Right. 1) (constantly :error))))
  (is (= (Left. "oops") (f/on-success (Left. "oops") inc)))
  (is (= "oops" (f/on-failure (Left. "oops") #(-> % .getData :error)))))

(deftest fail--test
  (testing "with 0 arguments"
    (let [e (f/fail)
          m (Throwable->map e)]
      (is (= clojure.lang.ExceptionInfo (class e)))
      (is (= nil (:cause m)))
      (is (= {} (:data m)))))

  (testing "with 1 argument"
    (testing "with string argument"
      (let [e (f/fail "oops")
            m (Throwable->map e)]
        (is (= clojure.lang.ExceptionInfo (class e)))
        (is (= "oops" (:cause m)))
        (is (= {} (:data m)))))
    (testing "with non-string argument"
      (testing "with map agrument"
        (let [e (f/fail {:value 1})
              m (Throwable->map e)]
          (is (= clojure.lang.ExceptionInfo (class e)))
          (is (nil? (:cause m)))
          (is (= {:value 1} (:data m)))))
      (testing "with non-map agrument"
        (let [e (f/fail 29)
              m (Throwable->map e)]
          (is (= clojure.lang.ExceptionInfo (class e)))
          (is (nil? (:cause m)))
          (is (= {::f/data 29} (:data m)))))))

  (testing "with 2 arguments"
    (testing "with 2nd map argument"
      (let [m (-> (f/fail "oops" {:a 1}) Throwable->map)]
        (is (= "oops" (:cause m)))
        (is (= {:a 1} (:data m)))))
    (testing "with 2nd non-map argument"
      (let [m (-> (f/fail "oops" 1) Throwable->map)]
        (is (= "oops" (:cause m)))
        (is (= {::f/data 1} (:data m)))))))