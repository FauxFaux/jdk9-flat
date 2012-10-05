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

package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.comp.Attr.ResultInfo;
import com.sun.tools.javac.comp.Infer.InferenceContext;
import com.sun.tools.javac.comp.Resolve.MethodResolutionPhase;
import com.sun.tools.javac.tree.JCTree.*;

import javax.tools.JavaFileObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;

import static com.sun.tools.javac.code.TypeTags.*;
import static com.sun.tools.javac.tree.JCTree.Tag.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;

/**
 * This is an helper class that is used to perform deferred type-analysis.
 * Each time a poly expression occurs in argument position, javac attributes it
 * with a temporary 'deferred type' that is checked (possibly multiple times)
 * against an expected formal type.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class DeferredAttr extends JCTree.Visitor {
    protected static final Context.Key<DeferredAttr> deferredAttrKey =
        new Context.Key<DeferredAttr>();

    final Attr attr;
    final Check chk;
    final Enter enter;
    final Infer infer;
    final Log log;
    final Symtab syms;
    final TreeMaker make;
    final Types types;

    public static DeferredAttr instance(Context context) {
        DeferredAttr instance = context.get(deferredAttrKey);
        if (instance == null)
            instance = new DeferredAttr(context);
        return instance;
    }

    protected DeferredAttr(Context context) {
        context.put(deferredAttrKey, this);
        attr = Attr.instance(context);
        chk = Check.instance(context);
        enter = Enter.instance(context);
        infer = Infer.instance(context);
        log = Log.instance(context);
        syms = Symtab.instance(context);
        make = TreeMaker.instance(context);
        types = Types.instance(context);
    }

    /**
     * This type represents a deferred type. A deferred type starts off with
     * no information on the underlying expression type. Such info needs to be
     * discovered through type-checking the deferred type against a target-type.
     * Every deferred type keeps a pointer to the AST node from which it originated.
     */
    public class DeferredType extends Type {

        public JCExpression tree;
        Env<AttrContext> env;
        AttrMode mode;
        SpeculativeCache speculativeCache;

        DeferredType(JCExpression tree, Env<AttrContext> env) {
            super(DEFERRED, null);
            this.tree = tree;
            this.env = env.dup(tree, env.info.dup());
            this.speculativeCache = new SpeculativeCache();
        }

        /**
         * A speculative cache is used to keep track of all overload resolution rounds
         * that triggered speculative attribution on a given deferred type. Each entry
         * stores a pointer to the speculative tree and the resolution phase in which the entry
         * has been added.
         */
        class SpeculativeCache {

            private Map<Symbol, List<Entry>> cache =
                    new WeakHashMap<Symbol, List<Entry>>();

            class Entry {
                JCTree speculativeTree;
                Resolve.MethodResolutionPhase phase;

                public Entry(JCTree speculativeTree, MethodResolutionPhase phase) {
                    this.speculativeTree = speculativeTree;
                    this.phase = phase;
                }

                boolean matches(Resolve.MethodResolutionPhase phase) {
                    return this.phase == phase;
                }
            }

            /**
             * Clone a speculative cache entry as a fresh entry associated
             * with a new method (this maybe required to fixup speculative cache
             * misses after Resolve.access())
             */
            void dupAllTo(Symbol from, Symbol to) {
                Assert.check(cache.get(to) == null);
                List<Entry> entries = cache.get(from);
                if (entries != null) {
                    cache.put(to, entries);
                }
            }

            /**
             * Retrieve a speculative cache entry corresponding to given symbol
             * and resolution phase
             */
            Entry get(Symbol msym, MethodResolutionPhase phase) {
                List<Entry> entries = cache.get(msym);
                if (entries == null) return null;
                for (Entry e : entries) {
                    if (e.matches(phase)) return e;
                }
                return null;
            }

            /**
             * Stores a speculative cache entry corresponding to given symbol
             * and resolution phase
             */
            void put(Symbol msym, JCTree speculativeTree, MethodResolutionPhase phase) {
                List<Entry> entries = cache.get(msym);
                if (entries == null) {
                    entries = List.nil();
                }
                cache.put(msym, entries.prepend(new Entry(speculativeTree, phase)));
            }
        }

        /**
         * Get the type that has been computed during a speculative attribution round
         */
        Type speculativeType(Symbol msym, MethodResolutionPhase phase) {
            SpeculativeCache.Entry e = speculativeCache.get(msym, phase);
            return e != null ? e.speculativeTree.type : Type.noType;
        }

        /**
         * Check a deferred type against a potential target-type. Depending on
         * the current attribution mode, a normal vs. speculative attribution
         * round is performed on the underlying AST node. There can be only one
         * speculative round for a given target method symbol; moreover, a normal
         * attribution round must follow one or more speculative rounds.
         */
        Type check(ResultInfo resultInfo) {
            DeferredAttrContext deferredAttrContext =
                    resultInfo.checkContext.deferredAttrContext();
            Assert.check(deferredAttrContext != emptyDeferredAttrContext);
            List<Type> stuckVars = stuckVars(tree, resultInfo);
            if (stuckVars.nonEmpty()) {
                deferredAttrContext.addDeferredAttrNode(this, resultInfo, stuckVars);
                return Type.noType;
            } else {
                try {
                    switch (deferredAttrContext.mode) {
                        case SPECULATIVE:
                            Assert.check(mode == null ||
                                    (mode == AttrMode.SPECULATIVE &&
                                    speculativeType(deferredAttrContext.msym, deferredAttrContext.phase).tag == NONE));
                            JCTree speculativeTree = attribSpeculative(tree, env, resultInfo);
                            speculativeCache.put(deferredAttrContext.msym, speculativeTree, deferredAttrContext.phase);
                            return speculativeTree.type;
                        case CHECK:
                            Assert.check(mode == AttrMode.SPECULATIVE);
                            return attr.attribTree(tree, env, resultInfo);
                    }
                    Assert.error();
                    return null;
                } finally {
                    mode = deferredAttrContext.mode;
                }
            }
        }
    }

    /**
     * The 'mode' in which the deferred type is to be type-checked
     */
    public enum AttrMode {
        /**
         * A speculative type-checking round is used during overload resolution
         * mainly to generate constraints on inference variables. Side-effects
         * arising from type-checking the expression associated with the deferred
         * type are reversed after the speculative round finishes. This means the
         * expression tree will be left in a blank state.
         */
        SPECULATIVE,
        /**
         * This is the plain type-checking mode. Produces side-effects on the underlying AST node
         */
        CHECK;
    }

    /**
     * Routine that performs speculative type-checking; the input AST node is
     * cloned (to avoid side-effects cause by Attr) and compiler state is
     * restored after type-checking. All diagnostics (but critical ones) are
     * disabled during speculative type-checking.
     */
    JCTree attribSpeculative(JCTree tree, Env<AttrContext> env, ResultInfo resultInfo) {
        JCTree newTree = new TreeCopier<Object>(make).copy(tree);
        Env<AttrContext> speculativeEnv = env.dup(newTree, env.info.dup(env.info.scope.dupUnshared()));
        speculativeEnv.info.scope.owner = env.info.scope.owner;
        Filter<JCDiagnostic> prevDeferDiagsFilter = log.deferredDiagFilter;
        Queue<JCDiagnostic> prevDeferredDiags = log.deferredDiagnostics;
        final JavaFileObject currentSource = log.currentSourceFile();
        try {
            log.deferredDiagnostics = new ListBuffer<JCDiagnostic>();
            log.deferredDiagFilter = new Filter<JCDiagnostic>() {
                public boolean accepts(JCDiagnostic t) {
                    return t.getDiagnosticSource().getFile().equals(currentSource);
                }
            };
            attr.attribTree(newTree, speculativeEnv, resultInfo);
            unenterScanner.scan(newTree);
            return newTree;
        } catch (Abort ex) {
            //if some very bad condition occurred during deferred attribution
            //we should dump all errors before killing javac
            log.reportDeferredDiagnostics();
            throw ex;
        } finally {
            unenterScanner.scan(newTree);
            log.deferredDiagFilter = prevDeferDiagsFilter;
            log.deferredDiagnostics = prevDeferredDiags;
        }
    }
    //where
        protected TreeScanner unenterScanner = new TreeScanner() {
            @Override
            public void visitClassDef(JCClassDecl tree) {
                ClassSymbol csym = tree.sym;
                enter.typeEnvs.remove(csym);
                chk.compiled.remove(csym.flatname);
                syms.classes.remove(csym.flatname);
                super.visitClassDef(tree);
            }
        };

    /**
     * A deferred context is created on each method check. A deferred context is
     * used to keep track of information associated with the method check, such as
     * the symbol of the method being checked, the overload resolution phase,
     * the kind of attribution mode to be applied to deferred types and so forth.
     * As deferred types are processed (by the method check routine) stuck AST nodes
     * are added (as new deferred attribution nodes) to this context. The complete()
     * routine makes sure that all pending nodes are properly processed, by
     * progressively instantiating all inference variables on which one or more
     * deferred attribution node is stuck.
     */
    class DeferredAttrContext {

        /** attribution mode */
        final AttrMode mode;

        /** symbol of the method being checked */
        final Symbol msym;

        /** method resolution step */
        final Resolve.MethodResolutionPhase phase;

        /** inference context */
        final InferenceContext inferenceContext;

        /** list of deferred attribution nodes to be processed */
        ArrayList<DeferredAttrNode> deferredAttrNodes = new ArrayList<DeferredAttrNode>();

        DeferredAttrContext(AttrMode mode, Symbol msym, MethodResolutionPhase phase, InferenceContext inferenceContext) {
            this.mode = mode;
            this.msym = msym;
            this.phase = phase;
            this.inferenceContext = inferenceContext;
        }

        /**
         * Adds a node to the list of deferred attribution nodes - used by Resolve.rawCheckArgumentsApplicable
         * Nodes added this way act as 'roots' for the out-of-order method checking process.
         */
        void addDeferredAttrNode(final DeferredType dt, ResultInfo resultInfo, List<Type> stuckVars) {
            deferredAttrNodes.add(new DeferredAttrNode(dt, resultInfo, stuckVars));
        }

        /**
         * Incrementally process all nodes, by skipping 'stuck' nodes and attributing
         * 'unstuck' ones. If at any point no progress can be made (no 'unstuck' nodes)
         * some inference variable might get eagerly instantiated so that all nodes
         * can be type-checked.
         */
        void complete() {
            while (!deferredAttrNodes.isEmpty()) {
                Set<Type> stuckVars = new HashSet<Type>();
                boolean progress = false;
                //scan a defensive copy of the node list - this is because a deferred
                //attribution round can add new nodes to the list
                for (DeferredAttrNode deferredAttrNode : List.from(deferredAttrNodes)) {
                    if (!deferredAttrNode.isStuck()) {
                        deferredAttrNode.process();
                        deferredAttrNodes.remove(deferredAttrNode);
                        progress = true;
                    } else {
                        stuckVars.addAll(deferredAttrNode.stuckVars);
                    }
                }
                if (!progress) {
                    //remove all variables that have already been instantiated
                    //from the list of stuck variables
                    inferenceContext.solveAny(inferenceContext.freeVarsIn(List.from(stuckVars)), types, infer);
                    inferenceContext.notifyChange(types);
                }
            }
        }

        /**
         * Class representing a deferred attribution node. It keeps track of
         * a deferred type, along with the expected target type information.
         */
        class DeferredAttrNode implements Infer.InferenceContext.FreeTypeListener {

            /** underlying deferred type */
            DeferredType dt;

            /** underlying target type information */
            ResultInfo resultInfo;

            /** list of uninferred inference variables causing this node to be stuck */
            List<Type> stuckVars;

            DeferredAttrNode(DeferredType dt, ResultInfo resultInfo, List<Type> stuckVars) {
                this.dt = dt;
                this.resultInfo = resultInfo;
                this.stuckVars = stuckVars;
                if (!stuckVars.isEmpty()) {
                    resultInfo.checkContext.inferenceContext().addFreeTypeListener(stuckVars, this);
                }
            }

            @Override
            public void typesInferred(InferenceContext inferenceContext) {
                stuckVars = List.nil();
                resultInfo = resultInfo.dup(inferenceContext.asInstType(resultInfo.pt, types));
            }

            /**
             * is this node stuck?
             */
            boolean isStuck() {
                return stuckVars.nonEmpty();
            }

            /**
             * Process a deferred attribution node.
             * Invariant: a stuck node cannot be processed.
             */
            void process() {
                if (isStuck()) {
                    throw new IllegalStateException("Cannot process a stuck deferred node");
                }
                dt.check(resultInfo);
            }
        }
    }

    /** an empty deferred attribution context - all methods throw exceptions */
    final DeferredAttrContext emptyDeferredAttrContext =
            new DeferredAttrContext(null, null, null, null) {
                @Override
                void addDeferredAttrNode(DeferredType dt, ResultInfo ri, List<Type> stuckVars) {
                    Assert.error("Empty deferred context!");
                }
                @Override
                void complete() {
                    Assert.error("Empty deferred context!");
                }
            };

    /**
     * Map a list of types possibly containing one or more deferred types
     * into a list of ordinary types. Each deferred type D is mapped into a type T,
     * where T is computed by retrieving the type that has already been
     * computed for D during a previous deferred attribution round of the given kind.
     */
    class DeferredTypeMap extends Type.Mapping {

        DeferredAttrContext deferredAttrContext;

        protected DeferredTypeMap(AttrMode mode, Symbol msym, MethodResolutionPhase phase) {
            super(String.format("deferredTypeMap[%s]", mode));
            this.deferredAttrContext = new DeferredAttrContext(mode, msym, phase, infer.emptyContext);
        }

        protected boolean validState(DeferredType dt) {
            return dt.mode != null &&
                    deferredAttrContext.mode.ordinal() <= dt.mode.ordinal();
        }

        @Override
        public Type apply(Type t) {
            if (t.tag != DEFERRED) {
                return t.map(this);
            } else {
                DeferredType dt = (DeferredType)t;
                Assert.check(validState(dt));
                return typeOf(dt);
            }
        }

        protected Type typeOf(DeferredType dt) {
            switch (deferredAttrContext.mode) {
                case CHECK:
                    return dt.tree.type == null ? Type.noType : dt.tree.type;
                case SPECULATIVE:
                    return dt.speculativeType(deferredAttrContext.msym, deferredAttrContext.phase);
            }
            Assert.error();
            return null;
        }
    }

    /**
     * Specialized recovery deferred mapping.
     * Each deferred type D is mapped into a type T, where T is computed either by
     * (i) retrieving the type that has already been computed for D during a previous
     * attribution round (as before), or (ii) by synthesizing a new type R for D
     * (the latter step is useful in a recovery scenario).
     */
    public class RecoveryDeferredTypeMap extends DeferredTypeMap {

        public RecoveryDeferredTypeMap(AttrMode mode, Symbol msym, MethodResolutionPhase phase) {
            super(mode, msym, phase);
        }

        @Override
        protected Type typeOf(DeferredType dt) {
            Type owntype = super.typeOf(dt);
            return owntype.tag == NONE ?
                        recover(dt) : owntype;
        }

        @Override
        protected boolean validState(DeferredType dt) {
            return true;
        }

        /**
         * Synthesize a type for a deferred type that hasn't been previously
         * reduced to an ordinary type. Functional deferred types and conditionals
         * are mapped to themselves, in order to have a richer diagnostic
         * representation. Remaining deferred types are attributed using
         * a default expected type (j.l.Object).
         */
        private Type recover(DeferredType dt) {
            dt.check(attr.new RecoveryInfo(deferredAttrContext));
            switch (TreeInfo.skipParens(dt.tree).getTag()) {
                case LAMBDA:
                case REFERENCE:
                case CONDEXPR:
                    //propagate those deferred types to the
                    //diagnostic formatter
                    return dt;
                default:
                    return super.apply(dt);
            }
        }
    }

    /**
     * Retrieves the list of inference variables that need to be inferred before
     * an AST node can be type-checked
     */
    @SuppressWarnings("fallthrough")
    List<Type> stuckVars(JCTree tree, ResultInfo resultInfo) {
        if (resultInfo.pt.tag == NONE || resultInfo.pt.isErroneous()) {
            return List.nil();
        } else {
            StuckChecker sc = new StuckChecker(resultInfo);
            sc.scan(tree);
            return List.from(sc.stuckVars);
        }
    }

    /**
     * This visitor is used to check that structural expressions conform
     * to their target - this step is required as inference could end up
     * inferring types that make some of the nested expressions incompatible
     * with their corresponding instantiated target
     */
    class StuckChecker extends TreeScanner {

        Type pt;
        Filter<JCTree> treeFilter;
        Infer.InferenceContext inferenceContext;
        Set<Type> stuckVars = new HashSet<Type>();

        final Filter<JCTree> argsFilter = new Filter<JCTree>() {
            public boolean accepts(JCTree t) {
                switch (t.getTag()) {
                    case CONDEXPR:
                    case LAMBDA:
                    case PARENS:
                    case REFERENCE:
                        return true;
                    default:
                        return false;
                }
            }
        };

        final Filter<JCTree> lambdaBodyFilter = new Filter<JCTree>() {
            public boolean accepts(JCTree t) {
                switch (t.getTag()) {
                    case BLOCK: case CASE: case CATCH: case DOLOOP:
                    case FOREACHLOOP: case FORLOOP: case RETURN:
                    case SYNCHRONIZED: case SWITCH: case TRY: case WHILELOOP:
                        return true;
                    default:
                        return false;
                }
            }
        };

        StuckChecker(ResultInfo resultInfo) {
            this.pt = resultInfo.pt;
            this.inferenceContext = resultInfo.checkContext.inferenceContext();
            this.treeFilter = argsFilter;
        }

        @Override
        public void scan(JCTree tree) {
            if (tree != null && treeFilter.accepts(tree)) {
                super.scan(tree);
            }
        }

        @Override
        public void visitLambda(JCLambda tree) {
            Type prevPt = pt;
            Filter<JCTree> prevFilter = treeFilter;
            try {
                if (inferenceContext.inferenceVars().contains(pt)) {
                    stuckVars.add(pt);
                }
                if (!types.isFunctionalInterface(pt.tsym)) {
                    return;
                }
                Type descType = types.findDescriptorType(pt);
                List<Type> freeArgVars = inferenceContext.freeVarsIn(descType.getParameterTypes());
                if (!TreeInfo.isExplicitLambda(tree) &&
                        freeArgVars.nonEmpty()) {
                    stuckVars.addAll(freeArgVars);
                }
                pt = descType.getReturnType();
                if (tree.getBodyKind() == JCTree.JCLambda.BodyKind.EXPRESSION) {
                    scan(tree.getBody());
                } else {
                    treeFilter = lambdaBodyFilter;
                    super.visitLambda(tree);
                }
            } finally {
                pt = prevPt;
                treeFilter = prevFilter;
            }
        }

        @Override
        public void visitReference(JCMemberReference tree) {
            scan(tree.expr);
            if (inferenceContext.inferenceVars().contains(pt)) {
                stuckVars.add(pt);
                return;
            }
            if (!types.isFunctionalInterface(pt.tsym)) {
                return;
            }
            Type descType = types.findDescriptorType(pt);
            List<Type> freeArgVars = inferenceContext.freeVarsIn(descType.getParameterTypes());
            stuckVars.addAll(freeArgVars);
        }

        @Override
        public void visitReturn(JCReturn tree) {
            Filter<JCTree> prevFilter = treeFilter;
            try {
                treeFilter = argsFilter;
                if (tree.expr != null) {
                    scan(tree.expr);
                }
            } finally {
                treeFilter = prevFilter;
            }
        }
    }
}
