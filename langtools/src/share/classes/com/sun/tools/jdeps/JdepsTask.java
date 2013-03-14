/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.sun.tools.jdeps;

import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.Dependencies;
import com.sun.tools.classfile.Dependencies.ClassFileError;
import com.sun.tools.classfile.Dependency;
import java.io.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Implementation for the jdeps tool for static class dependency analysis.
 */
class JdepsTask {
    class BadArgs extends Exception {
        static final long serialVersionUID = 8765093759964640721L;
        BadArgs(String key, Object... args) {
            super(JdepsTask.getMessage(key, args));
            this.key = key;
            this.args = args;
        }

        BadArgs showUsage(boolean b) {
            showUsage = b;
            return this;
        }
        final String key;
        final Object[] args;
        boolean showUsage;
    }

    static abstract class Option {
        Option(boolean hasArg, String... aliases) {
            this.hasArg = hasArg;
            this.aliases = aliases;
        }

        boolean isHidden() {
            return false;
        }

        boolean matches(String opt) {
            for (String a : aliases) {
                if (a.equals(opt)) {
                    return true;
                } else if (opt.startsWith("--") && hasArg && opt.startsWith(a + "=")) {
                    return true;
                }
            }
            return false;
        }

        boolean ignoreRest() {
            return false;
        }

        abstract void process(JdepsTask task, String opt, String arg) throws BadArgs;
        final boolean hasArg;
        final String[] aliases;
    }

    static abstract class HiddenOption extends Option {
        HiddenOption(boolean hasArg, String... aliases) {
            super(hasArg, aliases);
        }

        boolean isHidden() {
            return true;
        }
    }

    static Option[] recognizedOptions = {
        new Option(false, "-h", "-?", "--help") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.help = true;
            }
        },
        new Option(false, "-s", "--summary") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.showSummary = true;
                task.options.verbose = Analyzer.Type.SUMMARY;
            }
        },
        new Option(false, "-v", "--verbose") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.verbose = Analyzer.Type.VERBOSE;
            }
        },
        new Option(true, "-V", "--verbose-level") {
            void process(JdepsTask task, String opt, String arg) throws BadArgs {
                if ("package".equals(arg)) {
                    task.options.verbose = Analyzer.Type.PACKAGE;
                } else if ("class".equals(arg)) {
                    task.options.verbose = Analyzer.Type.CLASS;
                } else {
                    throw task.new BadArgs("err.invalid.arg.for.option", opt);
                }
            }
        },
        new Option(true, "-c", "--classpath") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.classpath = arg;
            }
        },
        new Option(true, "-p", "--package") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.packageNames.add(arg);
            }
        },
        new Option(true, "-e", "--regex") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.regex = arg;
            }
        },
        new Option(false, "-P", "--profile") {
            void process(JdepsTask task, String opt, String arg) throws BadArgs {
                task.options.showProfile = true;
                if (Profiles.getProfileCount() == 0) {
                    throw task.new BadArgs("err.option.unsupported", opt, getMessage("err.profiles.msg"));
                }
            }
        },
        new Option(false, "-R", "--recursive") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.depth = 0;
            }
        },
        new HiddenOption(true, "-d", "--depth") {
            void process(JdepsTask task, String opt, String arg) throws BadArgs {
                try {
                    task.options.depth = Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    throw task.new BadArgs("err.invalid.arg.for.option", opt);
                }
            }
        },
        new Option(false, "--version") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.version = true;
            }
        },
        new HiddenOption(false, "--fullversion") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.fullVersion = true;
            }
        },
    };

    private static final String PROGNAME = "jdeps";
    private final Options options = new Options();
    private final List<String> classes = new ArrayList<String>();

    private PrintWriter log;
    void setLog(PrintWriter out) {
        log = out;
    }

    /**
     * Result codes.
     */
    static final int EXIT_OK = 0, // Completed with no errors.
                     EXIT_ERROR = 1, // Completed but reported errors.
                     EXIT_CMDERR = 2, // Bad command-line arguments
                     EXIT_SYSERR = 3, // System error or resource exhaustion.
                     EXIT_ABNORMAL = 4;// terminated abnormally

    int run(String[] args) {
        if (log == null) {
            log = new PrintWriter(System.out);
        }
        try {
            handleOptions(args);
            if (options.help) {
                showHelp();
            }
            if (options.version || options.fullVersion) {
                showVersion(options.fullVersion);
            }
            if (classes.isEmpty() && !options.wildcard) {
                if (options.help || options.version || options.fullVersion) {
                    return EXIT_OK;
                } else {
                    showHelp();
                    return EXIT_CMDERR;
                }
            }
            if (options.regex != null && options.packageNames.size() > 0) {
                showHelp();
                return EXIT_CMDERR;
            }
            if (options.showSummary && options.verbose != Analyzer.Type.SUMMARY) {
                showHelp();
                return EXIT_CMDERR;
            }
            boolean ok = run();
            return ok ? EXIT_OK : EXIT_ERROR;
        } catch (BadArgs e) {
            reportError(e.key, e.args);
            if (e.showUsage) {
                log.println(getMessage("main.usage.summary", PROGNAME));
            }
            return EXIT_CMDERR;
        } catch (IOException e) {
            return EXIT_ABNORMAL;
        } finally {
            log.flush();
        }
    }

    private final List<Archive> sourceLocations = new ArrayList<Archive>();
    private boolean run() throws IOException {
        findDependencies();
        Analyzer analyzer = new Analyzer(options.verbose);
        analyzer.run(sourceLocations);
        if (options.verbose == Analyzer.Type.SUMMARY) {
            printSummary(log, analyzer);
        } else {
            printDependencies(log, analyzer);
        }
        return true;
    }

    private boolean isValidClassName(String name) {
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            return false;
        }
        for (int i=1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c != '.'  && !Character.isJavaIdentifierPart(c)) {
                return false;
            }
        }
        return true;
    }

    private void findDependencies() throws IOException {
        Dependency.Finder finder = Dependencies.getClassDependencyFinder();
        Dependency.Filter filter;
        if (options.regex != null) {
            filter = Dependencies.getRegexFilter(Pattern.compile(options.regex));
        } else if (options.packageNames.size() > 0) {
            filter = Dependencies.getPackageFilter(options.packageNames, false);
        } else {
            filter = new Dependency.Filter() {
                public boolean accepts(Dependency dependency) {
                    return !dependency.getOrigin().equals(dependency.getTarget());
                }
            };
        }

        List<Archive> archives = new ArrayList<Archive>();
        Deque<String> roots = new LinkedList<String>();
        for (String s : classes) {
            File f = new File(s);
            if (f.exists()) {
                archives.add(new Archive(f, ClassFileReader.newInstance(f)));
            } else {
                if (isValidClassName(s)) {
                    roots.add(s);
                } else {
                    warning("warn.invalid.arg", s);
                }
            }
        }

        List<Archive> classpaths = new ArrayList<Archive>(); // for class file lookup
        if (options.wildcard) {
            // include all archives from classpath to the initial list
            archives.addAll(getClassPathArchives(options.classpath));
        } else {
            classpaths.addAll(getClassPathArchives(options.classpath));
        }
        classpaths.addAll(PlatformClassPath.getArchives());

        // add all archives to the source locations for reporting
        sourceLocations.addAll(archives);
        sourceLocations.addAll(classpaths);

        // Work queue of names of classfiles to be searched.
        // Entries will be unique, and for classes that do not yet have
        // dependencies in the results map.
        Deque<String> deque = new LinkedList<String>();
        Set<String> doneClasses = new HashSet<String>();

        // get the immediate dependencies of the input files
        for (Archive a : archives) {
            for (ClassFile cf : a.reader().getClassFiles()) {
                String classFileName;
                try {
                    classFileName = cf.getName();
                } catch (ConstantPoolException e) {
                    throw new ClassFileError(e);
                }

                if (!doneClasses.contains(classFileName)) {
                    doneClasses.add(classFileName);
                }
                for (Dependency d : finder.findDependencies(cf)) {
                    if (filter.accepts(d)) {
                        String cn = d.getTarget().getName();
                        if (!doneClasses.contains(cn) && !deque.contains(cn)) {
                            deque.add(cn);
                        }
                        a.addClass(d.getOrigin(), d.getTarget());
                    }
                }
            }
        }

        // add Archive for looking up classes from the classpath
        // for transitive dependency analysis
        Deque<String> unresolved = roots;
        int depth = options.depth > 0 ? options.depth : Integer.MAX_VALUE;
        do {
            String name;
            while ((name = unresolved.poll()) != null) {
                if (doneClasses.contains(name)) {
                    continue;
                }
                ClassFile cf = null;
                for (Archive a : classpaths) {
                    cf = a.reader().getClassFile(name);
                    if (cf != null) {
                        String classFileName;
                        try {
                            classFileName = cf.getName();
                        } catch (ConstantPoolException e) {
                            throw new ClassFileError(e);
                        }
                        if (!doneClasses.contains(classFileName)) {
                            // if name is a fully-qualified class name specified
                            // from command-line, this class might already be parsed
                            doneClasses.add(classFileName);
                            for (Dependency d : finder.findDependencies(cf)) {
                                if (depth == 0) {
                                    // ignore the dependency
                                    a.addClass(d.getOrigin());
                                    break;
                                } else if (filter.accepts(d)) {
                                    a.addClass(d.getOrigin(), d.getTarget());
                                    String cn = d.getTarget().getName();
                                    if (!doneClasses.contains(cn) && !deque.contains(cn)) {
                                        deque.add(cn);
                                    }
                                }
                            }
                        }
                        break;
                    }
                }
                if (cf == null) {
                    doneClasses.add(name);
                }
            }
            unresolved = deque;
            deque = new LinkedList<String>();
        } while (!unresolved.isEmpty() && depth-- > 0);
    }

    private void printSummary(final PrintWriter out, final Analyzer analyzer) {
        Analyzer.Visitor visitor = new Analyzer.Visitor() {
            public void visit(String origin, String target, String profile) {
                if (options.showProfile) {
                    out.format("%-30s -> %s%n", origin, target);
                }
            }
            public void visit(Archive origin, Archive target) {
                if (!options.showProfile) {
                    out.format("%-30s -> %s%n", origin, target);
                }
            }
        };
        analyzer.visitSummary(visitor);
    }

    private void printDependencies(final PrintWriter out, final Analyzer analyzer) {
        Analyzer.Visitor visitor = new Analyzer.Visitor() {
            private String pkg = "";
            public void visit(String origin, String target, String profile) {
                if (!origin.equals(pkg)) {
                    pkg = origin;
                    out.format("   %s (%s)%n", origin, analyzer.getArchive(origin).getFileName());
                }
                out.format("      -> %-50s %s%n", target,
                           (options.showProfile && !profile.isEmpty())
                               ? profile
                               : analyzer.getArchiveName(target, profile));
            }
            public void visit(Archive origin, Archive target) {
                out.format("%s -> %s%n", origin, target);
            }
        };
        analyzer.visit(visitor);
    }

    public void handleOptions(String[] args) throws BadArgs {
        // process options
        for (int i=0; i < args.length; i++) {
            if (args[i].charAt(0) == '-') {
                String name = args[i];
                Option option = getOption(name);
                String param = null;
                if (option.hasArg) {
                    if (name.startsWith("--") && name.indexOf('=') > 0) {
                        param = name.substring(name.indexOf('=') + 1, name.length());
                    } else if (i + 1 < args.length) {
                        param = args[++i];
                    }
                    if (param == null || param.isEmpty() || param.charAt(0) == '-') {
                        throw new BadArgs("err.missing.arg", name).showUsage(true);
                    }
                }
                option.process(this, name, param);
                if (option.ignoreRest()) {
                    i = args.length;
                }
            } else {
                // process rest of the input arguments
                for (; i < args.length; i++) {
                    String name = args[i];
                    if (name.charAt(0) == '-') {
                        throw new BadArgs("err.option.after.class", name).showUsage(true);
                    }
                    if (name.equals("*") || name.equals("\"*\"")) {
                        options.wildcard = true;
                    } else {
                        classes.add(name);
                    }
                }
            }
        }
    }

    private Option getOption(String name) throws BadArgs {
        for (Option o : recognizedOptions) {
            if (o.matches(name)) {
                return o;
            }
        }
        throw new BadArgs("err.unknown.option", name).showUsage(true);
    }

    private void reportError(String key, Object... args) {
        log.println(getMessage("error.prefix") + " " + getMessage(key, args));
    }

    private void warning(String key, Object... args) {
        log.println(getMessage("warn.prefix") + " " + getMessage(key, args));
    }

    private void showHelp() {
        log.println(getMessage("main.usage", PROGNAME));
        for (Option o : recognizedOptions) {
            String name = o.aliases[0].substring(1); // there must always be at least one name
            name = name.charAt(0) == '-' ? name.substring(1) : name;
            if (o.isHidden() || name.equals("h")) {
                continue;
            }
            log.println(getMessage("main.opt." + name));
        }
    }

    private void showVersion(boolean full) {
        log.println(version(full ? "full" : "release"));
    }

    private String version(String key) {
        // key=version:  mm.nn.oo[-milestone]
        // key=full:     mm.mm.oo[-milestone]-build
        if (ResourceBundleHelper.versionRB == null) {
            return System.getProperty("java.version");
        }
        try {
            return ResourceBundleHelper.versionRB.getString(key);
        } catch (MissingResourceException e) {
            return getMessage("version.unknown", System.getProperty("java.version"));
        }
    }

    static String getMessage(String key, Object... args) {
        try {
            return MessageFormat.format(ResourceBundleHelper.bundle.getString(key), args);
        } catch (MissingResourceException e) {
            throw new InternalError("Missing message: " + key);
        }
    }

    private static class Options {
        boolean help;
        boolean version;
        boolean fullVersion;
        boolean showFlags;
        boolean showProfile;
        boolean showSummary;
        boolean wildcard;
        String regex;
        String classpath = "";
        int depth = 1;
        Analyzer.Type verbose = Analyzer.Type.PACKAGE;
        Set<String> packageNames = new HashSet<String>();
    }

    private static class ResourceBundleHelper {
        static final ResourceBundle versionRB;
        static final ResourceBundle bundle;

        static {
            Locale locale = Locale.getDefault();
            try {
                bundle = ResourceBundle.getBundle("com.sun.tools.jdeps.resources.jdeps", locale);
            } catch (MissingResourceException e) {
                throw new InternalError("Cannot find jdeps resource bundle for locale " + locale);
            }
            try {
                versionRB = ResourceBundle.getBundle("com.sun.tools.jdeps.resources.version");
            } catch (MissingResourceException e) {
                throw new InternalError("version.resource.missing");
            }
        }
    }

    private List<Archive> getArchives(List<String> filenames) throws IOException {
        List<Archive> result = new ArrayList<Archive>();
        for (String s : filenames) {
            File f = new File(s);
            if (f.exists()) {
                result.add(new Archive(f, ClassFileReader.newInstance(f)));
            } else {
                warning("warn.file.not.exist", s);
            }
        }
        return result;
    }

    private List<Archive> getClassPathArchives(String paths) throws IOException {
        List<Archive> result = new ArrayList<Archive>();
        if (paths.isEmpty()) {
            return result;
        }
        for (String p : paths.split(File.pathSeparator)) {
            if (p.length() > 0) {
                File f = new File(p);
                if (f.exists()) {
                    result.add(new Archive(f, ClassFileReader.newInstance(f)));
                }
            }
        }
        return result;
    }
}
