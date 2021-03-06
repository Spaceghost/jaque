(defproject jaque "0.1.0-SNAPSHOT"
  :description "Clojure implementation of nock" :jvm-opts     ["-server"
; think your stack overflows are spurious? they're not, but...
;                 "-Xss8m"
                 "-XX:+UnlockExperimentalVMOptions" 
                 "-XX:+EnableJVMCI"
;                 "-XX:+TraceDeoptimization"
                 "-d64" 
                 "-Djvmci.class.path.append=/home/pdriver/graal/graal-core/mxbuild/dists/graal.jar"
                 "-Xbootclasspath/a:/home/pdriver/graal/truffle/mxbuild/dists/truffle-api.jar"
;                "-Dgraal.TraceTruffleAssumptions=true" 
;                "-Dgraal.TraceTruffleCompilation=true" 
;                "-Dgraal.PrintCompilation=true" 

                "-Dgraal.Dump"
;                "-Dcom.sun.management.jmxremote"
;                "-Dcom.sun.management.jmxremote.ssl=false"
;                "-Dcom.sun.management.jmxremote.authenticate=false"
;                "-Dcom.sun.management.jmxremote.port=43210"
                 "-Djava.library.path=ed25519"
;                 "-Djava.awt.headless=true"
                ]
  :main         jaque.main
  :aot          [jaque.main]

  ;; leinigen's invocation of javac causes some problems for truffle's
  ;; compiler detection. To mitigate this, please run an annotation processor
  ;; separately (say, with eclipse) and point it at the "gen" folder. we
  ;; simply don't depend on the dsl processor as a temporary workaround.
  :source-paths      ["src/clojure"] 
  :java-source-paths ["gen" "src/java"]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [com.google.guava/guava "23.0"]
                 [com.oracle.truffle/truffle-api "0.22"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.logging "0.4.0"]
                 [com.github.jnr/jnr-ffi "2.1.6"]
                 [com.googlecode.lanterna/lanterna "3.0.0"]
                 [org.clojure/core.async "0.3.443"]
                 [org.prevayler/prevayler-factory "2.6"]
                 [org.prevayler.extras/prevayler-xstream "2.6"]
                 [com.atlassian.commonmark/commonmark "0.9.0"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail
                                                    javax.jms/jms
                                                    com.sun.jdmk/jmxtools
                                                    com.sun.jmx/jmxri]]
;                 [com.oracle.truffle/truffle-dsl-processor "0.22"]
;                 [com.oracle.truffle/truffle-tck "0.20"]
;                 [primitive-math "0.1.3"]
;                 [criterium "0.4.4"]
;                 [clojure-lanterna "0.9.7"]
                 [ring/ring-core "1.5.0"]
                 [ring/ring-jetty-adapter "1.5.0"]
                 [hawk "0.2.11"]
                 [clj-http "3.6.1"]
                 [http-kit "2.2.0"]
                 [slingshot "0.12.2"]])
