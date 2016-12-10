rmdir /S /Q out\doc
mkdir out\doc
javadoc -cp lib/quickcheck-0.6.jar -private -author src/ru/ifmo/ctddev/fedotov/implementor/Implementor.java tests/info/kgeorgiy/java/advanced/implementor/Impler.java tests/info/kgeorgiy/java/advanced/implementor/JarImpler.java tests/info/kgeorgiy/java/advanced/implementor/ImplerException.java -d out/doc
