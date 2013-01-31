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

package jdk.nashorn.internal.ir;

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.options.Options;

/**
 * Maps a name to specific data.
 */

public final class Symbol implements Comparable<Symbol> {
    /** Symbol flags. Kind ordered by precedence. */
    public static final int IS_TEMP     = 0b0000_0001;
    /** Is this Global */
    public static final int IS_GLOBAL   = 0b0000_0010;
    /** Is this a variable */
    public static final int IS_VAR      = 0b0000_0011;
    /** Is this a parameter */
    public static final int IS_PARAM    = 0b0000_0100;
    /** Is this a constant */
    public static final int IS_CONSTANT = 0b0000_0101;

    static final int KINDMASK = 0b0000_1111;

    /** Is this scope */
    public static final int IS_SCOPE         = 0b0000_0001_0000;
    /** Is this a this symbol */
    public static final int IS_THIS          = 0b0000_0010_0000;
    /** Can this symbol ever be undefined */
    public static final int CAN_BE_UNDEFINED = 0b0000_0100_0000;
    /** Can this symbol ever have primitive types */
    public static final int CAN_BE_PRIMITIVE = 0b0000_1000_0000;
    /** Is this a let */
    public static final int IS_LET           = 0b0001_0000_0000;
    /** Is this an internal symbol, never represented explicitly in source code */
    public static final int IS_INTERNAL      = 0b0010_0000_0000;

    /** Null or name identifying symbol. */
    private final String name;

    /** Symbol flags. */
    private int flags;

    /** Defining node. */
    private Node node;

    /** Definition block. */
    private final Block block;

    /** Type of symbol. */
    private Type type;

    /** Local variable slot. -1 indicates external property. */
    private int slot;

    /** Field number in scope or property; array index in varargs when not using arguments object. */
    private int fieldIndex;

    /** Number of times this symbol is used in code */
    private int useCount;

    /** Debugging option - dump info and stack trace when symbols with given names are manipulated */
    private static final Set<String> TRACE_SYMBOLS;
    private static final Set<String> TRACE_SYMBOLS_STACKTRACE;

    static {
        final String stacktrace = Options.getStringProperty("nashorn.compiler.symbol.stacktrace", null);
        final String trace;
        if (stacktrace != null) {
            trace = stacktrace; //stacktrace always implies trace as well
            TRACE_SYMBOLS_STACKTRACE = new HashSet<>();
            for (StringTokenizer st = new StringTokenizer(stacktrace, ","); st.hasMoreTokens(); ) {
                TRACE_SYMBOLS_STACKTRACE.add(st.nextToken());
            }
        } else {
            trace = Options.getStringProperty("nashorn.compiler.symbol.trace", null);
            TRACE_SYMBOLS_STACKTRACE = null;
        }

        if (trace != null) {
            TRACE_SYMBOLS = new HashSet<>();
            for (StringTokenizer st = new StringTokenizer(trace, ","); st.hasMoreTokens(); ) {
                TRACE_SYMBOLS.add(st.nextToken());
            }
        } else {
            TRACE_SYMBOLS = null;
        }
    }

    /**
     * Constructor
     *
     * @param name  name of symbol
     * @param flags symbol flags
     * @param node  node this symbol is in
     * @param block block this symbol is in
     * @param type  type of this symbol
     * @param slot  bytecode slot for this symbol
     */
    protected Symbol(final String name, final int flags, final Node node, final Block block, final Type type, final int slot) {
        this.name       = name;
        this.flags      = flags;
        this.node       = node;
        this.block      = block;
        this.type       = type;
        this.slot       = slot;
        this.fieldIndex = -1;
        trace("CREATE SYMBOL");
    }

    /**
     * Constructor
     *
     * @param name  name of symbol
     * @param flags symbol flags
     * @param node  node this symbol is in
     * @param block block this symbol is in
     */
    public Symbol(final String name, final int flags, final Node node, final Block block) {
        this(name, flags, node, block, Type.UNKNOWN, -1);
    }

    /**
     * Constructor
     *
     * @param name  name of symbol
     * @param flags symbol flags
     * @param type  type of this symbol
     */
    public Symbol(final String name, final int flags, final Type type) {
        this(name, flags, null, null, type, -1);
    }

    private static String align(final String string, final int max) {
        final StringBuilder sb = new StringBuilder();
        sb.append(string.substring(0, Math.min(string.length(), max)));

        while (sb.length() < max) {
            sb.append(' ');
        }
        return sb.toString();
    }

    /**
     * Return the type for this symbol. Normally, if there is no type override,
     * this is where any type for any node is stored. If the node has a TypeOverride,
     * it may override this, e.g. when asking for a scoped field as a double
     *
     * @return symbol type
     */
    public final Type getSymbolType() {
        return type;
    }

    /**
     * Debugging .
     *
     * @param stream Stream to print to.
     */

    void print(final PrintWriter stream) {
        final String printName = align(name, 20);
        final String printType = align(type.toString(), 10);
        final String printSlot = align(slot == -1 ? "none" : "" + slot, 10);
        String printFlags = "";

        switch (flags & KINDMASK) {
        case IS_TEMP:
            printFlags = "temp " + printFlags;
            break;
        case IS_GLOBAL:
            printFlags = "global " + printFlags;
            break;
        case IS_VAR:
            printFlags = "var " + printFlags;
            break;
        case IS_PARAM:
            printFlags = "param " + printFlags;
            break;
        case IS_CONSTANT:
            printFlags = "CONSTANT " + printFlags;
            break;
        default:
            break;
        }

        if (isScope()) {
            printFlags += "scope ";
        }

        if (isInternal()) {
            printFlags += "internal ";
        }

        if (isLet()) {
            printFlags += "let ";
        }

        if (isThis()) {
            printFlags += "this ";
        }

        if (!canBeUndefined()) {
            printFlags += "always_def ";
        }

        if (canBePrimitive()) {
            printFlags += "can_be_prim ";
        }

        stream.print(printName + ": " + printType + ", " + printSlot + ", " + printFlags);
        stream.println();
    }

    /**
     * Compare the the symbol kind with another.
     *
     * @param other Other symbol's flags.
     * @return True if symbol has less kind.
     */
    public boolean less(final int other) {
        return (flags & KINDMASK) < (other & KINDMASK);
    }

    /**
     * Allocate a slot for this symbol.
     *
     * @param needsSlot True if symbol needs a slot.
     */
    public void setNeedsSlot(final boolean needsSlot) {
        setSlot(needsSlot ? 0 : -1);
    }

    /**
     * Return the number of slots required for the symbol.
     *
     * @return Number of slots.
     */
    public int slotCount() {
        return type.isCategory2() ? 2 : 1;
    }

    /**
     * Return the defining function (scope.)
     *
     * @return Defining function.
     */
    public FunctionNode findFunction() {
        return block != null ? block.getFunction() : null;
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof Symbol)) {
            return false;
        }
        final Symbol symbol = (Symbol) other;
        return name.equals(symbol.name) && block.equals(symbol.block);
    }

    @Override
    public int hashCode() {
        return name.hashCode() ^ block.hashCode();
    }

    private static String type(final String desc) {
        switch (desc.charAt(desc.length() - 1)) {
        case ';':
            return desc;//"obj";
        case 'D':
            return "double";
        case 'I':
            return "int";
        case 'J':
            return "long";
        case 'Z':
            return "boolean";
        default:
            return "UNKNOWN";
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb   = new StringBuilder();
        final String        desc = getSymbolType().getDescriptor();

        sb.append(name).
            append(' ').
            append('(').
            append(type(desc)).
            append(')');

        if (hasSlot()) {
            sb.append(' ').
                append('(').
                append("slot=").
                append(slot).
                append(')');
        }

        if (isScope()) {
            if(isGlobal()) {
                sb.append(" G");
            } else {
                sb.append(" S");
            }
        }

        if (canBePrimitive()) {
            sb.append(" P?");
        }

        return sb.toString();
    }

    @Override
    public int compareTo(final Symbol other) {
        return name.compareTo(other.name);
    }

    /**
     * Does this symbol have an allocated bytecode slot. If not, it is scope
     * and must be loaded from memory upon access
     *
     * @return true if this symbol has a local bytecode slot
     */
    public boolean hasSlot() {
        return slot >= 0;
    }

    /**
     * Check if this is a temporary symbol
     * @return true if temporary
     */
    public boolean isTemp() {
        return (flags & KINDMASK) == IS_TEMP;
    }

    /**
     * Check if this is a symbol in scope. Scope symbols cannot, for obvious reasons
     * be stored in byte code slots on the local frame
     *
     * @return true if this is scoped
     */
    public boolean isScope() {
        assert ((flags & KINDMASK) != IS_GLOBAL) || ((flags & IS_SCOPE) == IS_SCOPE) : "global without scope flag";
        return (flags & IS_SCOPE) == IS_SCOPE;
    }

    /**
     * Flag this symbol as scope as described in {@link Symbol#isScope()}
     */
    public void setIsScope() {
        if (!isScope()) {
            trace("SET IS SCOPE");
        }
        flags |= IS_SCOPE;
        if(!isGlobal()) {
            getBlock().setNeedsScope();
        }
    }

    /**
     * Check if this symbol is a variable
     * @return true if variable
     */
    public boolean isVar() {
        return (flags & KINDMASK) == IS_VAR;
    }

    /**
     * Check if this symbol is a global (undeclared) variable
     * @return true if global
     */
    public boolean isGlobal() {
        return (flags & KINDMASK) == IS_GLOBAL;
    }

    /**
     * Check if this symbol is a function parameter
     * @return true if parameter
     */
    public boolean isParam() {
        return (flags & KINDMASK) == IS_PARAM;
    }

    /**
     * Check whether this symbol ever has primitive assignments. Conservative
     * @return true if primitive assignments exist
     */
    public boolean canBePrimitive() {
        return (flags & CAN_BE_PRIMITIVE) == CAN_BE_PRIMITIVE;
    }

    /**
     * Check if this symbol can ever be undefined
     * @return true if can be undefined
     */
    public boolean canBeUndefined() {
        return (flags & CAN_BE_UNDEFINED) == CAN_BE_UNDEFINED;
    }

    /**
     * Flag this symbol as potentially undefined in parts of the program
     */
    public void setCanBeUndefined() {
        assert type.isObject() : type;
        flags |= CAN_BE_UNDEFINED;
    }

    /**
     * Flag this symbol as potentially primitive
     * @param type the primitive type it occurs with, currently unused but can be used for width guesses
     */
    public void setCanBePrimitive(final Type type) {
        flags |= CAN_BE_PRIMITIVE;
    }

    /**
     * Check if this symbol is a constant
     * @return true if a constant
     */
    public boolean isConstant() {
        return (flags & KINDMASK) == IS_CONSTANT;
    }

    /**
     * Check if this is an internal symbol, without an explicit JavaScript source
     * code equivalent
     * @return true if internal
     */
    public boolean isInternal() {
        return (flags & IS_INTERNAL) != 0;
    }

    /**
     * Check if this symbol represents {@code this}
     * @return true if this
     */
    public boolean isThis() {
        return (flags & IS_THIS) != 0;
    }

    /**
     * Check if this symbol is a let
     * @return true if let
     */
    public boolean isLet() {
        return (flags & IS_LET) == IS_LET;
    }

    /**
     * Flag this symbol as a let
     */
    public void setIsLet() {
        flags |= IS_LET;
    }

    /**
     * Check if this symbol can be accessed directly with a putfield or getfield or dynamic load
     *
     * @param currentFunction function to check for fast scope
     * @return true if fast scope
     */
    public boolean isFastScope(final FunctionNode currentFunction) {
        if (!isScope() || !block.needsScope()) {
            return false;
        }
        // Allow fast scope access if no parent function contains with or eval
        FunctionNode func = currentFunction;
        while (func != null) {
            if (func.hasWith() || func.hasEval()) {
                return false;
            }
            func = func.findParentFunction();
        }
        return true;
    }

    /**
     * Get the block in which the symbol is defined
     * @return a block
     */
    public Block getBlock() {
        return block;
    }

    /**
     * Get the index of the field used to store this symbol, should it be an AccessorProperty
     * and get allocated in a JO$-prefixed ScriptObject subclass.
     *
     * @return field index
     */
    public int getFieldIndex() {
        assert fieldIndex != -1 : "fieldIndex must be initialized";
        return fieldIndex;
    }

    /**
     * Set the index of the field used to store this symbol, should it be an AccessorProperty
     * and get allocated in a JO$-prefixed ScriptObject subclass.
     *
     * @param fieldIndex field index - a positive integer
     */
    public void setFieldIndex(final int fieldIndex) {
        assert this.fieldIndex == -1 : "fieldIndex must be initialized only once";
        this.fieldIndex = fieldIndex;
    }

    /**
     * Get the symbol flags
     * @return flags
     */
    public int getFlags() {
        return flags;
    }

    /**
     * Set the symbol flags
     * @param flags flags
     */
    public void setFlags(final int flags) {
        this.flags = flags;
    }

    /**
     * Get the node this symbol stores the result for
     * @return node
     */
    public Node getNode() {
        return node;
    }

    /**
     * Set the node this symbol stores the result for
     * @param node node
     */
    public void setNode(final Node node) {
        this.node = node;
    }

    /**
     * Get the name of this symbol
     * @return symbol name
     */
    public String getName() {
        return name;
    }

    /**
     * Get the byte code slot for this symbol
     * @return byte code slot, or -1 if no slot allocated/possible
     */
    public int getSlot() {
        return slot;
    }

    /**
     * Increase the symbol's use count by one.
     */
    public void increaseUseCount() {
        useCount++;
    }

    /**
     * Get the symbol's use count
     * @return the number of times the symbol is used in code.
     */
    public int getUseCount() {
        return useCount;
    }

    /**
     * Set the bytecode slot for this symbol
     * @param slot valid bytecode slot, or -1 if not available
     */
    public void setSlot(final int slot) {
        if (slot != this.slot) {
            trace("SET SLOT " + slot);
            this.slot = slot;
        }
    }

    /**
     * Assign a specific subclass of Object to the symbol
     *
     * @param type  the type
     */
    public void setType(final Class<?> type) {
        assert !type.isPrimitive() && !Number.class.isAssignableFrom(type) : "Class<?> types can only be subclasses of object";
        setType(Type.typeFor(type));
    }

    /**
     * Assign a type to the symbol
     *
     * @param type the type
     */
    public void setType(final Type type) {
        setTypeOverride(Type.widest(this.type, type));
    }

    /**
     * Only use this if you know about an existing type
     * constraint - otherwise a type can only be
     * widened
     *
     * @param type  the type
     */
    public void setTypeOverride(final Type type) {
        final Type old = this.type;
        if (old != type) {
            trace("TYPE CHANGE: " + old + "=>" + type + " == " + type);
            this.type = type;
        }
    }

    /**
     * Check if this symbol is in the global scope, i.e. it is on the outermost level
     * in the script
     * @return true if this this is a global scope symbol
     */
    public boolean isTopLevel() {
        return block instanceof FunctionNode && ((FunctionNode) block).isScript();
    }


    private void trace(final String desc) {
        if (TRACE_SYMBOLS != null && (TRACE_SYMBOLS.isEmpty() || TRACE_SYMBOLS.contains(name))) {
            Context.err("SYMBOL: '" + name + "' " + desc);
            if (TRACE_SYMBOLS_STACKTRACE != null && (TRACE_SYMBOLS_STACKTRACE.isEmpty() || TRACE_SYMBOLS_STACKTRACE.contains(name))) {
                new Throwable().printStackTrace(Context.getContext().getErr());
            }
        }
    }
}
