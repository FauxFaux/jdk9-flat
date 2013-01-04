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

import static jdk.nashorn.internal.codegen.CompilerConstants.CALLEE;
import static jdk.nashorn.internal.codegen.CompilerConstants.THIS;

import java.util.List;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.linker.LinkerCallSite;

/**
 * Class that generates function signatures for dynamic calls
 */
public final class FunctionSignature {

    /** parameter types that ASM can understand */
    private final Type[] paramTypes;

    /** return type that ASM can understand */
    private final Type returnType;

    /** valid Java descriptor string for function */
    private final String descriptor;

    /**
     * Constructor
     *
     * Create a FunctionSignature given arguments as AST Nodes
     *
     * @param hasSelf does the function have a self slot?
     * @param retType what is the return type
     * @param args    argument list of AST Nodes
     */
    public FunctionSignature(final boolean hasSelf, final Type retType, final List<? extends Node> args) {
        this(hasSelf, false, retType, FunctionSignature.typeArray(args));
    }

    /**
     * Constructor
     *
     * Create a FunctionSignature given arguments as AST Nodes
     *
     * @param hasSelf   does the function have a self slot?
     * @param hasCallee does the function need a callee variable
     * @param retType   what is the return type
     * @param args      argument list of AST Nodes
     */
    public FunctionSignature(final boolean hasSelf, final boolean hasCallee, final Type retType, final List<? extends Node> args) {
        this(hasSelf, hasCallee, retType, FunctionSignature.typeArray(args));
    }

    /**
     * Constructor
     *
     * Create a FunctionSignature given arguments as AST Nodes
     *
     * @param hasSelf does the function have a self slot?
     * @param retType what is the return type
     * @param nArgs   number of arguments
     */
    public FunctionSignature(final boolean hasSelf, final Type retType, final int nArgs) {
        this(hasSelf, false, retType, FunctionSignature.objectArgs(nArgs));
    }

    /**
     * Constructor
     *
     * Create a FunctionSignature given argument types only
     *
     * @param hasSelf   does the function have a self slot?
     * @param hasCallee does the function have a callee slot?
     * @param retType   what is the return type
     * @param argTypes  argument list of AST Nodes
     */
    public FunctionSignature(final boolean hasSelf, final boolean hasCallee, final Type retType, final Type... argTypes) {
        final boolean isVarArg;

        int count = 1;

        if (argTypes == null) {
            isVarArg = true;
        } else {
            isVarArg = argTypes.length > LinkerCallSite.ARGLIMIT;
            count    = isVarArg ? 1 : argTypes.length;
        }

        int first = 0;

        if (hasSelf) {
            count++;
            first++;
        }
        if (hasCallee) {
            count++;
            first++;
        }

        paramTypes = new Type[count];

        if (hasSelf) {
            paramTypes[THIS.slot()] = Type.OBJECT;
        }
        if (hasCallee) {
            paramTypes[CALLEE.slot()] = Type.typeFor(ScriptFunction.class);
        }

        if (isVarArg) {
            paramTypes[first] = Type.OBJECT_ARRAY;
        } else if (argTypes != null) {
            for (int i = first, j = 0; i < count; i++, j++) {
                paramTypes[i] = argTypes[j];
                if (paramTypes[i].isObject()) {
                    paramTypes[i] = Type.OBJECT; //TODO: for now, turn java/lang/String into java/lang/Object as we aren't as specific.
                }
            }
        } else {
            assert false : "isVarArgs cannot be false when argTypes are null";
        }

        returnType = retType;
        descriptor = Type.getMethodDescriptor(returnType, paramTypes);
    }

    /**
     * Internal function that converts an array of nodes to their Types
     *
     * @param args node arg list
     *
     * @return the array of types
     */
    private static Type[] typeArray(final List<? extends Node> args) {
        if (args == null) {
            return null;
        }

        final Type[] typeArray = new Type[args.size()];

        int pos = 0;
        for (final Node arg : args) {
            typeArray[pos++] = arg.getType();
        }

        return typeArray;
    }

    @Override
    public String toString() {
        return descriptor;
    }

    /**
     * @return the number of param types
     */
    public int size() {
        return paramTypes.length;
    }

    /**
     * Returns the generic signature of the function being compiled.
     *
     * @param functionNode function being compiled.
     * @return function signature.
     */
    public static String functionSignature(final FunctionNode functionNode) {
        return new FunctionSignature(
            true,
            functionNode.needsCallee(),
            functionNode.getReturnType(),
            (functionNode.isVarArg() && !functionNode.isScript()) ?
                null :
                functionNode.getParameters()).toString();
    }

    private static Type[] objectArgs(final int nArgs) {
        final Type[] array = new Type[nArgs];
        for (int i = 0; i < nArgs; i++) {
            array[i] = Type.OBJECT;
        }
        return array;
    }

}
