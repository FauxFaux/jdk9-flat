/*
 * Copyright (c) 2003, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.comp;

import java.util.HashSet;
import java.util.Set;

import javax.tools.JavaFileObject;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Lint.LintCategory;
import com.sun.tools.javac.code.Scope.ImportFilter;
import com.sun.tools.javac.code.Scope.NamedImportScope;
import com.sun.tools.javac.code.Scope.StarImportScope;
import com.sun.tools.javac.code.Scope.WriteableScope;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.DefinedBy.Api;

import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.tree.JCTree.*;

import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Flags.ANNOTATION;
import static com.sun.tools.javac.code.Scope.LookupKind.NON_RECURSIVE;
import static com.sun.tools.javac.code.Kinds.Kind.*;
import static com.sun.tools.javac.code.TypeTag.CLASS;
import static com.sun.tools.javac.code.TypeTag.ERROR;
import static com.sun.tools.javac.tree.JCTree.Tag.*;

import com.sun.tools.javac.util.Dependencies.AttributionKind;
import com.sun.tools.javac.util.Dependencies.CompletionCause;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticFlag;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;

/** This is the second phase of Enter, in which classes are completed
 *  by resolving their headers and entering their members in the into
 *  the class scope. See Enter for an overall overview.
 *
 *  This class uses internal phases to process the classes. When a phase
 *  processes classes, the lower phases are not invoked until all classes
 *  pass through the current phase. Note that it is possible that upper phases
 *  are run due to recursive completion. The internal phases are:
 *  - ImportPhase: shallow pass through imports, adds information about imports
 *                 the NamedImportScope and StarImportScope, but avoids queries
 *                 about class hierarchy.
 *  - HierarchyPhase: resolves the supertypes of the given class. Does not handle
 *                    type parameters of the class or type argument of the supertypes.
 *  - HeaderPhase: finishes analysis of the header of the given class by resolving
 *                 type parameters, attributing supertypes including type arguments
 *                 and scheduling full annotation attribution. This phase also adds
 *                 a synthetic default constructor if needed and synthetic "this" field.
 *  - MembersPhase: resolves headers for fields, methods and constructors in the given class.
 *                  Also generates synthetic enum members.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class TypeEnter implements Completer {
    protected static final Context.Key<TypeEnter> typeEnterKey = new Context.Key<>();

    /** A switch to determine whether we check for package/class conflicts
     */
    final static boolean checkClash = true;

    private final Names names;
    private final Enter enter;
    private final MemberEnter memberEnter;
    private final Log log;
    private final Check chk;
    private final Attr attr;
    private final Symtab syms;
    private final TreeMaker make;
    private final Todo todo;
    private final Annotate annotate;
    private final TypeAnnotations typeAnnotations;
    private final Types types;
    private final JCDiagnostic.Factory diags;
    private final Source source;
    private final DeferredLintHandler deferredLintHandler;
    private final Lint lint;
    private final TypeEnvs typeEnvs;
    private final Dependencies dependencies;

    public static TypeEnter instance(Context context) {
        TypeEnter instance = context.get(typeEnterKey);
        if (instance == null)
            instance = new TypeEnter(context);
        return instance;
    }

    protected TypeEnter(Context context) {
        context.put(typeEnterKey, this);
        names = Names.instance(context);
        enter = Enter.instance(context);
        memberEnter = MemberEnter.instance(context);
        log = Log.instance(context);
        chk = Check.instance(context);
        attr = Attr.instance(context);
        syms = Symtab.instance(context);
        make = TreeMaker.instance(context);
        todo = Todo.instance(context);
        annotate = Annotate.instance(context);
        typeAnnotations = TypeAnnotations.instance(context);
        types = Types.instance(context);
        diags = JCDiagnostic.Factory.instance(context);
        source = Source.instance(context);
        deferredLintHandler = DeferredLintHandler.instance(context);
        lint = Lint.instance(context);
        typeEnvs = TypeEnvs.instance(context);
        dependencies = Dependencies.instance(context);
        allowTypeAnnos = source.allowTypeAnnotations();
        allowDeprecationOnImport = source.allowDeprecationOnImport();
    }

    /** Switch: support type annotations.
     */
    boolean allowTypeAnnos;

    /**
     * Switch: should deprecation warnings be issued on import
     */
    boolean allowDeprecationOnImport;

    /** A flag to disable completion from time to time during member
     *  enter, as we only need to look up types.  This avoids
     *  unnecessarily deep recursion.
     */
    boolean completionEnabled = true;

    /* Verify Imports:
     */
    protected void ensureImportsChecked(List<JCCompilationUnit> trees) {
        // if there remain any unimported toplevels (these must have
        // no classes at all), process their import statements as well.
        for (JCCompilationUnit tree : trees) {
            if (tree.starImportScope.isEmpty()) {
                Env<AttrContext> topEnv = enter.topLevelEnv(tree);
                finishImports(tree, () -> { completeClass.resolveImports(tree, topEnv); });
            }
       }
    }

/* ********************************************************************
 * Source completer
 *********************************************************************/

    /** Complete entering a class.
     *  @param sym         The symbol of the class to be completed.
     */
    public void complete(Symbol sym) throws CompletionFailure {
        // Suppress some (recursive) MemberEnter invocations
        if (!completionEnabled) {
            // Re-install same completer for next time around and return.
            Assert.check((sym.flags() & Flags.COMPOUND) == 0);
            sym.completer = this;
            return;
        }

        try {
            annotate.enterStart();
            sym.flags_field |= UNATTRIBUTED;

            List<Env<AttrContext>> queue;

            dependencies.push((ClassSymbol) sym, CompletionCause.MEMBER_ENTER);
            try {
                queue = completeClass.runPhase(List.of(typeEnvs.get((ClassSymbol) sym)));
            } finally {
                dependencies.pop();
            }

            if (!queue.isEmpty()) {
                Set<JCCompilationUnit> seen = new HashSet<>();

                for (Env<AttrContext> env : queue) {
                    if (env.toplevel.defs.contains(env.enclClass) && seen.add(env.toplevel)) {
                        finishImports(env.toplevel, () -> {});
                    }
                }
            }
        } finally {
            annotate.enterDone();
        }
    }

    void finishImports(JCCompilationUnit toplevel, Runnable resolve) {
        JavaFileObject prev = log.useSource(toplevel.sourcefile);
        try {
            resolve.run();
            chk.checkImportsUnique(toplevel);
            chk.checkImportsResolvable(toplevel);
            toplevel.namedImportScope.finalizeScope();
            toplevel.starImportScope.finalizeScope();
        } finally {
            log.useSource(prev);
        }
    }

    abstract class Phase {
        private final ListBuffer<Env<AttrContext>> queue = new ListBuffer<>();
        private final Phase next;
        private final CompletionCause phaseName;

        Phase(CompletionCause phaseName, Phase next) {
            this.phaseName = phaseName;
            this.next = next;
        }

        public List<Env<AttrContext>> runPhase(List<Env<AttrContext>> envs) {
            boolean firstToComplete = queue.isEmpty();

            for (Env<AttrContext> env : envs) {
                JCClassDecl tree = (JCClassDecl)env.tree;

                queue.add(env);

                JavaFileObject prev = log.useSource(env.toplevel.sourcefile);
                DiagnosticPosition prevLintPos = deferredLintHandler.setPos(tree.pos());
                try {
                    dependencies.push(env.enclClass.sym, phaseName);
                    doRunPhase(env);
                } catch (CompletionFailure ex) {
                    chk.completionError(tree.pos(), ex);
                } finally {
                    dependencies.pop();
                    deferredLintHandler.setPos(prevLintPos);
                    log.useSource(prev);
                }
            }

            if (firstToComplete) {
                List<Env<AttrContext>> out = queue.toList();

                queue.clear();
                return next != null ? next.runPhase(out) : out;
            } else {
                return List.nil();
            }
       }

        protected abstract void doRunPhase(Env<AttrContext> env);
    }

    private final ImportsPhase completeClass = new ImportsPhase();

    /**Analyze import clauses.
     */
    private final class ImportsPhase extends Phase {

        public ImportsPhase() {
            super(CompletionCause.IMPORTS_PHASE, new HierarchyPhase());
        }

        Env<AttrContext> env;
        ImportFilter staticImportFilter;
        ImportFilter typeImportFilter = new ImportFilter() {
            @Override
            public boolean accepts(Scope origin, Symbol t) {
                return t.kind == TYP;
            }
        };

        @Override
        protected void doRunPhase(Env<AttrContext> env) {
            JCClassDecl tree = env.enclClass;
            ClassSymbol sym = tree.sym;

            // If sym is a toplevel-class, make sure any import
            // clauses in its source file have been seen.
            if (sym.owner.kind == PCK) {
                resolveImports(env.toplevel, env.enclosing(TOPLEVEL));
                todo.append(env);
            }

            if (sym.owner.kind == TYP)
                sym.owner.complete();
        }

        private void resolveImports(JCCompilationUnit tree, Env<AttrContext> env) {
            if (!tree.starImportScope.isEmpty()) {
                // we must have already processed this toplevel
                return;
            }

            ImportFilter prevStaticImportFilter = staticImportFilter;
            DiagnosticPosition prevLintPos = deferredLintHandler.immediate();
            Lint prevLint = chk.setLint(lint);
            Env<AttrContext> prevEnv = this.env;
            try {
                this.env = env;
                final PackageSymbol packge = env.toplevel.packge;
                this.staticImportFilter = new ImportFilter() {
                    @Override
                    public boolean accepts(Scope origin, Symbol sym) {
                        return sym.isStatic() &&
                               chk.staticImportAccessible(sym, packge) &&
                               sym.isMemberOf((TypeSymbol) origin.owner, types);
                    }
                };

                // Import-on-demand java.lang.
                importAll(tree.pos, syms.enterPackage(names.java_lang), env);

                // Process the package def and all import clauses.
                if (tree.getPackage() != null)
                    checkClassPackageClash(tree.getPackage());

                for (JCImport imp : tree.getImports()) {
                    doImport(imp);
                }
            } finally {
                this.env = prevEnv;
                chk.setLint(prevLint);
                deferredLintHandler.setPos(prevLintPos);
                this.staticImportFilter = prevStaticImportFilter;
            }
        }

        private void checkClassPackageClash(JCPackageDecl tree) {
            // check that no class exists with same fully qualified name as
            // toplevel package
            if (checkClash && tree.pid != null) {
                Symbol p = env.toplevel.packge;
                while (p.owner != syms.rootPackage) {
                    p.owner.complete(); // enter all class members of p
                    if (syms.classes.get(p.getQualifiedName()) != null) {
                        log.error(tree.pos,
                                  "pkg.clashes.with.class.of.same.name",
                                  p);
                    }
                    p = p.owner;
                }
            }
            // process package annotations
            annotate.annotateLater(tree.annotations, env, env.toplevel.packge, null);
        }

        private void doImport(JCImport tree) {
            dependencies.push(AttributionKind.IMPORT, tree);
            JCFieldAccess imp = (JCFieldAccess)tree.qualid;
            Name name = TreeInfo.name(imp);

            // Create a local environment pointing to this tree to disable
            // effects of other imports in Resolve.findGlobalType
            Env<AttrContext> localEnv = env.dup(tree);

            TypeSymbol p = attr.attribImportQualifier(tree, localEnv).tsym;
            if (name == names.asterisk) {
                // Import on demand.
                chk.checkCanonical(imp.selected);
                if (tree.staticImport)
                    importStaticAll(tree.pos, p, env);
                else
                    importAll(tree.pos, p, env);
            } else {
                // Named type import.
                if (tree.staticImport) {
                    importNamedStatic(tree.pos(), p, name, localEnv, tree);
                    chk.checkCanonical(imp.selected);
                } else {
                    TypeSymbol c = attribImportType(imp, localEnv).tsym;
                    chk.checkCanonical(imp);
                    importNamed(tree.pos(), c, env, tree);
                }
            }
            dependencies.pop();
        }

        Type attribImportType(JCTree tree, Env<AttrContext> env) {
            Assert.check(completionEnabled);
            Lint prevLint = chk.setLint(allowDeprecationOnImport ?
                    lint : lint.suppress(LintCategory.DEPRECATION));
            try {
                // To prevent deep recursion, suppress completion of some
                // types.
                completionEnabled = false;
                return attr.attribType(tree, env);
            } finally {
                completionEnabled = true;
                chk.setLint(prevLint);
            }
        }

        /** Import all classes of a class or package on demand.
         *  @param pos           Position to be used for error reporting.
         *  @param tsym          The class or package the members of which are imported.
         *  @param env           The env in which the imported classes will be entered.
         */
        private void importAll(int pos,
                               final TypeSymbol tsym,
                               Env<AttrContext> env) {
            // Check that packages imported from exist (JLS ???).
            if (tsym.kind == PCK && tsym.members().isEmpty() && !tsym.exists()) {
                // If we can't find java.lang, exit immediately.
                if (((PackageSymbol)tsym).fullname.equals(names.java_lang)) {
                    JCDiagnostic msg = diags.fragment("fatal.err.no.java.lang");
                    throw new FatalError(msg);
                } else {
                    log.error(DiagnosticFlag.RESOLVE_ERROR, pos, "doesnt.exist", tsym);
                }
            }
            env.toplevel.starImportScope.importAll(types, tsym.members(), typeImportFilter, false);
        }

        /** Import all static members of a class or package on demand.
         *  @param pos           Position to be used for error reporting.
         *  @param tsym          The class or package the members of which are imported.
         *  @param env           The env in which the imported classes will be entered.
         */
        private void importStaticAll(int pos,
                                     final TypeSymbol tsym,
                                     Env<AttrContext> env) {
            final StarImportScope toScope = env.toplevel.starImportScope;
            final TypeSymbol origin = tsym;

            toScope.importAll(types, origin.members(), staticImportFilter, true);
        }

        /** Import statics types of a given name.  Non-types are handled in Attr.
         *  @param pos           Position to be used for error reporting.
         *  @param tsym          The class from which the name is imported.
         *  @param name          The (simple) name being imported.
         *  @param env           The environment containing the named import
         *                  scope to add to.
         */
        private void importNamedStatic(final DiagnosticPosition pos,
                                       final TypeSymbol tsym,
                                       final Name name,
                                       final Env<AttrContext> env,
                                       final JCImport imp) {
            if (tsym.kind != TYP) {
                log.error(DiagnosticFlag.RECOVERABLE, pos, "static.imp.only.classes.and.interfaces");
                return;
            }

            final NamedImportScope toScope = env.toplevel.namedImportScope;
            final Scope originMembers = tsym.members();

            imp.importScope = toScope.importByName(types, originMembers, name, staticImportFilter);
        }

        /** Import given class.
         *  @param pos           Position to be used for error reporting.
         *  @param tsym          The class to be imported.
         *  @param env           The environment containing the named import
         *                  scope to add to.
         */
        private void importNamed(DiagnosticPosition pos, final Symbol tsym, Env<AttrContext> env, JCImport imp) {
            if (tsym.kind == TYP)
                imp.importScope = env.toplevel.namedImportScope.importType(tsym.owner.members(), tsym.owner.members(), tsym);
        }

    }

    /**Defines common utility methods used by the HierarchyPhase and HeaderPhase.
     */
    private abstract class AbstractHeaderPhase extends Phase {

        public AbstractHeaderPhase(CompletionCause phaseName, Phase next) {
            super(phaseName, next);
        }

        protected Env<AttrContext> baseEnv(JCClassDecl tree, Env<AttrContext> env) {
            WriteableScope baseScope = WriteableScope.create(tree.sym);
            //import already entered local classes into base scope
            for (Symbol sym : env.outer.info.scope.getSymbols(NON_RECURSIVE)) {
                if (sym.isLocal()) {
                    baseScope.enter(sym);
                }
            }
            //import current type-parameters into base scope
            if (tree.typarams != null)
                for (List<JCTypeParameter> typarams = tree.typarams;
                     typarams.nonEmpty();
                     typarams = typarams.tail)
                    baseScope.enter(typarams.head.type.tsym);
            Env<AttrContext> outer = env.outer; // the base clause can't see members of this class
            Env<AttrContext> localEnv = outer.dup(tree, outer.info.dup(baseScope));
            localEnv.baseClause = true;
            localEnv.outer = outer;
            localEnv.info.isSelfCall = false;
            return localEnv;
        }

        /** Generate a base clause for an enum type.
         *  @param pos              The position for trees and diagnostics, if any
         *  @param c                The class symbol of the enum
         */
        protected  JCExpression enumBase(int pos, ClassSymbol c) {
            JCExpression result = make.at(pos).
                TypeApply(make.QualIdent(syms.enumSym),
                          List.<JCExpression>of(make.Type(c.type)));
            return result;
        }

        protected Type modelMissingTypes(Type t, final JCExpression tree, final boolean interfaceExpected) {
            if (!t.hasTag(ERROR))
                return t;

            return new ErrorType(t.getOriginalType(), t.tsym) {
                private Type modelType;

                @Override
                public Type getModelType() {
                    if (modelType == null)
                        modelType = new Synthesizer(getOriginalType(), interfaceExpected).visit(tree);
                    return modelType;
                }
            };
        }
            // where:
            private class Synthesizer extends JCTree.Visitor {
                Type originalType;
                boolean interfaceExpected;
                List<ClassSymbol> synthesizedSymbols = List.nil();
                Type result;

                Synthesizer(Type originalType, boolean interfaceExpected) {
                    this.originalType = originalType;
                    this.interfaceExpected = interfaceExpected;
                }

                Type visit(JCTree tree) {
                    tree.accept(this);
                    return result;
                }

                List<Type> visit(List<? extends JCTree> trees) {
                    ListBuffer<Type> lb = new ListBuffer<>();
                    for (JCTree t: trees)
                        lb.append(visit(t));
                    return lb.toList();
                }

                @Override
                public void visitTree(JCTree tree) {
                    result = syms.errType;
                }

                @Override
                public void visitIdent(JCIdent tree) {
                    if (!tree.type.hasTag(ERROR)) {
                        result = tree.type;
                    } else {
                        result = synthesizeClass(tree.name, syms.unnamedPackage).type;
                    }
                }

                @Override
                public void visitSelect(JCFieldAccess tree) {
                    if (!tree.type.hasTag(ERROR)) {
                        result = tree.type;
                    } else {
                        Type selectedType;
                        boolean prev = interfaceExpected;
                        try {
                            interfaceExpected = false;
                            selectedType = visit(tree.selected);
                        } finally {
                            interfaceExpected = prev;
                        }
                        ClassSymbol c = synthesizeClass(tree.name, selectedType.tsym);
                        result = c.type;
                    }
                }

                @Override
                public void visitTypeApply(JCTypeApply tree) {
                    if (!tree.type.hasTag(ERROR)) {
                        result = tree.type;
                    } else {
                        ClassType clazzType = (ClassType) visit(tree.clazz);
                        if (synthesizedSymbols.contains(clazzType.tsym))
                            synthesizeTyparams((ClassSymbol) clazzType.tsym, tree.arguments.size());
                        final List<Type> actuals = visit(tree.arguments);
                        result = new ErrorType(tree.type, clazzType.tsym) {
                            @Override @DefinedBy(Api.LANGUAGE_MODEL)
                            public List<Type> getTypeArguments() {
                                return actuals;
                            }
                        };
                    }
                }

                ClassSymbol synthesizeClass(Name name, Symbol owner) {
                    int flags = interfaceExpected ? INTERFACE : 0;
                    ClassSymbol c = new ClassSymbol(flags, name, owner);
                    c.members_field = new Scope.ErrorScope(c);
                    c.type = new ErrorType(originalType, c) {
                        @Override @DefinedBy(Api.LANGUAGE_MODEL)
                        public List<Type> getTypeArguments() {
                            return typarams_field;
                        }
                    };
                    synthesizedSymbols = synthesizedSymbols.prepend(c);
                    return c;
                }

                void synthesizeTyparams(ClassSymbol sym, int n) {
                    ClassType ct = (ClassType) sym.type;
                    Assert.check(ct.typarams_field.isEmpty());
                    if (n == 1) {
                        TypeVar v = new TypeVar(names.fromString("T"), sym, syms.botType);
                        ct.typarams_field = ct.typarams_field.prepend(v);
                    } else {
                        for (int i = n; i > 0; i--) {
                            TypeVar v = new TypeVar(names.fromString("T" + i), sym,
                                                    syms.botType);
                            ct.typarams_field = ct.typarams_field.prepend(v);
                        }
                    }
                }
            }

        protected void attribSuperTypes(Env<AttrContext> env, Env<AttrContext> baseEnv) {
            JCClassDecl tree = env.enclClass;
            ClassSymbol sym = tree.sym;
            ClassType ct = (ClassType)sym.type;
            // Determine supertype.
            Type supertype;
            JCExpression extending;

            if (tree.extending != null) {
                extending = clearTypeParams(tree.extending);
                dependencies.push(AttributionKind.EXTENDS, tree.extending);
                try {
                    supertype = attr.attribBase(extending, baseEnv,
                                                true, false, true);
                } finally {
                    dependencies.pop();
                }
            } else {
                extending = null;
                supertype = ((tree.mods.flags & Flags.ENUM) != 0)
                ? attr.attribBase(enumBase(tree.pos, sym), baseEnv,
                                  true, false, false)
                : (sym.fullname == names.java_lang_Object)
                ? Type.noType
                : syms.objectType;
            }
            ct.supertype_field = modelMissingTypes(supertype, extending, false);

            // Determine interfaces.
            ListBuffer<Type> interfaces = new ListBuffer<>();
            ListBuffer<Type> all_interfaces = null; // lazy init
            List<JCExpression> interfaceTrees = tree.implementing;
            for (JCExpression iface : interfaceTrees) {
                iface = clearTypeParams(iface);
                dependencies.push(AttributionKind.IMPLEMENTS, iface);
                try {
                    Type it = attr.attribBase(iface, baseEnv, false, true, true);
                    if (it.hasTag(CLASS)) {
                        interfaces.append(it);
                        if (all_interfaces != null) all_interfaces.append(it);
                    } else {
                        if (all_interfaces == null)
                            all_interfaces = new ListBuffer<Type>().appendList(interfaces);
                        all_interfaces.append(modelMissingTypes(it, iface, true));

                    }
                } finally {
                    dependencies.pop();
                }
            }

            if ((sym.flags_field & ANNOTATION) != 0) {
                ct.interfaces_field = List.of(syms.annotationType);
                ct.all_interfaces_field = ct.interfaces_field;
            }  else {
                ct.interfaces_field = interfaces.toList();
                ct.all_interfaces_field = (all_interfaces == null)
                        ? ct.interfaces_field : all_interfaces.toList();
            }
        }
            //where:
            protected JCExpression clearTypeParams(JCExpression superType) {
                return superType;
            }
    }

    private final class HierarchyPhase extends AbstractHeaderPhase {

        public HierarchyPhase() {
            super(CompletionCause.HIERARCHY_PHASE, new HeaderPhase());
        }

        @Override
        protected void doRunPhase(Env<AttrContext> env) {
            JCClassDecl tree = env.enclClass;
            ClassSymbol sym = tree.sym;
            ClassType ct = (ClassType)sym.type;

            Env<AttrContext> baseEnv = baseEnv(tree, env);

            attribSuperTypes(env, baseEnv);

            if (sym.fullname == names.java_lang_Object) {
                if (tree.extending != null) {
                    chk.checkNonCyclic(tree.extending.pos(),
                                       ct.supertype_field);
                    ct.supertype_field = Type.noType;
                }
                else if (tree.implementing.nonEmpty()) {
                    chk.checkNonCyclic(tree.implementing.head.pos(),
                                       ct.interfaces_field.head);
                    ct.interfaces_field = List.nil();
                }
            }

            // Annotations.
            // In general, we cannot fully process annotations yet,  but we
            // can attribute the annotation types and then check to see if the
            // @Deprecated annotation is present.
            attr.attribAnnotationTypes(tree.mods.annotations, baseEnv);
            if (hasDeprecatedAnnotation(tree.mods.annotations))
                sym.flags_field |= DEPRECATED;

            chk.checkNonCyclicDecl(tree);
        }
            //where:
            protected JCExpression clearTypeParams(JCExpression superType) {
                switch (superType.getTag()) {
                    case TYPEAPPLY:
                        return ((JCTypeApply) superType).clazz;
                }

                return superType;
            }

            /**
             * Check if a list of annotations contains a reference to
             * java.lang.Deprecated.
             **/
            private boolean hasDeprecatedAnnotation(List<JCAnnotation> annotations) {
                for (List<JCAnnotation> al = annotations; !al.isEmpty(); al = al.tail) {
                    JCAnnotation a = al.head;
                    if (a.annotationType.type == syms.deprecatedType && a.args.isEmpty())
                        return true;
                }
                return false;
            }
    }

    private final class HeaderPhase extends AbstractHeaderPhase {

        public HeaderPhase() {
            super(CompletionCause.HEADER_PHASE, new MembersPhase());
        }

        @Override
        protected void doRunPhase(Env<AttrContext> env) {
            JCClassDecl tree = env.enclClass;
            ClassSymbol sym = tree.sym;
            ClassType ct = (ClassType)sym.type;

            // create an environment for evaluating the base clauses
            Env<AttrContext> baseEnv = baseEnv(tree, env);

            if (tree.extending != null)
                annotate.annotateTypeLater(tree.extending, baseEnv, sym, tree.pos());
            for (JCExpression impl : tree.implementing)
                annotate.annotateTypeLater(impl, baseEnv, sym, tree.pos());
            annotate.flush();

            attribSuperTypes(env, baseEnv);

            Set<Type> interfaceSet = new HashSet<>();

            for (JCExpression iface : tree.implementing) {
                Type it = iface.type;
                if (it.hasTag(CLASS))
                    chk.checkNotRepeated(iface.pos(), types.erasure(it), interfaceSet);
            }

            annotate.annotateLater(tree.mods.annotations, baseEnv,
                        sym, tree.pos());

            attr.attribTypeVariables(tree.typarams, baseEnv);
            for (JCTypeParameter tp : tree.typarams)
                annotate.annotateTypeLater(tp, baseEnv, sym, tree.pos());

            // check that no package exists with same fully qualified name,
            // but admit classes in the unnamed package which have the same
            // name as a top-level package.
            if (checkClash &&
                sym.owner.kind == PCK && sym.owner != syms.unnamedPackage &&
                syms.packageExists(sym.fullname)) {
                log.error(tree.pos, "clash.with.pkg.of.same.name", Kinds.kindName(sym), sym);
            }
            if (sym.owner.kind == PCK && (sym.flags_field & PUBLIC) == 0 &&
                !env.toplevel.sourcefile.isNameCompatible(sym.name.toString(),JavaFileObject.Kind.SOURCE)) {
                sym.flags_field |= AUXILIARY;
            }
        }
    }

    /** Enter member fields and methods of a class
     */
    private final class MembersPhase extends Phase {

        public MembersPhase() {
            super(CompletionCause.MEMBERS_PHASE, null);
        }

        @Override
        protected void doRunPhase(Env<AttrContext> env) {
            JCClassDecl tree = env.enclClass;
            ClassSymbol sym = tree.sym;
            ClassType ct = (ClassType)sym.type;

            // Add default constructor if needed.
            if ((sym.flags() & INTERFACE) == 0 &&
                !TreeInfo.hasConstructors(tree.defs)) {
                List<Type> argtypes = List.nil();
                List<Type> typarams = List.nil();
                List<Type> thrown = List.nil();
                long ctorFlags = 0;
                boolean based = false;
                boolean addConstructor = true;
                JCNewClass nc = null;
                if (sym.name.isEmpty()) {
                    nc = (JCNewClass)env.next.tree;
                    if (nc.constructor != null) {
                        addConstructor = nc.constructor.kind != ERR;
                        Type superConstrType = types.memberType(sym.type,
                                                                nc.constructor);
                        argtypes = superConstrType.getParameterTypes();
                        typarams = superConstrType.getTypeArguments();
                        ctorFlags = nc.constructor.flags() & VARARGS;
                        if (nc.encl != null) {
                            argtypes = argtypes.prepend(nc.encl.type);
                            based = true;
                        }
                        thrown = superConstrType.getThrownTypes();
                    }
                }
                if (addConstructor) {
                    MethodSymbol basedConstructor = nc != null ?
                            (MethodSymbol)nc.constructor : null;
                    JCTree constrDef = DefaultConstructor(make.at(tree.pos), sym,
                                                        basedConstructor,
                                                        typarams, argtypes, thrown,
                                                        ctorFlags, based);
                    tree.defs = tree.defs.prepend(constrDef);
                }
            }

            // enter symbols for 'this' into current scope.
            VarSymbol thisSym =
                new VarSymbol(FINAL | HASINIT, names._this, sym.type, sym);
            thisSym.pos = Position.FIRSTPOS;
            env.info.scope.enter(thisSym);
            // if this is a class, enter symbol for 'super' into current scope.
            if ((sym.flags_field & INTERFACE) == 0 &&
                    ct.supertype_field.hasTag(CLASS)) {
                VarSymbol superSym =
                    new VarSymbol(FINAL | HASINIT, names._super,
                                  ct.supertype_field, sym);
                superSym.pos = Position.FIRSTPOS;
                env.info.scope.enter(superSym);
            }

            finishClass(tree, env);

            if (allowTypeAnnos) {
                typeAnnotations.organizeTypeAnnotationsSignatures(env, (JCClassDecl)env.tree);
                typeAnnotations.validateTypeAnnotationsSignatures(env, (JCClassDecl)env.tree);
            }
        }

        /** Enter members for a class.
         */
        void finishClass(JCClassDecl tree, Env<AttrContext> env) {
            if ((tree.mods.flags & Flags.ENUM) != 0 &&
                (types.supertype(tree.sym.type).tsym.flags() & Flags.ENUM) == 0) {
                addEnumMembers(tree, env);
            }
            memberEnter.memberEnter(tree.defs, env);
        }

        /** Add the implicit members for an enum type
         *  to the symbol table.
         */
        private void addEnumMembers(JCClassDecl tree, Env<AttrContext> env) {
            JCExpression valuesType = make.Type(new ArrayType(tree.sym.type, syms.arrayClass));

            // public static T[] values() { return ???; }
            JCMethodDecl values = make.
                MethodDef(make.Modifiers(Flags.PUBLIC|Flags.STATIC),
                          names.values,
                          valuesType,
                          List.<JCTypeParameter>nil(),
                          List.<JCVariableDecl>nil(),
                          List.<JCExpression>nil(), // thrown
                          null, //make.Block(0, Tree.emptyList.prepend(make.Return(make.Ident(names._null)))),
                          null);
            memberEnter.memberEnter(values, env);

            // public static T valueOf(String name) { return ???; }
            JCMethodDecl valueOf = make.
                MethodDef(make.Modifiers(Flags.PUBLIC|Flags.STATIC),
                          names.valueOf,
                          make.Type(tree.sym.type),
                          List.<JCTypeParameter>nil(),
                          List.of(make.VarDef(make.Modifiers(Flags.PARAMETER |
                                                             Flags.MANDATED),
                                                names.fromString("name"),
                                                make.Type(syms.stringType), null)),
                          List.<JCExpression>nil(), // thrown
                          null, //make.Block(0, Tree.emptyList.prepend(make.Return(make.Ident(names._null)))),
                          null);
            memberEnter.memberEnter(valueOf, env);
        }

    }

/* ***************************************************************************
 * tree building
 ****************************************************************************/

    /** Generate default constructor for given class. For classes different
     *  from java.lang.Object, this is:
     *
     *    c(argtype_0 x_0, ..., argtype_n x_n) throws thrown {
     *      super(x_0, ..., x_n)
     *    }
     *
     *  or, if based == true:
     *
     *    c(argtype_0 x_0, ..., argtype_n x_n) throws thrown {
     *      x_0.super(x_1, ..., x_n)
     *    }
     *
     *  @param make     The tree factory.
     *  @param c        The class owning the default constructor.
     *  @param argtypes The parameter types of the constructor.
     *  @param thrown   The thrown exceptions of the constructor.
     *  @param based    Is first parameter a this$n?
     */
    JCTree DefaultConstructor(TreeMaker make,
                            ClassSymbol c,
                            MethodSymbol baseInit,
                            List<Type> typarams,
                            List<Type> argtypes,
                            List<Type> thrown,
                            long flags,
                            boolean based) {
        JCTree result;
        if ((c.flags() & ENUM) != 0 &&
            (types.supertype(c.type).tsym == syms.enumSym)) {
            // constructors of true enums are private
            flags = (flags & ~AccessFlags) | PRIVATE | GENERATEDCONSTR;
        } else
            flags |= (c.flags() & AccessFlags) | GENERATEDCONSTR;
        if (c.name.isEmpty()) {
            flags |= ANONCONSTR;
        }
        Type mType = new MethodType(argtypes, null, thrown, c);
        Type initType = typarams.nonEmpty() ?
            new ForAll(typarams, mType) :
            mType;
        MethodSymbol init = new MethodSymbol(flags, names.init,
                initType, c);
        init.params = createDefaultConstructorParams(make, baseInit, init,
                argtypes, based);
        List<JCVariableDecl> params = make.Params(argtypes, init);
        List<JCStatement> stats = List.nil();
        if (c.type != syms.objectType) {
            stats = stats.prepend(SuperCall(make, typarams, params, based));
        }
        result = make.MethodDef(init, make.Block(0, stats));
        return result;
    }

    private List<VarSymbol> createDefaultConstructorParams(
            TreeMaker make,
            MethodSymbol baseInit,
            MethodSymbol init,
            List<Type> argtypes,
            boolean based) {
        List<VarSymbol> initParams = null;
        List<Type> argTypesList = argtypes;
        if (based) {
            /*  In this case argtypes will have an extra type, compared to baseInit,
             *  corresponding to the type of the enclosing instance i.e.:
             *
             *  Inner i = outer.new Inner(1){}
             *
             *  in the above example argtypes will be (Outer, int) and baseInit
             *  will have parameter's types (int). So in this case we have to add
             *  first the extra type in argtypes and then get the names of the
             *  parameters from baseInit.
             */
            initParams = List.nil();
            VarSymbol param = new VarSymbol(PARAMETER, make.paramName(0), argtypes.head, init);
            initParams = initParams.append(param);
            argTypesList = argTypesList.tail;
        }
        if (baseInit != null && baseInit.params != null &&
            baseInit.params.nonEmpty() && argTypesList.nonEmpty()) {
            initParams = (initParams == null) ? List.<VarSymbol>nil() : initParams;
            List<VarSymbol> baseInitParams = baseInit.params;
            while (baseInitParams.nonEmpty() && argTypesList.nonEmpty()) {
                VarSymbol param = new VarSymbol(baseInitParams.head.flags() | PARAMETER,
                        baseInitParams.head.name, argTypesList.head, init);
                initParams = initParams.append(param);
                baseInitParams = baseInitParams.tail;
                argTypesList = argTypesList.tail;
            }
        }
        return initParams;
    }

    /** Generate call to superclass constructor. This is:
     *
     *    super(id_0, ..., id_n)
     *
     * or, if based == true
     *
     *    id_0.super(id_1,...,id_n)
     *
     *  where id_0, ..., id_n are the names of the given parameters.
     *
     *  @param make    The tree factory
     *  @param params  The parameters that need to be passed to super
     *  @param typarams  The type parameters that need to be passed to super
     *  @param based   Is first parameter a this$n?
     */
    JCExpressionStatement SuperCall(TreeMaker make,
                   List<Type> typarams,
                   List<JCVariableDecl> params,
                   boolean based) {
        JCExpression meth;
        if (based) {
            meth = make.Select(make.Ident(params.head), names._super);
            params = params.tail;
        } else {
            meth = make.Ident(names._super);
        }
        List<JCExpression> typeargs = typarams.nonEmpty() ? make.Types(typarams) : null;
        return make.Exec(make.Apply(typeargs, meth, make.Idents(params)));
    }
}
