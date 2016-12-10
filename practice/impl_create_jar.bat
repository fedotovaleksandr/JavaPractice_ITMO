javac -d out/ -cp lib/quickcheck-0.6.jar src/ru/ifmo/ctddev/fedotov/implementor/Implementor.java tests/info/kgeorgiy/java/advanced/implementor/Impler.java tests/info/kgeorgiy/java/advanced/implementor/JarImpler.java tests/info/kgeorgiy/java/advanced/implementor/ImplerException.java
jar cfm lib/Implementor.jar src/META-INF/MANIFEST.MF out/info/kgeorgiy/java/advanced/implementor/*.class out/ru/ifmo/ctddev/fedotov/implementor/*.class

