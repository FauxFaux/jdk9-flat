/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.codegen;

import java.util.HashSet;
import java.util.List;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.AccessNode;
import jdk.nashorn.internal.ir.Assignment;
import jdk.nashorn.internal.ir.BinaryNode;
import jdk.nashorn.internal.ir.Block;
import jdk.nashorn.internal.ir.CallNode;
import jdk.nashorn.internal.ir.CallNode.EvalArgs;
import jdk.nashorn.internal.ir.CaseNode;
import jdk.nashorn.internal.ir.CatchNode;
import jdk.nashorn.internal.ir.DoWhileNode;
import jdk.nashorn.internal.ir.ExecuteNode;
import jdk.nashorn.internal.ir.ForNode;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.FunctionNode.CompilationState;
import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.IfNode;
import jdk.nashorn.internal.ir.IndexNode;
import jdk.nashorn.internal.ir.LexicalContext;
import jdk.nashorn.internal.ir.LiteralNode;
import jdk.nashorn.internal.ir.LiteralNode.ArrayLiteralNode;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.ReturnNode;
import jdk.nashorn.internal.ir.RuntimeNode;
import jdk.nashorn.internal.ir.RuntimeNode.Request;
import jdk.nashorn.internal.ir.SwitchNode;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.ir.TernaryNode;
import jdk.nashorn.internal.ir.ThrowNode;
import jdk.nashorn.internal.ir.TypeOverride;
import jdk.nashorn.internal.ir.UnaryNode;
import jdk.nashorn.internal.ir.VarNode;
import jdk.nashorn.internal.ir.WhileNode;
import jdk.nashorn.internal.ir.WithNode;
import jdk.nashorn.internal.ir.visitor.NodeOperatorVisitor;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.parser.TokenType;
import jdk.nashorn.internal.runtime.Debug;
import jdk.nashorn.internal.runtime.DebugLogger;
import jdk.nashorn.internal.runtime.JSType;

/**
 * Lower to more primitive operations. After lowering, an AST has symbols and
 * types. Lowering may also add specialized versions of methods to the script if
 * the optimizer is turned on.
 *
 * Any expression that requires temporary storage as part of computation will
 * also be detected here and give a temporary symbol
 *
 * For any op that we process in FinalizeTypes it is an absolute guarantee
 * that scope and slot information is correct. This enables e.g. AccessSpecialization
 * and frame optimizations
 */

final class FinalizeTypes extends NodeOperatorVisitor {

    private static final DebugLogger LOG = new DebugLogger("finalize");

    private final LexicalContext lexicalContext = new LexicalContext();

    FinalizeTypes() {
    }

    @Override
    public Node leaveCallNode(final CallNode callNode) {
        final EvalArgs evalArgs = callNode.getEvalArgs();
        if (evalArgs != null) {
            evalArgs.setCode(evalArgs.getCode().accept(this));
        }

        // AccessSpecializer - call return type may change the access for this location
        final Node function = callNode.getFunction();
        if (function instanceof FunctionNode) {
            return setTypeOverride(callNode, ((FunctionNode)function).getReturnType());
        }
        return callNode;
    }

    private Node leaveUnary(final UnaryNode unaryNode) {
        return unaryNode.setRHS(convert(unaryNode.rhs(), unaryNode.getType()));
    }

    @Override
    public Node leaveADD(final UnaryNode unaryNode) {
        return leaveUnary(unaryNode);
    }

    @Override
    public Node leaveBIT_NOT(final UnaryNode unaryNode) {
        return leaveUnary(unaryNode);
    }

    @Override
    public Node leaveCONVERT(final UnaryNode unaryNode) {
        assert unaryNode.rhs().tokenType() != TokenType.CONVERT : "convert(convert encountered. check its origin and remove it";
        return unaryNode;
    }

    @Override
    public Node leaveDECINC(final UnaryNode unaryNode) {
        return specialize(unaryNode).node;
    }

    @Override
    public Node leaveNEW(final UnaryNode unaryNode) {
        assert unaryNode.getSymbol() != null && unaryNode.getSymbol().getSymbolType().isObject();
        ((CallNode)unaryNode.rhs()).setIsNew();
        return unaryNode;
    }

    @Override
    public Node leaveSUB(final UnaryNode unaryNode) {
        return leaveUnary(unaryNode);
    }

    /**
     * Add is a special binary, as it works not only on arithmetic, but for
     * strings etc as well.
     */
    @Override
    public Node leaveADD(final BinaryNode binaryNode) {
        final Node lhs = binaryNode.lhs();
        final Node rhs = binaryNode.rhs();

        final Type type = binaryNode.getType();

        if (type.isObject()) {
            if (!isAddString(binaryNode)) {
                return new RuntimeNode(binaryNode, Request.ADD);
            }
        }

        return binaryNode.setLHS(convert(lhs, type)).setRHS(convert(rhs, type));
    }

    @Override
    public Node leaveAND(final BinaryNode binaryNode) {
        return binaryNode;
    }

    @Override
    public Node leaveASSIGN(final BinaryNode binaryNode) {
        final SpecializedNode specialized = specialize(binaryNode);
        final BinaryNode specBinaryNode = (BinaryNode)specialized.node;
        Type destType = specialized.type;
        if (destType == null) {
            destType = specBinaryNode.getType();
        }
        return specBinaryNode.setRHS(convert(specBinaryNode.rhs(), destType));
    }

    @Override
    public Node leaveASSIGN_ADD(final BinaryNode binaryNode) {
        return leaveASSIGN(binaryNode);
    }

    @Override
    public Node leaveASSIGN_BIT_AND(final BinaryNode binaryNode) {
        return leaveASSIGN(binaryNode);
    }

    @Override
    public Node leaveASSIGN_BIT_OR(final BinaryNode binaryNode) {
        return leaveASSIGN(binaryNode);
    }

    @Override
    public Node leaveASSIGN_BIT_XOR(final BinaryNode binaryNode) {
        return leaveASSIGN(binaryNode);
    }

    @Override
    public Node leaveASSIGN_DIV(final BinaryNode binaryNode) {
        return leaveASSIGN(binaryNode);
    }

    @Override
    public Node leaveASSIGN_MOD(final BinaryNode binaryNode) {
        return leaveASSIGN(binaryNode);
    }

    @Override
    public Node leaveASSIGN_MUL(final BinaryNode binaryNode) {
        return leaveASSIGN(binaryNode);
    }

    @Override
    public Node leaveASSIGN_SAR(final BinaryNode binaryNode) {
        return leaveASSIGN(binaryNode);
    }

    @Override
    public Node leaveASSIGN_SHL(final BinaryNode binaryNode) {
        return leaveASSIGN(binaryNode);
    }

    @Override
    public Node leaveASSIGN_SHR(final BinaryNode binaryNode) {
        return leaveASSIGN(binaryNode);
    }

    @Override
    public Node leaveASSIGN_SUB(final BinaryNode binaryNode) {
        return leaveASSIGN(binaryNode);
    }

    @Override
    public Node leaveBIT_AND(BinaryNode binaryNode) {
        assert binaryNode.getSymbol() != null && binaryNode.getSymbol().getSymbolType().isInteger() : "int coercion expected: " + binaryNode.getSymbol();
        return leaveBinary(binaryNode, Type.INT, Type.INT);
    }

    @Override
    public Node leaveBIT_OR(BinaryNode binaryNode) {
        assert binaryNode.getSymbol() != null && binaryNode.getSymbol().getSymbolType().isInteger() : "int coercion expected: " + binaryNode.getSymbol();
        return leaveBinary(binaryNode, Type.INT, Type.INT);
    }

    @Override
    public Node leaveBIT_XOR(BinaryNode binaryNode) {
        assert binaryNode.getSymbol() != null && binaryNode.getSymbol().getSymbolType().isInteger() : "int coercion expected: " + binaryNode.getSymbol();
        return leaveBinary(binaryNode, Type.INT, Type.INT);
    }

    @Override
    public Node leaveCOMMALEFT(final BinaryNode binaryNode) {
        assert binaryNode.getSymbol() != null;
        final BinaryNode newBinaryNode = (BinaryNode)binaryNode.setRHS(discard(binaryNode.rhs()));
        // AccessSpecializer - the type of lhs, which is the remaining value of this node may have changed
        // in that case, update the node type as well
        propagateType(newBinaryNode, newBinaryNode.lhs().getType());
        return newBinaryNode;
    }

    @Override
    public Node leaveCOMMARIGHT(final BinaryNode binaryNode) {
        assert binaryNode.getSymbol() != null;
        final BinaryNode newBinaryNode = binaryNode.setLHS(discard(binaryNode.lhs()));
        // AccessSpecializer - the type of rhs, which is the remaining value of this node may have changed
        // in that case, update the node type as well
        propagateType(newBinaryNode, newBinaryNode.rhs().getType());
        return newBinaryNode;
    }

    @Override
    public Node leaveDIV(final BinaryNode binaryNode) {
        return leaveBinaryArith(binaryNode);
    }


    @Override
    public Node leaveEQ(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode, Request.EQ);
    }

    @Override
    public Node leaveEQ_STRICT(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode, Request.EQ_STRICT);
    }

    @Override
    public Node leaveGE(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode, Request.GE);
    }

    @Override
    public Node leaveGT(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode, Request.GT);
    }

    @Override
    public Node leaveLE(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode, Request.LE);
    }

    @Override
    public Node leaveLT(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode, Request.LT);
    }

    @Override
    public Node leaveMOD(final BinaryNode binaryNode) {
        return leaveBinaryArith(binaryNode);
    }

    @Override
    public Node leaveMUL(final BinaryNode binaryNode) {
        return leaveBinaryArith(binaryNode);
    }

    @Override
    public Node leaveNE(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode, Request.NE);
    }

    @Override
    public Node leaveNE_STRICT(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode, Request.NE_STRICT);
    }

    @Override
    public Node leaveOR(final BinaryNode binaryNode) {
        return binaryNode;
    }

    @Override
    public Node leaveSAR(final BinaryNode binaryNode) {
        return leaveBinary(binaryNode, Type.INT, Type.INT);
    }

    @Override
    public Node leaveSHL(final BinaryNode binaryNode) {
        return leaveBinary(binaryNode, Type.INT, Type.INT);
    }

    @Override
    public Node leaveSHR(final BinaryNode binaryNode) {
        assert binaryNode.getSymbol() != null && binaryNode.getSymbol().getSymbolType().isLong() : "long coercion expected: " + binaryNode.getSymbol();
        return leaveBinary(binaryNode, Type.INT, Type.INT);
    }

    @Override
    public Node leaveSUB(final BinaryNode binaryNode) {
        return leaveBinaryArith(binaryNode);
    }

    @Override
    public Node enterBlock(final Block block) {
        lexicalContext.push(block);
        updateSymbols(block);
        return block;
    }

    @Override
    public Node leaveBlock(Block block) {
        lexicalContext.pop(block);
        return super.leaveBlock(block);
    }

    @Override
    public Node leaveCatchNode(final CatchNode catchNode) {
        final Node exceptionCondition = catchNode.getExceptionCondition();
        if (exceptionCondition != null) {
            catchNode.setExceptionCondition(convert(exceptionCondition, Type.BOOLEAN));
        }
        return catchNode;
    }

    @Override
    public Node enterDoWhileNode(final DoWhileNode doWhileNode) {
        return enterWhileNode(doWhileNode);
    }

    @Override
    public Node leaveDoWhileNode(final DoWhileNode doWhileNode) {
        return leaveWhileNode(doWhileNode);
    }

    @Override
    public Node leaveExecuteNode(final ExecuteNode executeNode) {
        executeNode.setExpression(discard(executeNode.getExpression()));
        return executeNode;
    }

    @Override
    public Node leaveForNode(final ForNode forNode) {
        final Node init   = forNode.getInit();
        final Node test   = forNode.getTest();
        final Node modify = forNode.getModify();

        if (forNode.isForIn()) {
            forNode.setModify(convert(forNode.getModify(), Type.OBJECT)); // NASHORN-400
            return forNode;
        }

        if (init != null) {
            forNode.setInit(discard(init));
        }

        if (test != null) {
            forNode.setTest(convert(test, Type.BOOLEAN));
        } else {
            assert forNode.hasGoto() : "forNode " + forNode + " needs goto and is missing it in " + getCurrentFunctionNode();
        }

        if (modify != null) {
            forNode.setModify(discard(modify));
        }

        return forNode;
    }

    @Override
    public Node enterFunctionNode(final FunctionNode functionNode) {
        if (functionNode.isLazy()) {
            return null;
        }

        lexicalContext.push(functionNode);
        // If the function doesn't need a callee, we ensure its __callee__ symbol doesn't get a slot. We can't do
        // this earlier, as access to scoped variables, self symbol, etc. in previous phases can all trigger the
        // need for the callee.
        if (!functionNode.needsCallee()) {
            functionNode.getCalleeNode().getSymbol().setNeedsSlot(false);
        }
        // Similar reasoning applies to __scope__ symbol: if the function doesn't need either parent scope or its
        // own scope, we ensure it doesn't get a slot, but we can't determine whether it needs a scope earlier than
        // this phase.
        if (!(functionNode.needsScope() || functionNode.needsParentScope())) {
            functionNode.getScopeNode().getSymbol().setNeedsSlot(false);
        }

        updateSymbols(functionNode);
        functionNode.setState(CompilationState.FINALIZED);

        return functionNode;
    }

    @Override
    public Node leaveFunctionNode(FunctionNode functionNode) {
        lexicalContext.pop(functionNode);
        return super.leaveFunctionNode(functionNode);
    }

    @Override
    public Node leaveIfNode(final IfNode ifNode) {
        ifNode.setTest(convert(ifNode.getTest(), Type.BOOLEAN));
        return ifNode;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Node enterLiteralNode(final LiteralNode literalNode) {
        if (literalNode instanceof ArrayLiteralNode) {
            final ArrayLiteralNode arrayLiteralNode = (ArrayLiteralNode)literalNode;
            final Node[]           array            = arrayLiteralNode.getValue();
            final Type             elementType      = arrayLiteralNode.getElementType();

            for (int i = 0; i < array.length; i++) {
                final Node element = array[i];
                if (element != null) {
                    array[i] = convert(element.accept(this), elementType);
                }
            }
        }

        return null;
    }

    @Override
    public Node leaveReturnNode(final ReturnNode returnNode) {
        final Node expr = returnNode.getExpression();
        if (expr != null) {
            returnNode.setExpression(convert(expr, getCurrentFunctionNode().getReturnType()));
        }
        return returnNode;
    }

    @Override
    public Node leaveRuntimeNode(final RuntimeNode runtimeNode) {
        final List<Node> args = runtimeNode.getArgs();
        for (final Node arg : args) {
            assert !arg.getType().isUnknown();
        }
        return runtimeNode;
    }

    @Override
    public Node leaveSwitchNode(final SwitchNode switchNode) {
        final Node           expression  = switchNode.getExpression();
        final List<CaseNode> cases       = switchNode.getCases();
        final boolean        allInteger  = switchNode.getTag().getSymbolType().isInteger();

        if (!allInteger) {
            switchNode.setExpression(convert(expression, Type.OBJECT));
            for (final CaseNode caseNode : cases) {
                final Node test = caseNode.getTest();
                if (test != null) {
                    caseNode.setTest(convert(test, Type.OBJECT));
                }
            }
        }

        return switchNode;
    }

    @Override
    public Node leaveTernaryNode(final TernaryNode ternaryNode) {
        return ternaryNode.setLHS(convert(ternaryNode.lhs(), Type.BOOLEAN));
    }

    @Override
    public Node leaveThrowNode(final ThrowNode throwNode) {
        throwNode.setExpression(convert(throwNode.getExpression(), Type.OBJECT));
        return throwNode;
    }

    @Override
    public Node leaveVarNode(final VarNode varNode) {
        final Node rhs = varNode.getInit();
        if (rhs != null) {
            final SpecializedNode specialized = specialize(varNode);
            final VarNode specVarNode = (VarNode)specialized.node;
            Type destType = specialized.type;
            if (destType == null) {
                destType = specVarNode.getType();
            }
            assert specVarNode.hasType() : specVarNode + " doesn't have a type";
            return specVarNode.setInit(convert(rhs, destType));
        }
        return varNode;
    }

    @Override
    public Node leaveWhileNode(final WhileNode whileNode) {
        final Node test = whileNode.getTest();
        if (test != null) {
            whileNode.setTest(convert(test, Type.BOOLEAN));
        }
        return whileNode;
    }

    @Override
    public Node leaveWithNode(final WithNode withNode) {
        withNode.setExpression(convert(withNode.getExpression(), Type.OBJECT));
        return withNode;
    }

    private static void updateSymbolsLog(final FunctionNode functionNode, final Symbol symbol, final boolean loseSlot) {
        if (!symbol.isScope()) {
            LOG.finest("updateSymbols: " + symbol + " => scope, because all vars in " + functionNode.getName() + " are in scope");
        }
        if (loseSlot && symbol.hasSlot()) {
            LOG.finest("updateSymbols: " + symbol + " => no slot, because all vars in " + functionNode.getName() + " are in scope");
        }
    }

    /**
     * Called after a block or function node (subclass of block) is finished. Guarantees
     * that scope and slot information is correct for every symbol
     * @param block block for which to to finalize type info.
     */
    private void updateSymbols(final Block block) {
        if (!block.needsScope()) {
            return; // nothing to do
        }

        final FunctionNode functionNode = lexicalContext.getFunction(block);
        assert !(block instanceof FunctionNode) || functionNode == block;

        final List<Symbol> symbols        = block.getFrame().getSymbols();
        final boolean      allVarsInScope = functionNode.allVarsInScope();
        final boolean      isVarArg       = functionNode.isVarArg();

        for (final Symbol symbol : symbols) {
            if (symbol.isInternal() || symbol.isThis()) {
                continue;
            }

            if (symbol.isVar()) {
                if (allVarsInScope || symbol.isScope()) {
                    updateSymbolsLog(functionNode, symbol, true);
                    symbol.setIsScope();
                    symbol.setNeedsSlot(false);
                } else {
                    assert symbol.hasSlot() : symbol + " should have a slot only, no scope";
                }
            } else if (symbol.isParam() && (allVarsInScope || isVarArg || symbol.isScope())) {
                updateSymbolsLog(functionNode, symbol, isVarArg);
                symbol.setIsScope();
                symbol.setNeedsSlot(!isVarArg);
            }
        }
    }

    /**
     * Exit a comparison node and do the appropriate replacements. We need to introduce runtime
     * nodes late for comparisons as types aren't known until the last minute
     *
     * Both compares and adds may turn into runtimes node at this level as when we first bump
     * into the op in Attr, we may type it according to what we know there, which may be wrong later
     *
     * e.g. i (int) < 5 -> normal compare
     *     i = object
     *  then the post pass that would add the conversion to the 5 needs to
     *
     * @param binaryNode binary node to leave
     * @param request    runtime request
     * @return lowered cmp node
     */
    @SuppressWarnings("fallthrough")
    private Node leaveCmp(final BinaryNode binaryNode, final RuntimeNode.Request request) {
        final Node lhs    = binaryNode.lhs();
        final Node rhs    = binaryNode.rhs();

        Type widest = Type.widest(lhs.getType(), rhs.getType());

        boolean newRuntimeNode = false, finalized = false;
        switch (request) {
        case EQ_STRICT:
        case NE_STRICT:
            if (lhs.getType().isBoolean() != rhs.getType().isBoolean()) {
                newRuntimeNode = true;
                widest = Type.OBJECT;
                finalized = true;
            }
            //fallthru
        default:
            if (newRuntimeNode || widest.isObject()) {
                final RuntimeNode runtimeNode = new RuntimeNode(binaryNode, request);
                if (finalized) {
                    runtimeNode.setIsFinal();
                }
                return runtimeNode;
            }
            break;
        }

        return binaryNode.setLHS(convert(lhs, widest)).setRHS(convert(rhs, widest));
    }

    /**
     * Compute the binary arithmetic type given the lhs and an rhs of a binary expression
     * @param lhsType  the lhs type
     * @param rhsType  the rhs type
     * @return the correct binary type
     */
    private static Type binaryArithType(final Type lhsType, final Type rhsType) {
        if (!Compiler.shouldUseIntegerArithmetic()) {
            return Type.NUMBER;
        }
        return Type.widest(lhsType, rhsType, Type.NUMBER);
    }

    private Node leaveBinaryArith(final BinaryNode binaryNode) {
        final Type type = binaryArithType(binaryNode.lhs().getType(), binaryNode.rhs().getType());
        return leaveBinary(binaryNode, type, type);
    }

    private Node leaveBinary(final BinaryNode binaryNode, final Type lhsType, final Type rhsType) {
        return binaryNode.setLHS(convert(binaryNode.lhs(), lhsType)).setRHS(convert(binaryNode.rhs(), rhsType));
    }

    /**
     * A symbol (and {@link Property}) can be tagged as "may be primitive". This is
     * used a hint for dual fields that it is even worth it to try representing this
     * field as something other than java.lang.Object.
     *
     * @param node node in which to tag symbols as primitive
     * @param to   which primitive type to use for tagging
     */
    private static void setCanBePrimitive(final Node node, final Type to) {
        final HashSet<Node> exclude = new HashSet<>();

        node.accept(new NodeVisitor() {
            private void setCanBePrimitive(final Symbol symbol) {
                LOG.info("*** can be primitive symbol " + symbol + " " + Debug.id(symbol));
                symbol.setCanBePrimitive(to);
            }

            @Override
            public Node enterIdentNode(final IdentNode identNode) {
                if (!exclude.contains(identNode)) {
                    setCanBePrimitive(identNode.getSymbol());
                }
                return null;
            }

            @Override
            public Node enterAccessNode(final AccessNode accessNode) {
                setCanBePrimitive(accessNode.getProperty().getSymbol());
                return null;
            }

            @Override
            public Node enterIndexNode(final IndexNode indexNode) {
                exclude.add(indexNode.getBase()); //prevent array base node to be flagged as primitive, but k in a[k++] is fine
                return indexNode;
            }
        });
    }

    private static class SpecializedNode {
        final Node node;
        final Type type;

        SpecializedNode(Node node, Type type) {
            this.node = node;
            this.type = type;
        }
    }

    private static <T extends Node> SpecializedNode specialize(final Assignment<T> assignment) {
        final Node node = ((Node)assignment);
        final T lhs = assignment.getAssignmentDest();
        final Node rhs = assignment.getAssignmentSource();

        if (!canHaveCallSiteType(lhs)) {
            return new SpecializedNode(node, null);
        }

        final Type to;
        if (node.isSelfModifying()) {
            to = node.getWidestOperationType();
        } else {
            to = rhs.getType();
        }

        if (!isSupportedCallSiteType(to)) {
            //meaningless to specialize to boolean or object
            return new SpecializedNode(node, null);
        }

        final Node newNode = assignment.setAssignmentDest(setTypeOverride(lhs, to));
        propagateType(newNode, to);

        return new SpecializedNode(newNode, to);
    }


    /**
     * Is this a node that can have its type overridden. This is true for
     * AccessNodes, IndexNodes and IdentNodes
     *
     * @param node the node to check
     * @return true if node can have a callsite type
     */
    private static boolean canHaveCallSiteType(final Node node) {
        return node instanceof TypeOverride && ((TypeOverride<?>)node).canHaveCallSiteType();
    }

    /**
     * Is the specialization type supported. Currently we treat booleans as objects
     * and have no special boolean type accessor, thus booleans are ignored.
     * TODO - support booleans? NASHORN-590
     *
     * @param castTo the type to check
     * @return true if call site type is supported
     */
    private static boolean isSupportedCallSiteType(final Type castTo) {
        return castTo.isNumeric(); // don't specializable for boolean
    }

    /**
     * Override the type of a node for e.g. access specialization of scope
     * objects. Normally a variable can only get a wider type and narrower type
     * sets are ignored. Not that a variable can still be on object type as
     * per the type analysis, but a specific access may be narrower, e.g. if it
     * is used in an arithmetic op. This overrides a type, regardless of
     * type environment and is used primarily by the access specializer
     *
     * @param node    node for which to change type
     * @param to      new type
     */
    @SuppressWarnings("unchecked")
    private static <T extends Node> T setTypeOverride(final T node, final Type to) {
        final Type from = node.getType();
        if (!node.getType().equals(to)) {
            LOG.info("Changing call override type for '" + node + "' from " + node.getType() + " to " + to);
            if (!to.isObject() && from.isObject()) {
                setCanBePrimitive(node, to);
            }
        }
        LOG.info("Type override for lhs in '" + node + "' => " + to);
        return ((TypeOverride<T>)node).setType(to);
    }

    /**
     * Add an explicit conversion. This is needed when attribution has created types
     * that do not mesh into an op type, e.g. a = b, where b is object and a is double
     * at the end of Attr, needs explicit conversion logic.
     *
     * An explicit conversion can be one of the following:
     *   + Convert a literal - just replace it with another literal
     *   + Convert a scope object - just replace the type of the access, e.g. get()D->get()I
     *   + Explicit convert placement, e.g. a = (double)b - all other cases
     *
     * No other part of the world after {@link Attr} may introduce new symbols. This
     * is the only place.
     *
     * @param node node to convert
     * @param to   destination type
     * @return     conversion node
     */
    private Node convert(final Node node, final Type to) {
        assert !to.isUnknown() : "unknown type for " + node + " class=" + node.getClass();
        assert node != null : "node is null";
        assert node.getSymbol() != null : "node " + node + " has no symbol!";
        assert node.tokenType() != TokenType.CONVERT : "assert convert in convert " + node + " in " + getCurrentFunctionNode();

        final Type from = node.getType();

        if (Type.areEquivalent(from, to)) {
            return node;
        }

        if (from.isObject() && to.isObject()) {
            return node;
        }

        Node resultNode = node;

        if (node instanceof LiteralNode && !to.isObject()) {
            final LiteralNode<?> newNode = new LiteralNodeConstantEvaluator((LiteralNode<?>)node, to).eval();
            if (newNode != null) {
                resultNode = newNode;
            }
        } else {
            if (canHaveCallSiteType(node) && isSupportedCallSiteType(to)) {
                assert node instanceof TypeOverride;
                return setTypeOverride(node, to);
            }
            resultNode = new UnaryNode(node.getSource(), Token.recast(node.getToken(), TokenType.CONVERT), node);
        }

        LOG.info("CONVERT('" + node + "', " + to + ") => '" + resultNode + "'");

        //This is the only place in this file that can create new temporaries
        //FinalizeTypes may not introduce ANY node that is not a conversion.
        getCurrentFunctionNode().newTemporary(getCurrentBlock().getFrame(), to, resultNode);
        resultNode.copyTerminalFlags(node);

        return resultNode;
    }

    private static Node discard(final Node node) {
        node.setDiscard(true);

        if (node.getSymbol() != null) {
            final Node discard = new UnaryNode(node.getSource(), Token.recast(node.getToken(), TokenType.DISCARD), node);
            //discard never has a symbol in the discard node - then it would be a nop
            discard.copyTerminalFlags(node);
            return discard;
        }

        // node has no result (symbol) so we can keep it the way it is
        return node;
    }

    /**
     * Whenever an expression like an addition or an assignment changes type, it
     * may be that case that {@link Attr} created a symbol for an intermediate
     * result of the expression, say for an addition. This also has to be updated
     * if the expression type changes.
     *
     * Assignments use their lhs as node symbol, and in this case we can't modify
     * it. Then {@link CodeGenerator#Store} needs to do an explicit conversion.
     * This is happens very rarely.
     *
     * @param node
     * @param to
     */
    private static void propagateType(final Node node, final Type to) {
        final Symbol symbol = node.getSymbol();
        if (symbol.isTemp()) {
            symbol.setTypeOverride(to);
            LOG.info("Type override for temporary in '" + node + "' => " + to);
        }
    }

    /**
     * Determine if the outcome of + operator is a string.
     *
     * @param node  Node to test.
     * @return true if a string result.
     */
    private boolean isAddString(final Node node) {
        if (node instanceof BinaryNode && node.isTokenType(TokenType.ADD)) {
            final BinaryNode binaryNode = (BinaryNode)node;
            final Node lhs = binaryNode.lhs();
            final Node rhs = binaryNode.rhs();

            return isAddString(lhs) || isAddString(rhs);
        }

        return node instanceof LiteralNode<?> && ((LiteralNode<?>)node).isString();
    }

    /**
     * Whenever an explicit conversion is needed and the convertee is a literal, we can
     * just change the literal
     */
    static class LiteralNodeConstantEvaluator extends FoldConstants.ConstantEvaluator<LiteralNode<?>> {
        private final Type type;

        LiteralNodeConstantEvaluator(final LiteralNode<?> parent, final Type type) {
            super(parent);
            this.type = type;
        }

        @Override
        protected LiteralNode<?> eval() {
            final Object value = ((LiteralNode<?>)parent).getValue();

            LiteralNode<?> literalNode = null;

            if (type.isString()) {
                literalNode = LiteralNode.newInstance(source, token, finish, JSType.toString(value));
            } else if (type.isBoolean()) {
                literalNode = LiteralNode.newInstance(source, token, finish, JSType.toBoolean(value));
            } else if (type.isInteger()) {
                literalNode = LiteralNode.newInstance(source, token, finish, JSType.toInt32(value));
            } else if (type.isLong()) {
                literalNode = LiteralNode.newInstance(source, token, finish, JSType.toLong(value));
            } else if (type.isNumber() || parent.getType().isNumeric() && !parent.getType().isNumber()) {
                literalNode = LiteralNode.newInstance(source, token, finish, JSType.toNumber(value));
            }

            if (literalNode != null) {
                //inherit literal symbol for attr.
                literalNode.setSymbol(parent.getSymbol());
            }

            return literalNode;
        }
    }
}
