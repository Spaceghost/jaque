(ns jaque.jets.jet
  (:require [clojure.string :refer [split]]
            [jaque.noun.read :refer [lark->axis mean]]))

; Thanks to https://gist.github.com/odyssomay/1035590 and
; http://grokbase.com/t/gg/clojure/11awnj1azb/extending-ifn
(defmacro defnrecord [ifn & defrecord-body]
  (let [max-arity   20
        args        (repeatedly max-arity gensym)
        make-invoke (fn [n]
                      (let [args (take n args)]
                        `(invoke [_ ~@args] (~ifn ~@args))))]
    `(defrecord
       ~@defrecord-body
       clojure.lang.IFn
       ~@(map make-invoke (range (inc max-arity)))
       (invoke [_ ~@args more#]
         (apply ~ifn (concat (list ~@args) more#)))
       (applyTo [_ args#]
         (apply ~ifn args#)))))

(defprotocol Jet
  (label [j])
  (axis [j])
  (apply-core [j core]))

(defnrecord f JetRec [label arm-axis mean-seq f]
  Jet
    (apply-core [this core] (apply f (apply (partial mean core) mean-seq)))
    (axis [this] arm-axis)
    (label [this] label))

(defmacro defjet [sym label arm men arg & body]
  `(def ~sym (->JetRec [~@(map name label)]
                       ~(lark->axis (name arm))
                       [~@(map lark->axis (map name men))]
                       (fn ~sym ~arg ~@body))))
