/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.module.Configuration;
import java.lang.module.ModuleReference;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Exports;
import java.lang.module.ModuleDescriptor.Opens;
import java.lang.module.ModuleDescriptor.Version;
import java.lang.module.ResolvedModule;
import java.lang.reflect.AnnotatedElement;
import java.net.URI;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.loader.BuiltinClassLoader;
import jdk.internal.loader.BootLoader;
import jdk.internal.misc.JavaLangAccess;
import jdk.internal.misc.SharedSecrets;
import jdk.internal.module.ModuleLoaderMap;
import jdk.internal.module.ServicesCatalog;
import jdk.internal.module.Resources;
import jdk.internal.org.objectweb.asm.AnnotationVisitor;
import jdk.internal.org.objectweb.asm.Attribute;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;
import sun.security.util.SecurityConstants;

/**
 * Represents a run-time module, either {@link #isNamed() named} or unnamed.
 *
 * <p> Named modules have a {@link #getName() name} and are constructed by the
 * Java Virtual Machine when a graph of modules is defined to the Java virtual
 * machine to create a {@linkplain ModuleLayer module layer}. </p>
 *
 * <p> An unnamed module does not have a name. There is an unnamed module for
 * each {@link ClassLoader ClassLoader}, obtained by invoking its {@link
 * ClassLoader#getUnnamedModule() getUnnamedModule} method. All types that are
 * not in a named module are members of their defining class loader's unnamed
 * module. </p>
 *
 * <p> The package names that are parameters or returned by methods defined in
 * this class are the fully-qualified names of the packages as defined in
 * section 6.5.3 of <cite>The Java&trade; Language Specification</cite>, for
 * example, {@code "java.lang"}. </p>
 *
 * <p> Unless otherwise specified, passing a {@code null} argument to a method
 * in this class causes a {@link NullPointerException NullPointerException} to
 * be thrown. </p>
 *
 * @since 9
 * @spec JPMS
 * @see Class#getModule()
 */

public final class Module implements AnnotatedElement {

    // the layer that contains this module, can be null
    private final ModuleLayer layer;

    // module name and loader, these fields are read by VM
    private final String name;
    private final ClassLoader loader;

    // the module descriptor
    private final ModuleDescriptor descriptor;


    /**
     * Creates a new named Module. The resulting Module will be defined to the
     * VM but will not read any other modules, will not have any exports setup
     * and will not be registered in the service catalog.
     */
    Module(ModuleLayer layer,
           ClassLoader loader,
           ModuleDescriptor descriptor,
           URI uri)
    {
        this.layer = layer;
        this.name = descriptor.name();
        this.loader = loader;
        this.descriptor = descriptor;

        // define module to VM

        boolean isOpen = descriptor.isOpen();
        Version version = descriptor.version().orElse(null);
        String vs = Objects.toString(version, null);
        String loc = Objects.toString(uri, null);
        String[] packages = descriptor.packages().toArray(new String[0]);
        defineModule0(this, isOpen, vs, loc, packages);
    }


    /**
     * Create the unnamed Module for the given ClassLoader.
     *
     * @see ClassLoader#getUnnamedModule
     */
    Module(ClassLoader loader) {
        this.layer = null;
        this.name = null;
        this.loader = loader;
        this.descriptor = null;
    }


    /**
     * Creates a named module but without defining the module to the VM.
     *
     * @apiNote This constructor is for VM white-box testing.
     */
    Module(ClassLoader loader, ModuleDescriptor descriptor) {
        this.layer = null;
        this.name = descriptor.name();
        this.loader = loader;
        this.descriptor = descriptor;
    }



    /**
     * Returns {@code true} if this module is a named module.
     *
     * @return {@code true} if this is a named module
     *
     * @see ClassLoader#getUnnamedModule()
     */
    public boolean isNamed() {
        return name != null;
    }

    /**
     * Returns the module name or {@code null} if this module is an unnamed
     * module.
     *
     * @return The module name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the {@code ClassLoader} for this module.
     *
     * <p> If there is a security manager then its {@code checkPermission}
     * method if first called with a {@code RuntimePermission("getClassLoader")}
     * permission to check that the caller is allowed to get access to the
     * class loader. </p>
     *
     * @return The class loader for this module
     *
     * @throws SecurityException
     *         If denied by the security manager
     */
    public ClassLoader getClassLoader() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(SecurityConstants.GET_CLASSLOADER_PERMISSION);
        }
        return loader;
    }

    /**
     * Returns the module descriptor for this module or {@code null} if this
     * module is an unnamed module.
     *
     * @return The module descriptor for this module
     */
    public ModuleDescriptor getDescriptor() {
        return descriptor;
    }

    /**
     * Returns the module layer that contains this module or {@code null} if
     * this module is not in a module layer.
     *
     * A module layer contains named modules and therefore this method always
     * returns {@code null} when invoked on an unnamed module.
     *
     * <p> <a href="reflect/Proxy.html#dynamicmodule">Dynamic modules</a> are
     * named modules that are generated at runtime. A dynamic module may or may
     * not be in a module layer. </p>
     *
     * @return The module layer that contains this module
     *
     * @see java.lang.reflect.Proxy
     */
    public ModuleLayer getLayer() {
        if (isNamed()) {
            ModuleLayer layer = this.layer;
            if (layer != null)
                return layer;

            // special-case java.base as it is created before the boot layer
            if (loader == null && name.equals("java.base")) {
                return ModuleLayer.boot();
            }
        }
        return null;
    }


    // --

    // special Module to mean "all unnamed modules"
    private static final Module ALL_UNNAMED_MODULE = new Module(null);

    // special Module to mean "everyone"
    private static final Module EVERYONE_MODULE = new Module(null);

    // set contains EVERYONE_MODULE, used when a package is opened or
    // exported unconditionally
    private static final Set<Module> EVERYONE_SET = Set.of(EVERYONE_MODULE);


    // -- readability --

    // the modules that this module reads
    private volatile Set<Module> reads;

    // additional module (2nd key) that some module (1st key) reflectively reads
    private static final WeakPairMap<Module, Module, Boolean> reflectivelyReads
        = new WeakPairMap<>();


    /**
     * Indicates if this module reads the given module. This method returns
     * {@code true} if invoked to test if this module reads itself. It also
     * returns {@code true} if invoked on an unnamed module (as unnamed
     * modules read all modules).
     *
     * @param  other
     *         The other module
     *
     * @return {@code true} if this module reads {@code other}
     *
     * @see #addReads(Module)
     */
    public boolean canRead(Module other) {
        Objects.requireNonNull(other);

        // an unnamed module reads all modules
        if (!this.isNamed())
            return true;

        // all modules read themselves
        if (other == this)
            return true;

        // check if this module reads other
        if (other.isNamed()) {
            Set<Module> reads = this.reads; // volatile read
            if (reads != null && reads.contains(other))
                return true;
        }

        // check if this module reads the other module reflectively
        if (reflectivelyReads.containsKeyPair(this, other))
            return true;

        // if other is an unnamed module then check if this module reads
        // all unnamed modules
        if (!other.isNamed()
            && reflectivelyReads.containsKeyPair(this, ALL_UNNAMED_MODULE))
            return true;

        return false;
    }

    /**
     * If the caller's module is this module then update this module to read
     * the given module.
     *
     * This method is a no-op if {@code other} is this module (all modules read
     * themselves), this module is an unnamed module (as unnamed modules read
     * all modules), or this module already reads {@code other}.
     *
     * @implNote <em>Read edges</em> added by this method are <em>weak</em> and
     * do not prevent {@code other} from being GC'ed when this module is
     * strongly reachable.
     *
     * @param  other
     *         The other module
     *
     * @return this module
     *
     * @throws IllegalCallerException
     *         If this is a named module and the caller's module is not this
     *         module
     *
     * @see #canRead
     */
    @CallerSensitive
    public Module addReads(Module other) {
        Objects.requireNonNull(other);
        if (this.isNamed()) {
            Module caller = getCallerModule(Reflection.getCallerClass());
            if (caller != this) {
                throw new IllegalCallerException(caller + " != " + this);
            }
            implAddReads(other, true);
        }
        return this;
    }

    /**
     * Updates this module to read another module.
     *
     * @apiNote Used by the --add-reads command line option.
     */
    void implAddReads(Module other) {
        implAddReads(other, true);
    }

    /**
     * Updates this module to read all unnamed modules.
     *
     * @apiNote Used by the --add-reads command line option.
     */
    void implAddReadsAllUnnamed() {
        implAddReads(Module.ALL_UNNAMED_MODULE, true);
    }

    /**
     * Updates this module to read another module without notifying the VM.
     *
     * @apiNote This method is for VM white-box testing.
     */
    void implAddReadsNoSync(Module other) {
        implAddReads(other, false);
    }

    /**
     * Makes the given {@code Module} readable to this module.
     *
     * If {@code syncVM} is {@code true} then the VM is notified.
     */
    private void implAddReads(Module other, boolean syncVM) {
        Objects.requireNonNull(other);
        if (!canRead(other)) {
            // update VM first, just in case it fails
            if (syncVM) {
                if (other == ALL_UNNAMED_MODULE) {
                    addReads0(this, null);
                } else {
                    addReads0(this, other);
                }
            }

            // add reflective read
            reflectivelyReads.putIfAbsent(this, other, Boolean.TRUE);
        }
    }


    // -- exported and open packages --

    // the packages are open to other modules, can be null
    // if the value contains EVERYONE_MODULE then the package is open to all
    private volatile Map<String, Set<Module>> openPackages;

    // the packages that are exported, can be null
    // if the value contains EVERYONE_MODULE then the package is exported to all
    private volatile Map<String, Set<Module>> exportedPackages;

    // additional exports or opens added at run-time
    // this module (1st key), other module (2nd key)
    // (package name, open?) (value)
    private static final WeakPairMap<Module, Module, Map<String, Boolean>>
        reflectivelyExports = new WeakPairMap<>();


    /**
     * Returns {@code true} if this module exports the given package to at
     * least the given module.
     *
     * <p> This method returns {@code true} if invoked to test if a package in
     * this module is exported to itself. It always returns {@code true} when
     * invoked on an unnamed module. A package that is {@link #isOpen open} to
     * the given module is considered exported to that module at run-time and
     * so this method returns {@code true} if the package is open to the given
     * module. </p>
     *
     * <p> This method does not check if the given module reads this module. </p>
     *
     * @param  pn
     *         The package name
     * @param  other
     *         The other module
     *
     * @return {@code true} if this module exports the package to at least the
     *         given module
     *
     * @see ModuleDescriptor#exports()
     * @see #addExports(String,Module)
     */
    public boolean isExported(String pn, Module other) {
        Objects.requireNonNull(pn);
        Objects.requireNonNull(other);
        return implIsExportedOrOpen(pn, other, /*open*/false);
    }

    /**
     * Returns {@code true} if this module has <em>opened</em> a package to at
     * least the given module.
     *
     * <p> This method returns {@code true} if invoked to test if a package in
     * this module is open to itself. It returns {@code true} when invoked on an
     * {@link ModuleDescriptor#isOpen open} module with a package in the module.
     * It always returns {@code true} when invoked on an unnamed module. </p>
     *
     * <p> This method does not check if the given module reads this module. </p>
     *
     * @param  pn
     *         The package name
     * @param  other
     *         The other module
     *
     * @return {@code true} if this module has <em>opened</em> the package
     *         to at least the given module
     *
     * @see ModuleDescriptor#opens()
     * @see #addOpens(String,Module)
     * @see AccessibleObject#setAccessible(boolean)
     * @see java.lang.invoke.MethodHandles#privateLookupIn
     */
    public boolean isOpen(String pn, Module other) {
        Objects.requireNonNull(pn);
        Objects.requireNonNull(other);
        return implIsExportedOrOpen(pn, other, /*open*/true);
    }

    /**
     * Returns {@code true} if this module exports the given package
     * unconditionally.
     *
     * <p> This method always returns {@code true} when invoked on an unnamed
     * module. A package that is {@link #isOpen(String) opened} unconditionally
     * is considered exported unconditionally at run-time and so this method
     * returns {@code true} if the package is opened unconditionally. </p>
     *
     * <p> This method does not check if the given module reads this module. </p>
     *
     * @param  pn
     *         The package name
     *
     * @return {@code true} if this module exports the package unconditionally
     *
     * @see ModuleDescriptor#exports()
     */
    public boolean isExported(String pn) {
        Objects.requireNonNull(pn);
        return implIsExportedOrOpen(pn, EVERYONE_MODULE, /*open*/false);
    }

    /**
     * Returns {@code true} if this module has <em>opened</em> a package
     * unconditionally.
     *
     * <p> This method always returns {@code true} when invoked on an unnamed
     * module. Additionally, it always returns {@code true} when invoked on an
     * {@link ModuleDescriptor#isOpen open} module with a package in the
     * module. </p>
     *
     * <p> This method does not check if the given module reads this module. </p>
     *
     * @param  pn
     *         The package name
     *
     * @return {@code true} if this module has <em>opened</em> the package
     *         unconditionally
     *
     * @see ModuleDescriptor#opens()
     */
    public boolean isOpen(String pn) {
        Objects.requireNonNull(pn);
        return implIsExportedOrOpen(pn, EVERYONE_MODULE, /*open*/true);
    }


    /**
     * Returns {@code true} if this module exports or opens the given package
     * to the given module. If the other module is {@code EVERYONE_MODULE} then
     * this method tests if the package is exported or opened unconditionally.
     */
    private boolean implIsExportedOrOpen(String pn, Module other, boolean open) {
        // all packages in unnamed modules are open
        if (!isNamed())
            return true;

        // all packages are exported/open to self
        if (other == this && containsPackage(pn))
            return true;

        // all packages in open and automatic modules are open
        if (descriptor.isOpen() || descriptor.isAutomatic())
            return containsPackage(pn);

        // exported/opened via module declaration/descriptor
        if (isStaticallyExportedOrOpen(pn, other, open))
            return true;

        // exported via addExports/addOpens
        if (isReflectivelyExportedOrOpen(pn, other, open))
            return true;

        // not exported or open to other
        return false;
    }

    /**
     * Returns {@code true} if this module exports or opens a package to
     * the given module via its module declaration.
     */
    private boolean isStaticallyExportedOrOpen(String pn, Module other, boolean open) {
        // package is open to everyone or <other>
        Map<String, Set<Module>> openPackages = this.openPackages;
        if (openPackages != null) {
            Set<Module> targets = openPackages.get(pn);
            if (targets != null) {
                if (targets.contains(EVERYONE_MODULE))
                    return true;
                if (other != EVERYONE_MODULE && targets.contains(other))
                    return true;
            }
        }

        if (!open) {
            // package is exported to everyone or <other>
            Map<String, Set<Module>> exportedPackages = this.exportedPackages;
            if (exportedPackages != null) {
                Set<Module> targets = exportedPackages.get(pn);
                if (targets != null) {
                    if (targets.contains(EVERYONE_MODULE))
                        return true;
                    if (other != EVERYONE_MODULE && targets.contains(other))
                        return true;
                }
            }
        }

        return false;
    }


    /**
     * Returns {@code true} if this module reflectively exports or opens given
     * package package to the given module.
     */
    private boolean isReflectivelyExportedOrOpen(String pn, Module other, boolean open) {
        // exported or open to all modules
        Map<String, Boolean> exports = reflectivelyExports.get(this, EVERYONE_MODULE);
        if (exports != null) {
            Boolean b = exports.get(pn);
            if (b != null) {
                boolean isOpen = b.booleanValue();
                if (!open || isOpen) return true;
            }
        }

        if (other != EVERYONE_MODULE) {

            // exported or open to other
            exports = reflectivelyExports.get(this, other);
            if (exports != null) {
                Boolean b = exports.get(pn);
                if (b != null) {
                    boolean isOpen = b.booleanValue();
                    if (!open || isOpen) return true;
                }
            }

            // other is an unnamed module && exported or open to all unnamed
            if (!other.isNamed()) {
                exports = reflectivelyExports.get(this, ALL_UNNAMED_MODULE);
                if (exports != null) {
                    Boolean b = exports.get(pn);
                    if (b != null) {
                        boolean isOpen = b.booleanValue();
                        if (!open || isOpen) return true;
                    }
                }
            }

        }

        return false;
    }


    /**
     * If the caller's module is this module then update this module to export
     * the given package to the given module.
     *
     * <p> This method has no effect if the package is already exported (or
     * <em>open</em>) to the given module. </p>
     *
     * @apiNote As specified in section 5.4.3 of the <cite>The Java&trade;
     * Virtual Machine Specification </cite>, if an attempt to resolve a
     * symbolic reference fails because of a linkage error, then subsequent
     * attempts to resolve the reference always fail with the same error that
     * was thrown as a result of the initial resolution attempt.
     *
     * @param  pn
     *         The package name
     * @param  other
     *         The module
     *
     * @return this module
     *
     * @throws IllegalArgumentException
     *         If {@code pn} is {@code null}, or this is a named module and the
     *         package {@code pn} is not a package in this module
     * @throws IllegalCallerException
     *         If this is a named module and the caller's module is not this
     *         module
     *
     * @jvms 5.4.3 Resolution
     * @see #isExported(String,Module)
     */
    @CallerSensitive
    public Module addExports(String pn, Module other) {
        if (pn == null)
            throw new IllegalArgumentException("package is null");
        Objects.requireNonNull(other);

        if (isNamed()) {
            Module caller = getCallerModule(Reflection.getCallerClass());
            if (caller != this) {
                throw new IllegalCallerException(caller + " != " + this);
            }
            implAddExportsOrOpens(pn, other, /*open*/false, /*syncVM*/true);
        }

        return this;
    }

    /**
     * If this module has <em>opened</em> a package to at least the caller
     * module then update this module to open the package to the given module.
     * Opening a package with this method allows all types in the package,
     * and all their members, not just public types and their public members,
     * to be reflected on by the given module when using APIs that support
     * private access or a way to bypass or suppress default Java language
     * access control checks.
     *
     * <p> This method has no effect if the package is already <em>open</em>
     * to the given module. </p>
     *
     * @apiNote This method can be used for cases where a <em>consumer
     * module</em> uses a qualified opens to open a package to an <em>API
     * module</em> but where the reflective access to the members of classes in
     * the consumer module is delegated to code in another module. Code in the
     * API module can use this method to open the package in the consumer module
     * to the other module.
     *
     * @param  pn
     *         The package name
     * @param  other
     *         The module
     *
     * @return this module
     *
     * @throws IllegalArgumentException
     *         If {@code pn} is {@code null}, or this is a named module and the
     *         package {@code pn} is not a package in this module
     * @throws IllegalCallerException
     *         If this is a named module and this module has not opened the
     *         package to at least the caller's module
     *
     * @see #isOpen(String,Module)
     * @see AccessibleObject#setAccessible(boolean)
     * @see java.lang.invoke.MethodHandles#privateLookupIn
     */
    @CallerSensitive
    public Module addOpens(String pn, Module other) {
        if (pn == null)
            throw new IllegalArgumentException("package is null");
        Objects.requireNonNull(other);

        if (isNamed()) {
            Module caller = getCallerModule(Reflection.getCallerClass());
            if (caller != this && (caller == null || !isOpen(pn, caller)))
                throw new IllegalCallerException(pn + " is not open to " + caller);
            implAddExportsOrOpens(pn, other, /*open*/true, /*syncVM*/true);
        }

        return this;
    }


    /**
     * Updates this module to export a package unconditionally.
     *
     * @apiNote This method is for JDK tests only.
     */
    void implAddExports(String pn) {
        implAddExportsOrOpens(pn, Module.EVERYONE_MODULE, false, true);
    }

    /**
     * Updates this module to export a package to another module.
     *
     * @apiNote Used by Instrumentation::redefineModule and --add-exports
     */
    void implAddExports(String pn, Module other) {
        implAddExportsOrOpens(pn, other, false, true);
    }

    /**
     * Updates this module to export a package to all unnamed modules.
     *
     * @apiNote Used by the --add-exports command line option.
     */
    void implAddExportsToAllUnnamed(String pn) {
        implAddExportsOrOpens(pn, Module.ALL_UNNAMED_MODULE, false, true);
    }

    /**
     * Updates this export to export a package unconditionally without
     * notifying the VM.
     *
     * @apiNote This method is for VM white-box testing.
     */
    void implAddExportsNoSync(String pn) {
        implAddExportsOrOpens(pn.replace('/', '.'), Module.EVERYONE_MODULE, false, false);
    }

    /**
     * Updates a module to export a package to another module without
     * notifying the VM.
     *
     * @apiNote This method is for VM white-box testing.
     */
    void implAddExportsNoSync(String pn, Module other) {
        implAddExportsOrOpens(pn.replace('/', '.'), other, false, false);
    }

    /**
     * Updates this module to open a package unconditionally.
     *
     * @apiNote This method is for JDK tests only.
     */
    void implAddOpens(String pn) {
        implAddExportsOrOpens(pn, Module.EVERYONE_MODULE, true, true);
    }

    /**
     * Updates this module to open a package to another module.
     *
     * @apiNote Used by Instrumentation::redefineModule and --add-opens
     */
    void implAddOpens(String pn, Module other) {
        implAddExportsOrOpens(pn, other, true, true);
    }

    /**
     * Updates this module to export a package to all unnamed modules.
     *
     * @apiNote Used by the --add-opens command line option.
     */
    void implAddOpensToAllUnnamed(String pn) {
        implAddExportsOrOpens(pn, Module.ALL_UNNAMED_MODULE, true, true);
    }


    /**
     * Updates a module to export or open a module to another module.
     *
     * If {@code syncVM} is {@code true} then the VM is notified.
     */
    private void implAddExportsOrOpens(String pn,
                                       Module other,
                                       boolean open,
                                       boolean syncVM) {
        Objects.requireNonNull(other);
        Objects.requireNonNull(pn);

        // all packages are open in unnamed, open, and automatic modules
        if (!isNamed() || descriptor.isOpen() || descriptor.isAutomatic())
            return;

        // nothing to do if already exported/open to other
        if (implIsExportedOrOpen(pn, other, open))
            return;

        // can only export a package in the module
        if (!containsPackage(pn)) {
            throw new IllegalArgumentException("package " + pn
                                               + " not in contents");
        }

        // update VM first, just in case it fails
        if (syncVM) {
            if (other == EVERYONE_MODULE) {
                addExportsToAll0(this, pn);
            } else if (other == ALL_UNNAMED_MODULE) {
                addExportsToAllUnnamed0(this, pn);
            } else {
                addExports0(this, pn, other);
            }
        }

        // add package name to reflectivelyExports if absent
        Map<String, Boolean> map = reflectivelyExports
            .computeIfAbsent(this, other,
                             (m1, m2) -> new ConcurrentHashMap<>());

        if (open) {
            map.put(pn, Boolean.TRUE);  // may need to promote from FALSE to TRUE
        } else {
            map.putIfAbsent(pn, Boolean.FALSE);
        }
    }


    // -- services --

    // additional service type (2nd key) that some module (1st key) uses
    private static final WeakPairMap<Module, Class<?>, Boolean> reflectivelyUses
        = new WeakPairMap<>();

    /**
     * If the caller's module is this module then update this module to add a
     * service dependence on the given service type. This method is intended
     * for use by frameworks that invoke {@link java.util.ServiceLoader
     * ServiceLoader} on behalf of other modules or where the framework is
     * passed a reference to the service type by other code. This method is
     * a no-op when invoked on an unnamed module or an automatic module.
     *
     * <p> This method does not cause {@link Configuration#resolveAndBind
     * resolveAndBind} to be re-run. </p>
     *
     * @param  service
     *         The service type
     *
     * @return this module
     *
     * @throws IllegalCallerException
     *         If this is a named module and the caller's module is not this
     *         module
     *
     * @see #canUse(Class)
     * @see ModuleDescriptor#uses()
     */
    @CallerSensitive
    public Module addUses(Class<?> service) {
        Objects.requireNonNull(service);

        if (isNamed() && !descriptor.isAutomatic()) {
            Module caller = getCallerModule(Reflection.getCallerClass());
            if (caller != this) {
                throw new IllegalCallerException(caller + " != " + this);
            }
            implAddUses(service);
        }

        return this;
    }

    /**
     * Update this module to add a service dependence on the given service
     * type.
     */
    void implAddUses(Class<?> service) {
        if (!canUse(service)) {
            reflectivelyUses.putIfAbsent(this, service, Boolean.TRUE);
        }
    }


    /**
     * Indicates if this module has a service dependence on the given service
     * type. This method always returns {@code true} when invoked on an unnamed
     * module or an automatic module.
     *
     * @param  service
     *         The service type
     *
     * @return {@code true} if this module uses service type {@code st}
     *
     * @see #addUses(Class)
     */
    public boolean canUse(Class<?> service) {
        Objects.requireNonNull(service);

        if (!isNamed())
            return true;

        if (descriptor.isAutomatic())
            return true;

        // uses was declared
        if (descriptor.uses().contains(service.getName()))
            return true;

        // uses added via addUses
        return reflectivelyUses.containsKeyPair(this, service);
    }



    // -- packages --

    // Additional packages that are added to the module at run-time.
    private volatile Map<String, Boolean> extraPackages;

    private boolean containsPackage(String pn) {
        if (descriptor.packages().contains(pn))
            return true;
        Map<String, Boolean> extraPackages = this.extraPackages;
        if (extraPackages != null && extraPackages.containsKey(pn))
            return true;
        return false;
    }


    /**
     * Returns the set of package names for the packages in this module.
     *
     * <p> For named modules, the returned set contains an element for each
     * package in the module. </p>
     *
     * <p> For unnamed modules, this method is the equivalent to invoking the
     * {@link ClassLoader#getDefinedPackages() getDefinedPackages} method of
     * this module's class loader and returning the set of package names. </p>
     *
     * @return the set of the package names of the packages in this module
     */
    public Set<String> getPackages() {
        if (isNamed()) {

            Set<String> packages = descriptor.packages();
            Map<String, Boolean> extraPackages = this.extraPackages;
            if (extraPackages == null) {
                return packages;
            } else {
                return Stream.concat(packages.stream(),
                                     extraPackages.keySet().stream())
                        .collect(Collectors.toSet());
            }

        } else {
            // unnamed module
            Stream<Package> packages;
            if (loader == null) {
                packages = BootLoader.packages();
            } else {
                packages = SharedSecrets.getJavaLangAccess().packages(loader);
            }
            return packages.map(Package::getName).collect(Collectors.toSet());
        }
    }

    /**
     * Add a package to this module without notifying the VM.
     *
     * @apiNote This method is VM white-box testing.
     */
    void implAddPackageNoSync(String pn) {
        implAddPackage(pn.replace('/', '.'), false);
    }

    /**
     * Add a package to this module.
     *
     * If {@code syncVM} is {@code true} then the VM is notified. This method is
     * a no-op if this is an unnamed module or the module already contains the
     * package.
     *
     * @throws IllegalArgumentException if the package name is not legal
     * @throws IllegalStateException if the package is defined to another module
     */
    private void implAddPackage(String pn, boolean syncVM) {
        // no-op if unnamed module
        if (!isNamed())
            return;

        // no-op if module contains the package
        if (containsPackage(pn))
            return;

        // check package name is legal for named modules
        if (pn.isEmpty())
            throw new IllegalArgumentException("Cannot add <unnamed> package");
        for (int i=0; i<pn.length(); i++) {
            char c = pn.charAt(i);
            if (c == '/' || c == ';' || c == '[') {
                throw new IllegalArgumentException("Illegal character: " + c);
            }
        }

        // create extraPackages if needed
        Map<String, Boolean> extraPackages = this.extraPackages;
        if (extraPackages == null) {
            synchronized (this) {
                extraPackages = this.extraPackages;
                if (extraPackages == null)
                    this.extraPackages = extraPackages = new ConcurrentHashMap<>();
            }
        }

        // update VM first in case it fails. This is a no-op if another thread
        // beats us to add the package first
        if (syncVM) {
            // throws IllegalStateException if defined to another module
            addPackage0(this, pn);
            if (descriptor.isOpen() || descriptor.isAutomatic()) {
                addExportsToAll0(this, pn);
            }
        }
        extraPackages.putIfAbsent(pn, Boolean.TRUE);
    }


    // -- creating Module objects --

    /**
     * Defines all module in a configuration to the runtime.
     *
     * @return a map of module name to runtime {@code Module}
     *
     * @throws IllegalArgumentException
     *         If defining any of the modules to the VM fails
     */
    static Map<String, Module> defineModules(Configuration cf,
                                             Function<String, ClassLoader> clf,
                                             ModuleLayer layer)
    {
        Map<String, Module> nameToModule = new HashMap<>();
        Map<String, ClassLoader> moduleToLoader = new HashMap<>();

        boolean isBootLayer = (ModuleLayer.boot() == null);
        Set<ClassLoader> loaders = new HashSet<>();

        // map each module to a class loader
        for (ResolvedModule resolvedModule : cf.modules()) {
            String name = resolvedModule.name();
            ClassLoader loader = clf.apply(name);
            if (loader != null) {
                moduleToLoader.put(name, loader);
                loaders.add(loader);
            } else if (!(clf instanceof ModuleLoaderMap.Mapper)) {
                throw new IllegalArgumentException("loader can't be 'null'");
            }
        }

        // define each module in the configuration to the VM
        for (ResolvedModule resolvedModule : cf.modules()) {
            ModuleReference mref = resolvedModule.reference();
            ModuleDescriptor descriptor = mref.descriptor();
            String name = descriptor.name();
            URI uri = mref.location().orElse(null);
            ClassLoader loader = moduleToLoader.get(resolvedModule.name());
            Module m;
            if (loader == null && isBootLayer && name.equals("java.base")) {
                // java.base is already defined to the VM
                m = Object.class.getModule();
            } else {
                m = new Module(layer, loader, descriptor, uri);
            }
            nameToModule.put(name, m);
            moduleToLoader.put(name, loader);
        }

        // setup readability and exports
        for (ResolvedModule resolvedModule : cf.modules()) {
            ModuleReference mref = resolvedModule.reference();
            ModuleDescriptor descriptor = mref.descriptor();

            String mn = descriptor.name();
            Module m = nameToModule.get(mn);
            assert m != null;

            // reads
            Set<Module> reads = new HashSet<>();

            // name -> source Module when in parent layer
            Map<String, Module> nameToSource = Collections.emptyMap();

            for (ResolvedModule other : resolvedModule.reads()) {
                Module m2 = null;
                if (other.configuration() == cf) {
                    // this configuration
                    m2 = nameToModule.get(other.name());
                    assert m2 != null;
                } else {
                    // parent layer
                    for (ModuleLayer parent: layer.parents()) {
                        m2 = findModule(parent, other);
                        if (m2 != null)
                            break;
                    }
                    assert m2 != null;
                    if (nameToSource.isEmpty())
                        nameToSource = new HashMap<>();
                    nameToSource.put(other.name(), m2);
                }
                reads.add(m2);

                // update VM view
                addReads0(m, m2);
            }
            m.reads = reads;

            // automatic modules read all unnamed modules
            if (descriptor.isAutomatic()) {
                m.implAddReads(ALL_UNNAMED_MODULE, true);
            }

            // exports and opens
            initExportsAndOpens(m, nameToSource, nameToModule, layer.parents());
        }

        // register the modules in the boot layer
        if (isBootLayer) {
            for (ResolvedModule resolvedModule : cf.modules()) {
                ModuleReference mref = resolvedModule.reference();
                ModuleDescriptor descriptor = mref.descriptor();
                if (!descriptor.provides().isEmpty()) {
                    String name = descriptor.name();
                    Module m = nameToModule.get(name);
                    ClassLoader loader = moduleToLoader.get(name);
                    ServicesCatalog catalog;
                    if (loader == null) {
                        catalog = BootLoader.getServicesCatalog();
                    } else {
                        catalog = ServicesCatalog.getServicesCatalog(loader);
                    }
                    catalog.register(m);
                }
            }
        }

        // record that there is a layer with modules defined to the class loader
        for (ClassLoader loader : loaders) {
            layer.bindToLoader(loader);
        }

        return nameToModule;
    }


    /**
     * Find the runtime Module corresponding to the given ResolvedModule
     * in the given parent layer (or its parents).
     */
    private static Module findModule(ModuleLayer parent,
                                     ResolvedModule resolvedModule) {
        Configuration cf = resolvedModule.configuration();
        String dn = resolvedModule.name();
        return parent.layers()
                .filter(l -> l.configuration() == cf)
                .findAny()
                .map(layer -> {
                    Optional<Module> om = layer.findModule(dn);
                    assert om.isPresent() : dn + " not found in layer";
                    Module m = om.get();
                    assert m.getLayer() == layer : m + " not in expected layer";
                    return m;
                })
                .orElse(null);
    }


    /**
     * Initialize the maps of exported and open packages for module m.
     */
    private static void initExportsAndOpens(Module m,
                                            Map<String, Module> nameToSource,
                                            Map<String, Module> nameToModule,
                                            List<ModuleLayer> parents) {
        // The VM doesn't special case open or automatic modules so need to
        // export all packages
        ModuleDescriptor descriptor = m.getDescriptor();
        if (descriptor.isOpen() || descriptor.isAutomatic()) {
            assert descriptor.opens().isEmpty();
            for (String source : descriptor.packages()) {
                addExportsToAll0(m, source);
            }
            return;
        }

        Map<String, Set<Module>> openPackages = new HashMap<>();
        Map<String, Set<Module>> exportedPackages = new HashMap<>();

        // process the open packages first
        for (Opens opens : descriptor.opens()) {
            String source = opens.source();

            if (opens.isQualified()) {
                // qualified opens
                Set<Module> targets = new HashSet<>();
                for (String target : opens.targets()) {
                    Module m2 = findModule(target, nameToSource, nameToModule, parents);
                    if (m2 != null) {
                        addExports0(m, source, m2);
                        targets.add(m2);
                    }
                }
                if (!targets.isEmpty()) {
                    openPackages.put(source, targets);
                }
            } else {
                // unqualified opens
                addExportsToAll0(m, source);
                openPackages.put(source, EVERYONE_SET);
            }
        }

        // next the exports, skipping exports when the package is open
        for (Exports exports : descriptor.exports()) {
            String source = exports.source();

            // skip export if package is already open to everyone
            Set<Module> openToTargets = openPackages.get(source);
            if (openToTargets != null && openToTargets.contains(EVERYONE_MODULE))
                continue;

            if (exports.isQualified()) {
                // qualified exports
                Set<Module> targets = new HashSet<>();
                for (String target : exports.targets()) {
                    Module m2 = findModule(target, nameToSource, nameToModule, parents);
                    if (m2 != null) {
                        // skip qualified export if already open to m2
                        if (openToTargets == null || !openToTargets.contains(m2)) {
                            addExports0(m, source, m2);
                            targets.add(m2);
                        }
                    }
                }
                if (!targets.isEmpty()) {
                    exportedPackages.put(source, targets);
                }

            } else {
                // unqualified exports
                addExportsToAll0(m, source);
                exportedPackages.put(source, EVERYONE_SET);
            }
        }

        if (!openPackages.isEmpty())
            m.openPackages = openPackages;
        if (!exportedPackages.isEmpty())
            m.exportedPackages = exportedPackages;
    }

    /**
     * Find the runtime Module with the given name. The module name is the
     * name of a target module in a qualified exports or opens directive.
     *
     * @param target The target module to find
     * @param nameToSource The modules in parent layers that are read
     * @param nameToModule The modules in the layer under construction
     * @param parents The parent layers
     */
    private static Module findModule(String target,
                                     Map<String, Module> nameToSource,
                                     Map<String, Module> nameToModule,
                                     List<ModuleLayer> parents) {
        Module m = nameToSource.get(target);
        if (m == null) {
            m = nameToModule.get(target);
            if (m == null) {
                for (ModuleLayer parent : parents) {
                    m = parent.findModule(target).orElse(null);
                    if (m != null) break;
                }
            }
        }
        return m;
    }


    // -- annotations --

    /**
     * {@inheritDoc}
     * This method returns {@code null} when invoked on an unnamed module.
     */
    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return moduleInfoClass().getDeclaredAnnotation(annotationClass);
    }

    /**
     * {@inheritDoc}
     * This method returns an empty array when invoked on an unnamed module.
     */
    @Override
    public Annotation[] getAnnotations() {
        return moduleInfoClass().getAnnotations();
    }

    /**
     * {@inheritDoc}
     * This method returns an empty array when invoked on an unnamed module.
     */
    @Override
    public Annotation[] getDeclaredAnnotations() {
        return moduleInfoClass().getDeclaredAnnotations();
    }

    // cached class file with annotations
    private volatile Class<?> moduleInfoClass;

    private Class<?> moduleInfoClass() {
        Class<?> clazz = this.moduleInfoClass;
        if (clazz != null)
            return clazz;

        synchronized (this) {
            clazz = this.moduleInfoClass;
            if (clazz == null) {
                if (isNamed()) {
                    PrivilegedAction<Class<?>> pa = this::loadModuleInfoClass;
                    clazz = AccessController.doPrivileged(pa);
                }
                if (clazz == null) {
                    class DummyModuleInfo { }
                    clazz = DummyModuleInfo.class;
                }
                this.moduleInfoClass = clazz;
            }
            return clazz;
        }
    }

    private Class<?> loadModuleInfoClass() {
        Class<?> clazz = null;
        try (InputStream in = getResourceAsStream("module-info.class")) {
            if (in != null)
                clazz = loadModuleInfoClass(in);
        } catch (Exception ignore) { }
        return clazz;
    }

    /**
     * Loads module-info.class as a package-private interface in a class loader
     * that is a child of this module's class loader.
     */
    private Class<?> loadModuleInfoClass(InputStream in) throws IOException {
        final String MODULE_INFO = "module-info";

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS
                                         + ClassWriter.COMPUTE_FRAMES);

        ClassVisitor cv = new ClassVisitor(Opcodes.ASM5, cw) {
            @Override
            public void visit(int version,
                              int access,
                              String name,
                              String signature,
                              String superName,
                              String[] interfaces) {
                cw.visit(version,
                        Opcodes.ACC_INTERFACE
                            + Opcodes.ACC_ABSTRACT
                            + Opcodes.ACC_SYNTHETIC,
                        MODULE_INFO,
                        null,
                        "java/lang/Object",
                        null);
            }
            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                // keep annotations
                return super.visitAnnotation(desc, visible);
            }
            @Override
            public void visitAttribute(Attribute attr) {
                // drop non-annotation attributes
            }
        };

        ClassReader cr = new ClassReader(in);
        cr.accept(cv, 0);
        byte[] bytes = cw.toByteArray();

        ClassLoader cl = new ClassLoader(loader) {
            @Override
            protected Class<?> findClass(String cn)throws ClassNotFoundException {
                if (cn.equals(MODULE_INFO)) {
                    return super.defineClass(cn, bytes, 0, bytes.length);
                } else {
                    throw new ClassNotFoundException(cn);
                }
            }
        };

        try {
            return cl.loadClass(MODULE_INFO);
        } catch (ClassNotFoundException e) {
            throw new InternalError(e);
        }
    }


    // -- misc --


    /**
     * Returns an input stream for reading a resource in this module.
     * The {@code name} parameter is a {@code '/'}-separated path name that
     * identifies the resource. As with {@link Class#getResourceAsStream
     * Class.getResourceAsStream}, this method delegates to the module's class
     * loader {@link ClassLoader#findResource(String,String)
     * findResource(String,String)} method, invoking it with the module name
     * (or {@code null} when the module is unnamed) and the name of the
     * resource. If the resource name has a leading slash then it is dropped
     * before delegation.
     *
     * <p> A resource in a named module may be <em>encapsulated</em> so that
     * it cannot be located by code in other modules. Whether a resource can be
     * located or not is determined as follows: </p>
     *
     * <ul>
     *     <li> If the resource name ends with  "{@code .class}" then it is not
     *     encapsulated. </li>
     *
     *     <li> A <em>package name</em> is derived from the resource name. If
     *     the package name is a {@linkplain #getPackages() package} in the
     *     module then the resource can only be located by the caller of this
     *     method when the package is {@linkplain #isOpen(String,Module) open}
     *     to at least the caller's module. If the resource is not in a
     *     package in the module then the resource is not encapsulated. </li>
     * </ul>
     *
     * <p> In the above, the <em>package name</em> for a resource is derived
     * from the subsequence of characters that precedes the last {@code '/'} in
     * the name and then replacing each {@code '/'} character in the subsequence
     * with {@code '.'}. A leading slash is ignored when deriving the package
     * name. As an example, the package name derived for a resource named
     * "{@code a/b/c/foo.properties}" is "{@code a.b.c}". A resource name
     * with the name "{@code META-INF/MANIFEST.MF}" is never encapsulated
     * because "{@code META-INF}" is not a legal package name. </p>
     *
     * <p> This method returns {@code null} if the resource is not in this
     * module, the resource is encapsulated and cannot be located by the caller,
     * or access to the resource is denied by the security manager. </p>
     *
     * @param  name
     *         The resource name
     *
     * @return An input stream for reading the resource or {@code null}
     *
     * @throws IOException
     *         If an I/O error occurs
     *
     * @see Class#getResourceAsStream(String)
     */
    @CallerSensitive
    public InputStream getResourceAsStream(String name) throws IOException {
        if (name.startsWith("/")) {
            name = name.substring(1);
        }

        if (isNamed() && Resources.canEncapsulate(name)) {
            Module caller = getCallerModule(Reflection.getCallerClass());
            if (caller != this && caller != Object.class.getModule()) {
                String pn = Resources.toPackageName(name);
                if (getPackages().contains(pn)) {
                    if (caller == null && !isOpen(pn)) {
                        // no caller, package not open
                        return null;
                    }
                    if (!isOpen(pn, caller)) {
                        // package not open to caller
                        return null;
                    }
                }
            }
        }

        String mn = this.name;

        // special-case built-in class loaders to avoid URL connection
        if (loader == null) {
            return BootLoader.findResourceAsStream(mn, name);
        } else if (loader instanceof BuiltinClassLoader) {
            return ((BuiltinClassLoader) loader).findResourceAsStream(mn, name);
        }

        // locate resource in module
        URL url = loader.findResource(mn, name);
        if (url != null) {
            try {
                return url.openStream();
            } catch (SecurityException e) { }
        }

        return null;
    }

    /**
     * Returns the string representation of this module. For a named module,
     * the representation is the string {@code "module"}, followed by a space,
     * and then the module name. For an unnamed module, the representation is
     * the string {@code "unnamed module"}, followed by a space, and then an
     * implementation specific string that identifies the unnamed module.
     *
     * @return The string representation of this module
     */
    @Override
    public String toString() {
        if (isNamed()) {
            return "module " + name;
        } else {
            String id = Integer.toHexString(System.identityHashCode(this));
            return "unnamed module @" + id;
        }
    }

    /**
     * Returns the module that a given caller class is a member of. Returns
     * {@code null} if the caller is {@code null}.
     */
    private Module getCallerModule(Class<?> caller) {
        return (caller != null) ? caller.getModule() : null;
    }


    // -- native methods --

    // JVM_DefineModule
    private static native void defineModule0(Module module,
                                             boolean isOpen,
                                             String version,
                                             String location,
                                             String[] pns);

    // JVM_AddReadsModule
    private static native void addReads0(Module from, Module to);

    // JVM_AddModuleExports
    private static native void addExports0(Module from, String pn, Module to);

    // JVM_AddModuleExportsToAll
    private static native void addExportsToAll0(Module from, String pn);

    // JVM_AddModuleExportsToAllUnnamed
    private static native void addExportsToAllUnnamed0(Module from, String pn);

    // JVM_AddModulePackage
    private static native void addPackage0(Module m, String pn);
}
