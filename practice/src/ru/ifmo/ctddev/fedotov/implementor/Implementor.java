package ru.ifmo.ctddev.fedotov.implementor;


import info.kgeorgiy.java.advanced.implementor.Impler;
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
public class Implementor implements JarImpler, Impler {

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

    /**
     * Class for Node generation etc. imports,class,constructors,function
     */
    interface ClassNodeInterface {
        /**
         * Method for return String build Section ans Imports Set
         *
         * @return Pair
         * @throws ImplerException exception impler
         */
        public Pair<String, Set<String>> build() throws ImplerException;

        /**
         * Add subNode
         *
         * @param classNode ClassNodeInterface
         * @return ClassNodeInterface
         */
        public ClassNodeInterface addNode(ClassNodeInterface classNode);

    }

    /**
     * Abstract Node for combinate another nodes
     */
    abstract class AbstractClassNode implements ClassNodeInterface {
        /**
         * Array list of sub NOdes
         */
        ArrayList<ClassNodeInterface> nodes = new ArrayList<>();

        /**
         * @param classNode ClassNodeInterface
         * @return ClassNodeInterface
         * 
         */
        @Override
        public ClassNodeInterface addNode(ClassNodeInterface classNode) {
            nodes.add(classNode);
            return this;
        }

        /**
         * Return section before  concat node
         *
         * @return String
         */
        protected abstract String getStartSource();

        /**
         * Get section after concat node
         *
         * @return String
         */
        protected abstract String getEndSource();

        /**
         * @return Pair
         * @throws ImplerException exception impler
         * 
         */
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

    /**
     * Point to start add nodes
     */
    class MainNode implements ClassNodeInterface {
        /**
         * Class for analyze
         *
         */
        private final Class<?> aClass;
        /**
         * araay of nodes
         */
        ArrayList<ClassNodeInterface> nodes = new ArrayList<>();

        /**
         * Constructor
         *
         * @param aClass analyze class
         */
        MainNode(Class<?> aClass) {

            this.aClass = aClass;
        }

        /**
         * @param classNode ClassNodeInterface
         * @return ClassNodeInterface
         * 
         */
        @Override
        public ClassNodeInterface addNode(ClassNodeInterface classNode) {
            nodes.add(classNode);
            return this;
        }

        /**
         * @return Pair
         * @throws ImplerException exception impler
         * 
         */
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

        /**
         * Imports node it must be generate late
         *
         */
        Optional<ClassNodeImports> importNode;

        /**
         * set inport node
         *
         * @param cni ClassNodeImports
         * @return MainNode
         */
        public MainNode setImports(ClassNodeImports cni) {
            importNode = Optional.of(cni);
            return this;
        }
    }

    /**
     * Impotr node
     */
    class ClassNodeImports implements ClassNodeInterface {
        /**
         * Imports
         *
         */
        private Set<String> imports;

        /**
         * add imports
         *
         * @param imports Set
         * @return ClassNodeImports
         */
        ClassNodeImports setImports(Set<String> imports) {
            this.imports = imports;
            return this;
        }

        /**
         * @return Pair
         * 
         */
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

        /**
         * @param classNode ClassNodeInterface
         * @return ClassNodeInterface
         * 
         */
        @Override
        public ClassNodeInterface addNode(ClassNodeInterface classNode) {
            return this;
        }
    }

    /**
     * Class node
     */
    class ClassNode extends AbstractClassNode {
        /**
         * 
         */
        private final Class<?> aClass;

        /**
         * Constructro
         *
         * @param aClass Class
         */
        ClassNode(Class<?> aClass) {
            this.aClass = aClass;
        }

        /**
         * @return String
         * 
         */
        @Override
        protected String getStartSource() {
            return String.format("public class %1s %2s %3s {",
                    aClass.getSimpleName() + "Impl",
                    aClass.isInterface() ? "implements" : "extends",
                    aClass.getSimpleName()) +
                    System.lineSeparator();
        }

        /**
         * @return String
         * 
         */
        @Override
        protected String getEndSource() {
            return "}";
        }

    }

    /**
     * Generator for input variable
     *
     */
    private Supplier<String> variableGenerator = new Supplier<String>() {
        Integer counter = 0;

        @Override
        public String get() {
            return "variable" + counter++;
        }
    };

    /**
     * Default value Mapper
     *
     */
    private Map<Class<?>, String> valuesMapper = new HashMap<Class<?>, String>() {{
        put(Void.class, "");
        put(void.class, "");
        put(char.class, " (char) 0");
        put(long.class, " 0L");
        put(float.class, " 0.0f");

    }};

    /**
     * Node Constructor
     *
     */
    class ClassNodeConstructors implements ClassNodeInterface {
        /**
         * Class for analyze
         */
        private final Class<?> aClass;

        /**
         * Consturctor
         *
         * @param aClass Class
         */
        ClassNodeConstructors(Class<?> aClass) {
            this.aClass = aClass;
        }

        /**
         * @return Pair
         * @throws ImplerException exception impler
         * 
         */
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

        /**
         * Get exceptions
         *
         * @param constructor Constructor
         * @return Pair
         */
        private Pair<String, Set<String>> getThrownExceptions(Constructor<?> constructor) {
            final StringBuilder sb = new StringBuilder();
            final Set<String> imports = new HashSet<>(constructor.getExceptionTypes().length);
            Integer len = constructor.getParameterTypes().length;
            Integer i = 0;
            for (Class<?> exception : constructor.getExceptionTypes()) {
                sb.append(exception.getSimpleName());
                if (i < len - 1) {
                    sb.append(',');
                }
                final Optional<String> importStringOptional = getImportForReferenceType(exception);
                if (importStringOptional.isPresent()) {
                    imports.add(importStringOptional.get());
                }
                i++;
            }
            return constructor.getExceptionTypes().length == 0
                    ? new Pair<>("", Collections.emptySet())
                    : new Pair<>("throws " + sb.substring(0, sb.length() - 1), imports);

        }



        /**
         * Check constructor public or private exist
         *
         * @param aClass Class
         * @return Collection
         * @throws ImplerException exception
         */
        private Collection<Constructor<?>> checkConstructors(final Class<?> aClass) throws ImplerException {
            final Collection<Constructor<?>> result = Stream.of(aClass.getDeclaredConstructors())
                    .filter(constructor -> {
                        final int modifiers = constructor.getModifiers();
                        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
                    }).collect(Collectors.toList());
            if (result.isEmpty() && !aClass.isInterface()) {
                throw new ImplerException("Can't extend class without public / protected constructor!");
            } else {
                return result;
            }
        }

        /**
         * Generate input parameters
         *
         * @param constructor Constructor
         * @return Triple
         */
        private Triple<String, Set<String>, List<String>> generateParameters(final Constructor<?> constructor) {
            final StringBuilder sb = new StringBuilder();
            final Set<String> imports = new HashSet<>(constructor.getParameterCount());
            final List<String> parameters = new ArrayList<String>(constructor.getParameterCount());
            Integer len = constructor.getParameterTypes().length;
            Integer i = 0;
            for (Class<?> parameter : constructor.getParameterTypes()) {
                final String parameterName = variableGenerator.get();
                sb.append(parameter.getSimpleName())
                        .append(' ')
                        .append(parameterName);
                if (i < len - 1) {
                    sb.append(',');
                }
                parameters.add(parameterName);
                final Optional<String> importStringOptional = getImportForReferenceType(parameter);
                if (importStringOptional.isPresent()) {
                    imports.add(importStringOptional.get());
                }
                i++;
            }
            return new Triple<String, Set<String>, List<String>>(sb.toString(), imports, parameters);
        }

        /**
         * @param classNode ClassNodeInterface
         * @return ClassNodeInterface
         * 
         */
        @Override
        public final ClassNodeInterface addNode(ClassNodeInterface classNode) {
            return this;
        }
    }

    /**
     * Find imports
     *
     * @param aClass Class
     * @return Optional
     */
    private Optional<String> getImportForReferenceType(final Class<?> aClass) {
        Class<?> current = aClass;
        while (current.isArray()) {
            current = current.getComponentType();
        }
        return current.isPrimitive()
                ? Optional.empty()
                : Optional.of(current.getCanonicalName());
    }

    /**
     * Create super call for method
     *
     * @param parameters Collection
     * @return String
     */
    private String getSuperCall(final Collection<String> parameters) {
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

    /**
     * Option resolver
     */
    class OptionResolver {
        /**
         * Input arguments
         */
        ArrayList<Pair<String, String>> args;
        /**
         * arg length
         */
        Integer argcLength = 0;

        /**
         * constructor
         */
        OptionResolver() {
            this.args = new ArrayList<>();
        }

        /**
         * add  arg by alias and default value
         *
         * @param aliasName    String
         * @param defaultValue String
         * @return OptionResolver
         */
        OptionResolver addArg(String aliasName, String defaultValue) {

            Pair<String, String> nPair = new Pair<>(aliasName, defaultValue);
            args.add(argcLength, nPair);
            argcLength++;
            return this;

        }

        /**
         * Resolve argumntss return resolve map arg = value
         *
         * @param arguments String[]
         * @return Map
         */
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

    /**
     * Constructor of base class
     */
    public Implementor() {
        String[] args = new String[]{};
        OptionResolver resolver = (new OptionResolver())
                .addArg("classname", "javax.sql.rowset.CachedRowSet");
        this.args = resolver.resolveArg(args);
    }

    /**
     * Construtor for input args
     *
     * @param args  String
     */
    public Implementor(String[] args) {
        OptionResolver resolver = (new OptionResolver())
                .addArg("classname", "ru.ifmo.ctddev.fedotov.implementor.Test");
        this.args = resolver.resolveArg(args);
    }

    /**
     * Start point of class
     *
     * @param args String[]
     * @throws ClassNotFoundException not found class for impler
     * @throws ImplerException exception impler
     */
    public static void main(String[] args) throws ClassNotFoundException, ImplerException {
        Implementor imp = new Implementor(args);
        Path p = FileSystems.getDefault().getPath("C:\\Users\\aleksandr\\IdeaProjects\\JavaPractice_ITMO\\practice\\out");
        imp.implement(Class.forName(imp.args.get("classname")), p);
    }

    /**
     * Class for function section
     */
    public class ClassNodeFunctions implements ClassNodeInterface {
        /**
         * Analyze class
         */
        private final Class<?> aClass;

        /**
         * constructor
         *
         * @param aClass Class
         */
        ClassNodeFunctions(Class<?> aClass) {
            this.aClass = aClass;
        }

        /**
         * @return Pair
         * @throws ImplerException exception impler
         * 
         */
        @Override
        public Pair<String, Set<String>> build() throws ImplerException {
            StringBuilder sb = new StringBuilder();
            Set<String> set = new HashSet<>();
            final Collection<Method> methods = getOverriddableMethods(aClass);

            final Pair<String, Set<String>> methodsStringWithImports = generateMethods(methods);
            sb.append(methodsStringWithImports.getFirst());
            set.addAll(methodsStringWithImports.getSecond());
            return new Pair<>(sb.toString(), set);
        }

        /**
         * Get set of overriddable methods of class
         * @param aClass Class
         * @return Set
         */
        private Set<Method> getOverriddableMethods(final Class<?> aClass) {
            return Stream.of(aClass.getDeclaredMethods(), aClass.getMethods())
                    .flatMap(Arrays::stream)
                    .filter(m -> Modifier.isAbstract(m.getModifiers()))
                    .collect(Collectors.toSet());
        }

        /**
         * Return method code and Imports
         * @param methods Iterable
         * @return Pair
         */
        private Pair<String, Set<String>> generateMethods(final Iterable<Method> methods) {
            final StringBuilder sb = new StringBuilder(1024);
            final Set<String> set = new HashSet<>(1024, 1.0f);
            for (Method method : methods) {
                final Pair<String, Set<String>> parametersWithImports = generateParameters(method);
                sb.append("    @Override").append(System.lineSeparator());
                sb.append(String.format("    %1s%2s %3s(%4s ) {",
                        patterns.get("remove_modifiers")
                                .matcher(Modifier.toString(method.getModifiers()))
                                .replaceAll(Matcher.quoteReplacement("")),
                        method.getReturnType().getSimpleName(),
                        method.getName(),
                        parametersWithImports.getFirst()))
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

        /**
         * Default value by type
         * @param aClass Class
         * @return String
         */
        private String getDefaultValue(final Class<?> aClass) {
            return valuesMapper.containsKey(aClass)
                    ? valuesMapper.get(aClass)
                    : ' ' + Objects.toString(Array.get(Array.newInstance(aClass, 1), 0));
        }

        /**
         * Genrate input parametrs
         *
         * @param method Method
         * @return Pair
         */
        private Pair<String, Set<String>> generateParameters(final Method method) {
            final StringBuilder sb = new StringBuilder();
            final Set<String> imports = new HashSet<>();
            Integer len = method.getParameterTypes().length;
            Integer i = 0;
            for (Class<?> parameter : method.getParameterTypes()) {
                sb.append(parameter.getSimpleName())
                        .append(' ')
                        .append(variableGenerator.get());
                if (i < len - 1) {
                    sb.append(',');
                }
                final Optional<String> importStringOptional = getImportForReferenceType(parameter);
                if (importStringOptional.isPresent()) {
                    imports.add(importStringOptional.get());
                }
                i++;
            }
            return new Pair<>(sb.toString(), imports);
        }

        /**
         * 
         * @param classNode ClassNodeInterface
         * @return ClassNodeInterface
         */
        @Override
        public ClassNodeInterface addNode(ClassNodeInterface classNode) {
            return null;
        }


    }
}
