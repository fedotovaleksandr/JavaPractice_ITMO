package ru.ifmo.ctddev.fedotov.implementor;


import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;
import net.java.quickcheck.collection.Pair;

import javax.swing.*;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Created by aleksandr on 07.12.16.
 */
public class Implementor implements JarImpler {
    private final Map<String, String> args;

    @Override
    public void implementJar(Class<?> aClass, Path path) throws ImplerException {
        this.generateJar(aClass, path);
    }

    private void generateJar(Class<?> aClass, Path path) {
    }

    @Override
    public void implement(Class<?> aClass, Path path) throws ImplerException {
        this.generateSourceCode(aClass, path);
    }

    private Map<String, Pattern> patterns = new HashMap<String, Pattern>() {{
        put("filepath", Pattern.compile("\\."));
    }};

    private void generateSourceCode(Class<?> aClass, Path path) throws ImplerException {
        if (aClass.isPrimitive()) {
            throw new ImplerException("Primitive");
        }
        File file = this.createClassFile(
                aClass.getSimpleName() + "Impl.java",
                aClass.getPackage().getName(),
                path
        );




    }

    private File createClassFile(String fileName, String packageName, Path path) throws ImplerException {
        final Path packagePath = path.resolve(patterns.get("filepath").matcher(packageName).replaceAll("/"));
        File packageFile = packagePath.toFile();
        if (!packageFile.exists() && !packageFile.mkdirs()) {
            throw new ImplerException("Can't create directory");
        }
        Path classPath = packagePath.resolve(fileName);
        return classPath.toFile();
    }

    interface  ClassNodeInterface {
        public Pair<String,Set<String>> build() ;
        public ClassNodeInterface addNode(ClassNodeInterface classNode,Integer priority);

    }
    abstract class AbstractClassNode implements ClassNodeInterface{
        protected ArrayList<Pair<Integer,ClassNodeInterface>> nodes = new ArrayList<>();
        @Override
        public ClassNodeInterface addNode(ClassNodeInterface classNode, Integer priority) {
            nodes.add(priority,new Pair<Integer, ClassNodeInterface>(nodes.size(),classNode));
            return this;
        }
        protected abstract String getStartSource();
        protected abstract String getEndSource();
        protected abstract Set<String> getImports();
        @Override
        public Pair<String, Set<String>> build() {
            StringBuilder sb = new StringBuilder();
            Set<String>  set = new HashSet<>();
            sb.append(this.getStartSource());
            //inner
            ArrayList<Pair<String, Set<String>>> nodesTowrite = new ArrayList<>();
            for (Pair<Integer, ClassNodeInterface> node:this.nodes) {
                Pair<String, Set<String>> nodeResult = node.getSecond().build();
                nodesTowrite.add(node.getFirst(),nodeResult);
            }
            for (Pair<String, Set<String>> node:nodesTowrite) {
                set.addAll(node.getSecond());
                sb.append(node.getFirst());
            }
            //--
            set.addAll(this.getImports());
            sb.append(this.getEndSource());
            return new Pair<>(sb.toString(), set);
        }

    }

    class MainNode extends AbstractClassNode implements ClassNodeInterface {
        @Override
        protected String getStartSource() {
            return null;
        }

        @Override
        protected String getEndSource() {
            return null;
        }

        @Override
        protected Set<String> getImports() {
            return null;
        }
    }

    class ClassNodeImports  implements ClassNodeInterface {
        private final Set<String> imports;

        public ClassNodeImports(Set<String> imports) {
            this.imports = imports;
        }

        @Override
        public Pair<String, Set<String>> build() {
            StringBuilder sb = new StringBuilder();
            Set<String>  set = new HashSet<>();
            this.imports.forEach(importString -> sb
                    .append("import ")
                    .append(importString)
                    .append(";")
                    .append(System.lineSeparator()));
            sb.append(System.lineSeparator());
            return new Pair<String, Set<String>>(sb.toString(), set);
        }

        @Override
        public ClassNodeInterface addNode(ClassNodeInterface classNode, Integer priority) {
            return this;
        }
    }

    class ClassNode extends AbstractClassNode implements ClassNodeInterface {

        @Override
        protected String getStartSource() {
            return null;
        }

        @Override
        protected String getEndSource() {
            return null;
        }

        @Override
        protected Set<String> getImports() {
            return null;
        }
    }



    class OptionResolver {

        ArrayList<Pair<String, String>> args;
        Integer argcLength = 0;

        OptionResolver() {
            this.args = new ArrayList<>();
        }

        public  OptionResolver addArg(String aliasName, String defaultValue) {

            Pair<String, String> nPair = new Pair<>(aliasName, defaultValue);
            args.add(argcLength, nPair);
            argcLength++;
            return this;

        }

        final Map<String, String> resolveArg(String[] arguments) {
            if (arguments.length > argcLength) {
                throw new IllegalArgumentException("Bad Arguments");
            }
            final Map<String, String> map = new HashMap<>();

            for (Integer i = 0; i < arguments.length || i < argcLength; i++) {
                String val;
                if (i < arguments.length) {
                    val = arguments[i];
                } else {
                    val = args.get(i).getSecond();
                }
                map.put(args.get(i).getFirst(), val);
            }
            return map;

        }
    }

    public Implementor() {
        String[] args = new String[]{};
        OptionResolver resolver = (new OptionResolver())
                .addArg("classname", "java.util.Map");
        this.args = resolver.resolveArg(args);
    }

    public Implementor(String[] args) {
        OptionResolver resolver = (new OptionResolver())
                .addArg("classname", "java.util.Map");
        this.args = resolver.resolveArg(args);
    }

    public static void main(String[] args) {

        Implementor imp = new Implementor(args);
        imp.process();
    }

    private void process() {
    }
}
