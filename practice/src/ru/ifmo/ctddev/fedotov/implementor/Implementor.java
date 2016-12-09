package ru.ifmo.ctddev.fedotov.implementor;


import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;
import net.java.quickcheck.collection.Pair;
import net.java.quickcheck.collection.Triple;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by aleksandr on 07.12.16.
 */
public class Implementor implements JarImpler {

    private final Map<String, String> args;

    private static final String CLASSPATH = System.getProperty("java.class.path");

    @Override
    public void implementJar(Class<?> aClass, Path path) throws ImplerException {
        implement(aClass, path.getParent() == null ? Paths.get(".") : path.getParent());
        createJar(aClass, path);
    }

    private void createJar(Class<?> aClass, Path path) throws ImplerException {
        final String sourceLocation = aClass.getCanonicalName().replace('.', '/') + "Impl.java";
        final String classLocation = aClass.getCanonicalName().replace('.', '/') + "Impl.class";
        final Path parent = path.getParent() == null ? Paths.get(".") : path.getParent();
        final Path classPath = parent.resolve(classLocation);
        compile(parent, sourceLocation);

        try (FileInputStream in = new FileInputStream(classPath.toFile());
             JarOutputStream out = new JarOutputStream(
                     new BufferedOutputStream(
                             new FileOutputStream(path.toFile())))) {
            final JarEntry zipEntry = new JarEntry(classLocation);
            out.putNextEntry(zipEntry);
            int nread;
            final byte[] buffer = new byte[1024];
            while ((nread = in.read(buffer)) != -1) {
                out.write(buffer, 0, nread);
            }
            out.closeEntry();
        } catch (IOException e) {
            throw new ImplerException("Error during jar generation!", e);
        }
    }

    private void compile(final Path root, final String file) throws ImplerException {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new ImplerException("Could not find java compiler!");
        }
        final List<String> args = new ArrayList<>(7);
        args.add(root.resolve(file).toString());
        args.add("-sourcepath");
        args.add(root.toString());
        args.add("-cp");
        args.add(root + File.pathSeparator + CLASSPATH);
        args.add("-d");
        args.add(root.toString());
        final int exitCode = compiler.run(null, System.out, System.err, args.toArray(new String[args.size()]));
        if (exitCode != 0) {
            throw new ImplerException(String.format("Can't compile file %1s!", root.resolve(file)));
        }
    }

    @Override
    public void implement(Class<?> aClass, Path path) throws ImplerException {
        this.generateSourceCode(aClass, path);
    }

    private Map<String, Pattern> patterns = new HashMap<String, Pattern>() {{
        put("filepath", Pattern.compile("\\."));
        put("remove_modifiers", Pattern.compile("abstract|transient"));
    }};

    private void generateSourceCode(Class<?> aClass, Path path) throws ImplerException {
        if (aClass.isPrimitive() || !aClass.isInterface()) {
            throw new ImplerException("Primitive");
        }
        File file = this.createClassFile(
                aClass.getSimpleName() + "Impl.java",
                aClass.getPackage().getName(),
                path
        );
        Pair<String, Set<String>> fileSource = new MainNode(aClass)
                .setImports(new ClassNodeImports())
                .addNode(
                        new ClassNode(aClass)
                                .addNode(new ClassNodeConstructors(aClass))
                                .addNode(new ClassNodeFunctions(aClass))
                ).build();

        try (BufferedWriter out = new BufferedWriter(
                new OutputStreamWriter(
                        new FileOutputStream(file), StandardCharsets.UTF_8))) {
            out.write(fileSource.getFirst());
        } catch (IOException e) {
            throw new ImplerException("Error occurred during class generation!", e);
        }
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

    interface ClassNodeInterface {
        public Pair<String, Set<String>> build() throws ImplerException;

        public ClassNodeInterface addNode(ClassNodeInterface classNode);

    }

    abstract class AbstractClassNode implements ClassNodeInterface {
        //
        protected ArrayList<ClassNodeInterface> nodes = new ArrayList<>();

        @Override
        public ClassNodeInterface addNode(ClassNodeInterface classNode) {
            nodes.add(classNode);
            return this;
        }

        protected abstract String getStartSource();

        protected abstract String getEndSource();

        @Override
        public Pair<String, Set<String>> build() throws ImplerException {
            StringBuilder sb = new StringBuilder();
            Set<String> set = new HashSet<>();
            sb.append(this.getStartSource());
            //inner
            for (ClassNodeInterface node : this.nodes) {
                Pair<String, Set<String>> nodeResult = node.build();
                set.addAll(nodeResult.getSecond());
                sb.append(nodeResult.getFirst());
            }
            //--
            sb.append(this.getEndSource());
            return new Pair<>(sb.toString(), set);
        }

    }

    class MainNode implements ClassNodeInterface {

        private final Class<?> aClass;
        //
        protected ArrayList<ClassNodeInterface> nodes = new ArrayList<>();

        public MainNode(Class<?> aClass) {

            this.aClass = aClass;
        }

        @Override
        public ClassNodeInterface addNode(ClassNodeInterface classNode) {
            nodes.add(classNode);
            return this;
        }

        @Override
        public Pair<String, Set<String>> build() throws ImplerException {
            StringBuilder sb = new StringBuilder();
            Set<String> set = new HashSet<>();

            sb.append("package ")
                    .append(aClass.getPackage().getName())
                    .append(";")
                    .append(System.lineSeparator());

            //inner
            StringBuilder sbForAnother = new StringBuilder();
            for (ClassNodeInterface node : this.nodes) {
                Pair<String, Set<String>> nodeResult = node.build();
                set.addAll(nodeResult.getSecond());
                sbForAnother.append(nodeResult.getFirst());
            }
            //--

            Pair<String, Set<String>> importResult = this.importNode.get().setImports(set).build();

            sb.append(importResult.getFirst());
            sb.append(sbForAnother.toString());

            return new Pair<>(sb.toString(), set);
        }

        Optional<ClassNodeImports> importNode;

        public MainNode setImports(ClassNodeImports cni) {
            importNode = Optional.of(cni);
            return this;
        }
    }

    class ClassNodeImports implements ClassNodeInterface {
        private Set<String> imports;

        public ClassNodeImports() {
        }

        public ClassNodeImports setImports(Set<String> imports) {
            this.imports = imports;
            return this;
        }

        @Override
        public Pair<String, Set<String>> build() {
            StringBuilder sb = new StringBuilder();
            Set<String> set = new HashSet<>();
            this.imports.forEach(importString -> sb
                    .append("import ")
                    .append(importString)
                    .append(";")
                    .append(System.lineSeparator()));
            sb.append(System.lineSeparator());

            return new Pair<String, Set<String>>(sb.toString(), set);
        }

        @Override
        public ClassNodeInterface addNode(ClassNodeInterface classNode) {
            return this;
        }
    }

    class ClassNode extends AbstractClassNode {

        private final Class<?> aClass;

        public ClassNode(Class<?> aClass) {
            this.aClass = aClass;
        }

        @Override
        protected String getStartSource() {
            return String.format("public class %1s %2s %3s {",
                    aClass.getSimpleName() + "Impl",
                    aClass.isInterface() ? "implements" : "extends",
                    aClass.getSimpleName()) +
                    System.lineSeparator();
        }

        @Override
        protected String getEndSource() {
            return "}";
        }

    }

    private Supplier<String> variableGenerator = new Supplier<String>() {
        Integer counter = 0;

        @Override
        public String get() {
            return "variable" + counter;
        }
    };

    private Map<Class<?>, String> valuesMapper = new HashMap<Class<?>, String>() {{
        put(Void.class, "");
        put(void.class, "");
        put(char.class, " (char) 0");
        put(long.class, " 0L");
        put(float.class, " 0.0f");

    }};

    class ClassNodeConstructors implements ClassNodeInterface {

        private final Class<?> aClass;

        public ClassNodeConstructors(Class<?> aClass) {
            this.aClass = aClass;
        }


        @Override
        public Pair<String, Set<String>> build() throws ImplerException {
            StringBuilder sb = new StringBuilder();
            Set<String> set = new HashSet<>();
            final Collection<Constructor<?>> constructors = checkConstructors(this.aClass);
            for (Constructor<?> constructor : constructors) {
                final Triple<String, Set<String>, List<String>> parametersWithImports = generateParameters(constructor);
                final Pair<String, Set<String>> exceptionsWithImports = getThrownExceptions(constructor);
                sb.append(String.format("    %1s %2s(%3s) %4s {",
                        patterns.get("remove_modifiers")
                                .matcher(
                                        Modifier.toString(constructor.getModifiers())
                                )
                                .replaceAll(Matcher.quoteReplacement("")),
                        this.aClass.getSimpleName() + "Impl",
                        parametersWithImports.getFirst(),
                        exceptionsWithImports.getFirst())
                ).append(System.lineSeparator());
                sb.append("        ").append(getSuperCall(parametersWithImports.getThird())).append(System.lineSeparator());
                sb.append("    }").append(System.lineSeparator());
                set.addAll(parametersWithImports.getSecond());
                set.addAll(exceptionsWithImports.getSecond());
            }
            return new Pair<>(sb.toString(), set);
        }


        private Pair<String, Set<String>> getThrownExceptions(Constructor<?> constructor) {
            final StringBuilder sb = new StringBuilder();
            final Set<String> imports = new HashSet<>(constructor.getExceptionTypes().length);
            for (Class<?> exception : constructor.getExceptionTypes()) {
                sb.append(exception.getSimpleName()).append(',');
                final Optional<String> importStringOptional = getImportForReferenceType(exception);
                if (importStringOptional.isPresent()) {
                    imports.add(importStringOptional.get());
                }
            }
            return constructor.getExceptionTypes().length == 0
                    ? new Pair<>("", Collections.emptySet())
                    : new Pair<>("throws " + sb.substring(0, sb.length() - 1), imports);

        }

        private Collection<Constructor<?>> checkConstructors(final Class<?> token) throws ImplerException {
            final Collection<Constructor<?>> result = Stream.of(token.getDeclaredConstructors())
                    .filter(constructor -> {
                        final int modifiers = constructor.getModifiers();
                        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
                    }).collect(Collectors.toList());
            if (result.isEmpty() && !token.isInterface()) {
                throw new ImplerException("Can't extend class without public / protected constructor!");
            } else {
                return result;
            }
        }

        private Triple<String, Set<String>, List<String>> generateParameters(final Constructor<?> constructor) {
            final StringBuilder sb = new StringBuilder();
            final Set<String> imports = new HashSet<>(constructor.getParameterCount());
            final List<String> parameters = new ArrayList<String>(constructor.getParameterCount());
            for (Class<?> parameter : constructor.getParameterTypes()) {
                final String parameterName = variableGenerator.get();
                sb.append(parameter.getSimpleName())
                        .append(' ')
                        .append(parameterName)
                        .append(',');
                if
                parameters.add(parameterName);
                final Optional<String> importStringOptional = getImportForReferenceType(parameter);
                if (importStringOptional.isPresent()) {
                    imports.add(importStringOptional.get());
                }
            }
            return new Triple<String, Set<String>, List<String>>(sb.toString(), imports, parameters);
        }


        @Override
        public final ClassNodeInterface addNode(ClassNodeInterface classNode) {
            return this;
        }
    }

    private Optional<String> getImportForReferenceType(final Class<?> token) {
        Class<?> current = token;
        while (current.isArray()) {
            current = current.getComponentType();
        }
        return current.isPrimitive()
                ? Optional.empty()
                : Optional.of(current.getCanonicalName());
    }

    private static String getSuperCall(final Collection<String> parameters) {
        final Optional<String> optionalResult = parameters.stream()
                .map(parameter -> parameter + ',')
                .reduce((s, s2) -> s + s2);
        if (optionalResult.isPresent()) {
            final String result = optionalResult.get();
            return String.format("super(%1);", result.substring(0, result.length() - 1));
        } else {
            return "";
        }
    }

    class OptionResolver {

        ArrayList<Pair<String, String>> args;
        Integer argcLength = 0;

        OptionResolver() {
            this.args = new ArrayList<>();
        }

        public OptionResolver addArg(String aliasName, String defaultValue) {

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

    public static void main(String[] args) throws ClassNotFoundException, ImplerException {

        Implementor imp = new Implementor(args);
        Path p = FileSystems.getDefault().getPath("/home/aleksandr/IdeaProjects/JavaPractice_ITMO/out/");
        imp.implement(Class.forName(imp.args.get("classname")), p);
    }

    private void process() {
    }

    public class ClassNodeFunctions implements ClassNodeInterface {

        private final Class<?> aClass;

        public ClassNodeFunctions(Class<?> aClass) {
            this.aClass = aClass;
        }

        @Override
        public Pair<String, Set<String>> build() throws ImplerException {
            StringBuilder sb = new StringBuilder();
            Set<String> set = new HashSet<>();
            final Collection<Method> methods = getOverriddableMethods(aClass);

            final Pair<String, Set<String>> methodsStringWithImports = generateMethods(methods);
            sb.append(methodsStringWithImports.getFirst());
            return new Pair<>(sb.toString(), set);
        }

        private Set<Method> getOverriddableMethods(final Class<?> token) {
            return Stream.of(token.getDeclaredMethods(), token.getMethods())
                    .flatMap(Arrays::stream)
                    .filter(m -> Modifier.isAbstract(m.getModifiers()))
                    .collect(Collectors.toSet());
        }

        private Pair<String, Set<String>> generateMethods(final Iterable<Method> methods) {
            final StringBuilder sb = new StringBuilder(1024);
            final Set<String> set = new HashSet<>(1024, 1.0f);
            for (Method method : methods) {
                final Pair<String, Set<String>> parametersWithImports = generateParameters(method);
                sb.append("    @Override").append(System.lineSeparator());
                sb.append(String.format("    %1s%2s %3s(%4s %5s) {",
                        patterns.get("remove_modifiers")
                                .matcher(Modifier.toString(method.getModifiers()))
                                .replaceAll(Matcher.quoteReplacement("")),
                        method.getReturnType().getSimpleName(),
                        method.getName(),
                        parametersWithImports.getFirst(),
                        ""))
                        .append(System.lineSeparator());

                sb.append(String.format("        return %1s;", getDefaultValue(method.getReturnType()))).append(System.lineSeparator());
                sb.append("    }").append(System.lineSeparator());
                final Optional<String> importStringOptional = getImportForReferenceType(method.getReturnType());
                if (importStringOptional.isPresent()) {
                    set.add(importStringOptional.get());
                }
                set.addAll(parametersWithImports.getSecond());
            }
            return new Pair<>(sb.toString(), set);
        }

        private String getDefaultValue(final Class<?> token) {
            return valuesMapper.containsKey(token)
                    ? valuesMapper.get(token)
                    : ' ' + Objects.toString(Array.get(Array.newInstance(token, 1), 0));
        }

        private Pair<String, Set<String>> generateParameters(final Method method) {
            final StringBuilder sb = new StringBuilder();
            final Set<String> imports = new HashSet<>();
            for (Class<?> parameter : method.getParameterTypes()) {
                sb.append(parameter.getSimpleName()).append(' ').append(variableGenerator.get()).append(',');
                final Optional<String> importStringOptional = getImportForReferenceType(parameter);
                if (importStringOptional.isPresent()) {
                    imports.add(importStringOptional.get());
                }
            }
            return new Pair<>(sb.toString(), imports);
        }


        @Override
        public ClassNodeInterface addNode(ClassNodeInterface classNode) {
            return null;
        }


    }
}
