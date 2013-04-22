package jdk.nashorn.internal.codegen;

import static jdk.nashorn.internal.ir.FunctionNode.CompilationState.ATTR;
import static jdk.nashorn.internal.ir.FunctionNode.CompilationState.CONSTANT_FOLDED;
import static jdk.nashorn.internal.ir.FunctionNode.CompilationState.FINALIZED;
import static jdk.nashorn.internal.ir.FunctionNode.CompilationState.INITIALIZED;
import static jdk.nashorn.internal.ir.FunctionNode.CompilationState.LOWERED;
import static jdk.nashorn.internal.ir.FunctionNode.CompilationState.PARSED;
import static jdk.nashorn.internal.ir.FunctionNode.CompilationState.SPLIT;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.Block;
import jdk.nashorn.internal.ir.CallNode;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.FunctionNode.CompilationState;
import jdk.nashorn.internal.ir.LexicalContext;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.debug.ASTWriter;
import jdk.nashorn.internal.ir.debug.PrintVisitor;
import jdk.nashorn.internal.ir.visitor.NodeOperatorVisitor;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.ECMAErrors;
import jdk.nashorn.internal.runtime.ScriptEnvironment;
import jdk.nashorn.internal.runtime.Timing;

/**
 * A compilation phase is a step in the processes of turning a JavaScript
 * FunctionNode into bytecode. It has an optional return value.
 */
enum CompilationPhase {

    /*
     * Lazy initialization - tag all function nodes not the script as lazy as
     * default policy. The will get trampolines and only be generated when
     * called
     */
    LAZY_INITIALIZATION_PHASE(EnumSet.of(INITIALIZED, PARSED)) {
        @Override
        FunctionNode transform(final Compiler compiler, final FunctionNode fn0) {

            /*
             * For lazy compilation, we might be given a node previously marked
             * as lazy to compile as the outermost function node in the
             * compiler. Unmark it so it can be compiled and not cause
             * recursion. Make sure the return type is unknown so it can be
             * correctly deduced. Return types are always Objects in Lazy nodes
             * as we haven't got a change to generate code for them and decude
             * its parameter specialization
             *
             * TODO: in the future specializations from a callsite will be
             * passed here so we can generate a better non-lazy version of a
             * function from a trampoline
             */

            final FunctionNode outermostFunctionNode = compiler.getFunctionNode();
            assert outermostFunctionNode == fn0;

            final Set<FunctionNode> neverLazy = new HashSet<>();
            final Set<FunctionNode> lazy      = new HashSet<>();

            FunctionNode newFunctionNode = outermostFunctionNode;

            newFunctionNode = (FunctionNode)newFunctionNode.accept(new NodeVisitor() {
                // self references are done with invokestatic and thus cannot
                // have trampolines - never lazy
                @Override
                public boolean enterCallNode(final CallNode node) {
                    final Node callee = node.getFunction();
                    if (callee instanceof FunctionNode) {
                        neverLazy.add(((FunctionNode)callee));
                        return false;
                    }
                    return true;
                }

                //any function that isn't the outermost one must be marked as lazy
                @Override
                public boolean enterFunctionNode(final FunctionNode node) {
                    assert compiler.isLazy();
                    lazy.add(node);
                    return true;
                }
            });

            //at least one method is non lazy - the outermost one
            neverLazy.add(newFunctionNode);

            for (final FunctionNode node : neverLazy) {
                Compiler.LOG.fine(
                        "Marking ",
                        node.getName(),
                        " as non lazy, as it's a self reference");
                lazy.remove(node);
            }

            newFunctionNode = (FunctionNode)newFunctionNode.accept(new NodeOperatorVisitor() {
                @Override
                public Node leaveFunctionNode(final FunctionNode functionNode) {
                    final LexicalContext lc = getLexicalContext();
                    if (lazy.contains(functionNode)) {
                        Compiler.LOG.fine(
                                "Marking ",
                                functionNode.getName(),
                                " as lazy");
                        final FunctionNode parent = lc.getParentFunction(functionNode);
                        assert parent != null;
                        lc.setFlag(parent, FunctionNode.HAS_LAZY_CHILDREN);
                        lc.setFlag(parent.getBody(), Block.NEEDS_SCOPE);
                        lc.setFlag(functionNode, FunctionNode.IS_LAZY);
                        return functionNode;
                    }

                    return functionNode.
                        clearFlag(lc, FunctionNode.IS_LAZY).
                        setReturnType(lc, Type.UNKNOWN);
                }
            });

            return newFunctionNode;
        }

        @Override
        public String toString() {
            return "[Lazy JIT Initialization]";
        }
    },

    /*
     * Constant folding pass Simple constant folding that will make elementary
     * constructs go away
     */
    CONSTANT_FOLDING_PHASE(EnumSet.of(INITIALIZED, PARSED)) {
        @Override
        FunctionNode transform(final Compiler compiler, final FunctionNode fn) {
            return (FunctionNode)fn.accept(new FoldConstants());
        }

        @Override
        public String toString() {
            return "[Constant Folding]";
        }
    },

    /*
     * Lower (Control flow pass) Finalizes the control flow. Clones blocks for
     * finally constructs and similar things. Establishes termination criteria
     * for nodes Guarantee return instructions to method making sure control
     * flow cannot fall off the end. Replacing high level nodes with lower such
     * as runtime nodes where applicable.
     */
    LOWERING_PHASE(EnumSet.of(INITIALIZED, PARSED, CONSTANT_FOLDED)) {
        @Override
        FunctionNode transform(final Compiler compiler, final FunctionNode fn) {
            return (FunctionNode)fn.accept(new Lower());
        }

        @Override
        public String toString() {
            return "[Control Flow Lowering]";
        }
    },

    /*
     * Attribution Assign symbols and types to all nodes.
     */
    ATTRIBUTION_PHASE(EnumSet.of(INITIALIZED, PARSED, CONSTANT_FOLDED, LOWERED)) {
        @Override
        FunctionNode transform(final Compiler compiler, final FunctionNode fn) {
            return (FunctionNode)initReturnTypes(fn).accept(new Attr());
        }

        /**
         * Pessimistically set all lazy functions' return types to Object
         * @param functionNode node where to start iterating
         */
        private FunctionNode initReturnTypes(final FunctionNode functionNode) {
            return (FunctionNode)functionNode.accept(new NodeVisitor() {
                @Override
                public Node leaveFunctionNode(final FunctionNode node) {
                    return node.isLazy() ?
                           node.setReturnType(getLexicalContext(), Type.OBJECT) :
                           node.setReturnType(getLexicalContext(), Type.UNKNOWN);
                }
            });
        }

        @Override
        public String toString() {
            return "[Type Attribution]";
        }
    },

    /*
     * Splitter Split the AST into several compile units based on a size
     * heuristic Splitter needs attributed AST for weight calculations (e.g. is
     * a + b a ScriptRuntime.ADD with call overhead or a dadd with much less).
     * Split IR can lead to scope information being changed.
     */
    SPLITTING_PHASE(EnumSet.of(INITIALIZED, PARSED, CONSTANT_FOLDED, LOWERED, ATTR)) {
        @Override
        FunctionNode transform(final Compiler compiler, final FunctionNode fn) {
            final CompileUnit outermostCompileUnit = compiler.addCompileUnit(compiler.firstCompileUnitName());

            final FunctionNode newFunctionNode = new Splitter(compiler, fn, outermostCompileUnit).split(fn);

            assert newFunctionNode.getCompileUnit() == outermostCompileUnit : "fn.compileUnit (" + newFunctionNode.getCompileUnit() + ") != " + outermostCompileUnit;

            if (newFunctionNode.isStrict()) {
                assert compiler.getStrictMode();
                compiler.setStrictMode(true);
            }

            /*
            newFunctionNode.accept(new NodeVisitor() {
                @Override
                public boolean enterFunctionNode(final FunctionNode functionNode) {
                    assert functionNode.getCompileUnit() != null : functionNode.getName() + " " + Debug.id(functionNode) + " has no compile unit";
                    return true;
                }
            });*/

            return newFunctionNode;
        }

        @Override
        public String toString() {
            return "[Code Splitting]";
        }
    },

    /*
     * FinalizeTypes
     *
     * This pass finalizes the types for nodes. If Attr created wider types than
     * known during the first pass, convert nodes are inserted or access nodes
     * are specialized where scope accesses.
     *
     * Runtime nodes may be removed and primitivized or reintroduced depending
     * on information that was established in Attr.
     *
     * Contract: all variables must have slot assignments and scope assignments
     * before type finalization.
     */
    TYPE_FINALIZATION_PHASE(EnumSet.of(INITIALIZED, PARSED, CONSTANT_FOLDED, LOWERED, ATTR, SPLIT)) {
        @Override
        FunctionNode transform(final Compiler compiler, final FunctionNode fn) {
            final ScriptEnvironment env = compiler.getEnv();

            final FunctionNode newFunctionNode = (FunctionNode)fn.accept(new FinalizeTypes());

            if (env._print_lower_ast) {
                env.getErr().println(new ASTWriter(newFunctionNode));
            }

            if (env._print_lower_parse) {
                env.getErr().println(new PrintVisitor(newFunctionNode));
            }

            return newFunctionNode;
        }

        @Override
        public String toString() {
            return "[Type Finalization]";
        }
    },

    /*
     * Bytecode generation:
     *
     * Generate the byte code class(es) resulting from the compiled FunctionNode
     */
    BYTECODE_GENERATION_PHASE(EnumSet.of(INITIALIZED, PARSED, CONSTANT_FOLDED, LOWERED, ATTR, SPLIT, FINALIZED)) {
        @Override
        FunctionNode transform(final Compiler compiler, final FunctionNode fn) {
            final ScriptEnvironment env = compiler.getEnv();
            FunctionNode newFunctionNode = fn;

            try {
                final CodeGenerator codegen = new CodeGenerator(compiler);
                newFunctionNode = (FunctionNode)newFunctionNode.accept(codegen);
                codegen.generateScopeCalls();
            } catch (final VerifyError e) {
                if (env._verify_code || env._print_code) {
                    env.getErr().println(e.getClass().getSimpleName() + ": "  + e.getMessage());
                    if (env._dump_on_error) {
                        e.printStackTrace(env.getErr());
                    }
                } else {
                    throw e;
                }
            }

            for (final CompileUnit compileUnit : compiler.getCompileUnits()) {
                final ClassEmitter classEmitter = compileUnit.getClassEmitter();
                classEmitter.end();

                final byte[] bytecode = classEmitter.toByteArray();
                assert bytecode != null;

                final String className = compileUnit.getUnitClassName();

                compiler.addClass(className, bytecode);

                // should could be printed to stderr for generate class?
                if (env._print_code) {
                    final StringBuilder sb = new StringBuilder();
                    sb.append("class: " + className).append('\n')
                            .append(ClassEmitter.disassemble(bytecode))
                            .append("=====");
                    env.getErr().println(sb);
                }

                // should we verify the generated code?
                if (env._verify_code) {
                    compiler.getCodeInstaller().verify(bytecode);
                }

                // should code be dumped to disk - only valid in compile_only
                // mode?
                if (env._dest_dir != null && env._compile_only) {
                    final String fileName = className.replace('.', File.separatorChar) + ".class";
                    final int index = fileName.lastIndexOf(File.separatorChar);

                    if (index != -1) {
                        final File dir = new File(fileName.substring(0, index));
                        try {
                            if (!dir.exists() && !dir.mkdirs()) {
                                throw new IOException(dir.toString());
                            }
                            final File file = new File(env._dest_dir, fileName);
                            try (final FileOutputStream fos = new FileOutputStream(file)) {
                                fos.write(bytecode);
                            }
                        } catch (final IOException e) {
                            Compiler.LOG.warning("Skipping class dump for ",
                                    className,
                                    ": ",
                                    ECMAErrors.getMessage(
                                        "io.error.cant.write",
                                        dir.toString()));
                        }
                    }
                }
            }

            return newFunctionNode;
        }

        @Override
        public String toString() {
            return "[Bytecode Generation]";
        }
    };

    private final EnumSet<CompilationState> pre;
    private long startTime;
    private long endTime;
    private boolean isFinished;

    private CompilationPhase(final EnumSet<CompilationState> pre) {
        this.pre = pre;
    }

    boolean isApplicable(final FunctionNode functionNode) {
        return functionNode.hasState(pre);
    }

    protected FunctionNode begin(final FunctionNode functionNode) {
        if (pre != null) {
            // check that everything in pre is present
            for (final CompilationState state : pre) {
                assert functionNode.hasState(state);
            }
            // check that nothing else is present
            for (final CompilationState state : CompilationState.values()) {
                assert !(functionNode.hasState(state) && !pre.contains(state));
            }
        }

        startTime = System.currentTimeMillis();
        return functionNode;
    }

    protected FunctionNode end(final FunctionNode functionNode) {
        endTime = System.currentTimeMillis();
        Timing.accumulateTime(toString(), endTime - startTime);

        isFinished = true;
        return functionNode;
    }

    boolean isFinished() {
        return isFinished;
    }

    long getStartTime() {
        return startTime;
    }

    long getEndTime() {
        return endTime;
    }

    abstract FunctionNode transform(final Compiler compiler, final FunctionNode functionNode) throws CompilationException;

    final FunctionNode apply(final Compiler compiler, final FunctionNode functionNode) throws CompilationException {
        if (!isApplicable(functionNode)) {
            throw new CompilationException("compile phase not applicable: " + this + " to " + functionNode.getName() + " state=" + functionNode.getState());
        }
        return end(transform(compiler, begin(functionNode)));
    }

}
