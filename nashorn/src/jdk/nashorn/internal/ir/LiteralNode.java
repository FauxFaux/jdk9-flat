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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jdk.nashorn.internal.codegen.CompileUnit;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.parser.Lexer.LexerToken;
import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.parser.TokenType;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.Source;
import jdk.nashorn.internal.runtime.Undefined;

/**
 * Literal nodes represent JavaScript values.
 *
 * @param <T> the literal type
 */
public abstract class LiteralNode<T> extends Node implements PropertyKey {
    /** Literal value */
    protected T value;

     /**
     * Constructor
     *
     * @param source  the source
     * @param token   token
     * @param finish  finish
     * @param value   the value of the literal
     */
    protected LiteralNode(final Source source, final long token, final int finish, final T value) {
        super(source, token, finish);
        this.value = value;
    }

    /**
     * Copy constructor
     *
     * @param literalNode source node
     */
    protected LiteralNode(final LiteralNode<T> literalNode) {
        super(literalNode);
        this.value = literalNode.value;
    }

    @Override
    public boolean isAtom() {
        return true;
    }

    /**
     * Check if the literal value is null
     * @return true if literal value is null
     */
    public boolean isNull() {
        return value == null;
    }

    @Override
    public int hashCode() {
        return value == null ? 0 : value.hashCode();
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof LiteralNode<?>)) {
            return false;
        }
        final LiteralNode<?> otherNode = (LiteralNode<?>)other;
        if (otherNode.isNull()) {
            return isNull();
        }
        return ((LiteralNode<?>)other).getValue().equals(value);
    }

    /**
     * Check if the literal value is boolean true
     * @return true if literal value is boolean true
     */
    public boolean isTrue() {
        return JSType.toBoolean(value);
    }

    @Override
    public Type getType() {
        return Type.typeFor(value.getClass());
    }

    @Override
    public String getPropertyName() {
        return JSType.toString(getObject());
    }

    /**
     * Fetch boolean value of node.
     *
     * @return boolean value of node.
     */
    public boolean getBoolean() {
        return JSType.toBoolean(value);
    }

    /**
     * Fetch int32 value of node.
     *
     * @return Int32 value of node.
     */
    public int getInt32() {
        return JSType.toInt32(value);
    }

    /**
     * Fetch uint32 value of node.
     *
     * @return uint32 value of node.
     */
    public long getUint32() {
        return JSType.toUint32(value);
    }

    /**
     * Fetch long value of node
     *
     * @return long value of node
     */
    public long getLong() {
        return JSType.toLong(value);
    }

    /**
     * Fetch double value of node.
     *
     * @return double value of node.
     */
    public double getNumber() {
        return JSType.toNumber(value);
    }

    /**
     * Get the array value of the node
     *
     * @return the array value
     */
    public Node[] getArray() {
        assert false : "not an array node";
        return null;
    }

    /**
     * Fetch String value of node.
     *
     * @return String value of node.
     */
    public String getString() {
        return JSType.toString(value);
    }

    /**
     * Fetch Object value of node.
     *
     * @return Object value of node.
     */
    public Object getObject() {
        return value;
    }

    /**
     * Test if the value is a string.
     *
     * @return True if value is a string.
     */
    public boolean isString() {
        return value instanceof String;
    }

    /**
     * Test if tha value is a number
     *
     * @return True if value is a number
     */
    public boolean isNumeric() {
        return value instanceof Number;
    }

    /**
     * Assist in IR navigation.
     *
     * @param visitor IR navigating visitor.
     */
    @Override
    public Node accept(final NodeVisitor visitor) {
        if (visitor.enter(this) != null) {
            return visitor.leave(this);
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else {
            sb.append(value.toString());
        }
    }

    /**
     * Get the literal node value
     * @return the value
     */
    public T getValue() {
        return value;
    }

    /**
     * Create a new null literal
     *
     * @param source  the source
     * @param token   token
     * @param finish  finish
     *
     * @return the new literal node
     */
    public static LiteralNode<Node> newInstance(final Source source, final long token, final int finish) {
        return new NodeLiteralNode(source, token, finish);
    }

    /**
     * Create a new null literal based on a parent node (source, token, finish)
     *
     * @param parent parent node
     *
     * @return the new literal node
     */
    public static LiteralNode<?> newInstance(final Node parent) {
        return new NodeLiteralNode(parent.getSource(), parent.getToken(), parent.getFinish());
    }

    private static class BooleanLiteralNode extends LiteralNode<Boolean> {

        private BooleanLiteralNode(final Source source, final long token, final int finish, final boolean value) {
            super(source, Token.recast(token, value ? TokenType.TRUE : TokenType.FALSE), finish, value);
        }

        private BooleanLiteralNode(final BooleanLiteralNode literalNode) {
            super(literalNode);
        }

        @Override
        protected Node copy(final CopyState cs) {
            return new BooleanLiteralNode(this);
        }

        @Override
        public boolean isTrue() {
            return value;
        }

        @Override
        public Type getType() {
            return Type.BOOLEAN;
        }

        @Override
        public Type getWidestOperationType() {
            return Type.BOOLEAN;
        }
    }

    /**
     * Create a new boolean literal
     *
     * @param source  the source
     * @param token   token
     * @param finish  finish
     * @param value   true or false
     *
     * @return the new literal node
     */
    public static LiteralNode<Boolean> newInstance(final Source source, final long token, final int finish, final boolean value) {
        return new BooleanLiteralNode(source, token,  finish, value);
    }

    /**
     * Create a new boolean literal based on a parent node (source, token, finish)
     *
     * @param parent parent node
     * @param value  true or false
     *
     * @return the new literal node
     */
    public static LiteralNode<?> newInstance(final Node parent, final boolean value) {
        return new BooleanLiteralNode(parent.getSource(), parent.getToken(), parent.getFinish(), value);
    }

    private static class NumberLiteralNode extends LiteralNode<Number> {

        private final Type type = numberGetType(value);

        private NumberLiteralNode(final Source source, final long token, final int finish, final Number value) {
            super(source, Token.recast(token, TokenType.DECIMAL), finish, value);
        }

        private NumberLiteralNode(final NumberLiteralNode literalNode) {
            super(literalNode);
        }

        private static Type numberGetType(final Number number) {
            if (number instanceof Integer) {
                return Type.INT;
            } else if (number instanceof Long) {
                return Type.LONG;
            } else if (number instanceof Double) {
                return Type.NUMBER;
            } else {
                assert false;
            }

            return null;
        }

        @Override
        protected Node copy(final CopyState cs) {
            return new NumberLiteralNode(this);
        }

        @Override
        public Type getType() {
            return type;
        }

        @Override
        public Type getWidestOperationType() {
            return getType();
        }

    }
    /**
     * Create a new number literal
     *
     * @param source  the source
     * @param token   token
     * @param finish  finish
     * @param value   literal value
     *
     * @return the new literal node
     */
    public static LiteralNode<Number> newInstance(final Source source, final long token, final int finish, final Number value) {
        return new NumberLiteralNode(source, token, finish, value);
    }

    /**
     * Create a new number literal based on a parent node (source, token, finish)
     *
     * @param parent parent node
     * @param value  literal value
     *
     * @return the new literal node
     */
    public static LiteralNode<?> newInstance(final Node parent, final Number value) {
        return new NumberLiteralNode(parent.getSource(), parent.getToken(), parent.getFinish(), value);
    }

    private static class UndefinedLiteralNode extends LiteralNode<Undefined> {
        private UndefinedLiteralNode(final Source source, final long token, final int finish) {
            super(source, Token.recast(token, TokenType.OBJECT), finish, ScriptRuntime.UNDEFINED);
        }

        private UndefinedLiteralNode(final UndefinedLiteralNode literalNode) {
            super(literalNode);
        }

        @Override
        protected Node copy(final CopyState cs) {
            return new UndefinedLiteralNode(this);
        }
    }

    /**
     * Create a new undefined literal
     *
     * @param source  the source
     * @param token   token
     * @param finish  finish
     * @param value   undefined value, passed only for polymorphisism discrimination
     *
     * @return the new literal node
     */
    public static LiteralNode<Undefined> newInstance(final Source source, final long token, final int finish, final Undefined value) {
        return new UndefinedLiteralNode(source, token, finish);
    }

    /**
     * Create a new null literal based on a parent node (source, token, finish)
     *
     * @param parent parent node
     * @param value  undefined value
     *
     * @return the new literal node
     */
    public static LiteralNode<?> newInstance(final Node parent, final Undefined value) {
        return new UndefinedLiteralNode(parent.getSource(), parent.getToken(), parent.getFinish());
    }

    private static class StringLiteralNode extends LiteralNode<String> {
        private StringLiteralNode(final Source source, final long token, final int finish, final String value) {
            super(source, Token.recast(token, TokenType.STRING), finish, value);
        }

        private StringLiteralNode(final StringLiteralNode literalNode) {
            super(literalNode);
        }

        @Override
        protected Node copy(final CopyState cs) {
            return new StringLiteralNode(this);
        }

        @Override
        public void toString(final StringBuilder sb) {
            sb.append('\"');
            sb.append(value);
            sb.append('\"');
        }
    }

    /**
     * Create a new string literal
     *
     * @param source  the source
     * @param token   token
     * @param finish  finish
     * @param value   string value
     *
     * @return the new literal node
     */
    public static LiteralNode<String> newInstance(final Source source, final long token, final int finish, final String value) {
        return new StringLiteralNode(source, token, finish, value);
    }

    /**
     * Create a new String literal based on a parent node (source, token, finish)
     *
     * @param parent parent node
     * @param value  string value
     *
     * @return the new literal node
     */
    public static LiteralNode<?> newInstance(final Node parent, final String value) {
        return new StringLiteralNode(parent.getSource(), parent.getToken(), parent.getFinish(), value);
    }

    private static class LexerTokenLiteralNode extends LiteralNode<LexerToken> {
        private LexerTokenLiteralNode(final Source source, final long token, final int finish, final LexerToken value) {
            super(source, Token.recast(token, TokenType.STRING), finish, value); //TODO is string the correct token type here?
        }

        private LexerTokenLiteralNode(final LexerTokenLiteralNode literalNode) {
            super(literalNode);
        }

        @Override
        protected Node copy(final CopyState cs) {
            return new LexerTokenLiteralNode(this);
        }

        @Override
        public void toString(final StringBuilder sb) {
            sb.append(value.toString());
        }
    }

    /**
     * Create a new literal node for a lexer token
     *
     * @param source  the source
     * @param token   token
     * @param finish  finish
     * @param value   lexer token value
     *
     * @return the new literal node
     */
    public static LiteralNode<LexerToken> newInstance(final Source source, final long token, final int finish, final LexerToken value) {
        return new LexerTokenLiteralNode(source, token, finish, value);
    }

    /**
     * Create a new lexer token literal based on a parent node (source, token, finish)
     *
     * @param parent parent node
     * @param value  lexer token
     *
     * @return the new literal node
     */
    public static LiteralNode<?> newInstance(final Node parent, final LexerToken value) {
        return new LexerTokenLiteralNode(parent.getSource(), parent.getToken(), parent.getFinish(), value);
    }

    private static class NodeLiteralNode extends LiteralNode<Node> {

        private NodeLiteralNode(final Source source, final long token, final int finish) {
            this(source, token, finish, null);
        }

        private NodeLiteralNode(final Source source, final long token, final int finish, final Node value) {
            super(source, Token.recast(token, TokenType.OBJECT), finish, value);
        }

        private NodeLiteralNode(final LiteralNode<Node> literalNode) {
            super(literalNode);
        }

        @Override
        protected Node copy(final CopyState cs) {
            return new NodeLiteralNode(this);
        }

        @Override
        public Node accept(final NodeVisitor visitor) {
            if (visitor.enter(this) != null) {
                if (value != null) {
                    value = value.accept(visitor);
                }
                return visitor.leave(this);
            }

            return this;
        }

        @Override
        public Type getType() {
            return value == null ? Type.OBJECT : super.getType();
        }

        @Override
        public Type getWidestOperationType() {
            return value == null ? Type.OBJECT : value.getWidestOperationType();
        }

    }
    /**
     * Create a new node literal for an arbitrary node
     *
     * @param source  the source
     * @param token   token
     * @param finish  finish
     * @param value   the literal value node
     *
     * @return the new literal node
     */
    public static LiteralNode<Node> newInstance(final Source source, final long token, final int finish, final Node value) {
        return new NodeLiteralNode(source, token, finish, value);
    }

    /**
     * Create a new node literal based on a parent node (source, token, finish)
     *
     * @param parent parent node
     * @param value  node value
     *
     * @return the new literal node
     */
    public static LiteralNode<?> newInstance(final Node parent, final Node value) {
        return new NodeLiteralNode(parent.getSource(), parent.getToken(), parent.getFinish(), value);
    }

    /**
     * Array literal node class.
     */
    public static class ArrayLiteralNode extends LiteralNode<Node[]> {
        private static class PostsetMarker {
            //empty
        }

        private static PostsetMarker POSTSET_MARKER = new PostsetMarker();

        /** Array element type. */
        private Type elementType;

        /** Preset constant array. */
        private Object presets;

        /** Indices of array elements requiring computed post sets. */
        private int[] postsets;

        private List<ArrayUnit> units;

        /**
         * An ArrayUnit is a range in an ArrayLiteral. ArrayLiterals can
         * be split if they are too large, for bytecode generation reasons
         */
        public static class ArrayUnit {
            /** Compile unit associated with the postsets range. */
            private final CompileUnit compileUnit;

            /** postsets range associated with the unit (hi not inclusive). */
            private final int lo, hi;

            /**
             * Constructor
             * @param compileUnit compile unit
             * @param lo lowest array index in unit
             * @param hi highest array index in unit + 1
             */
            public ArrayUnit(final CompileUnit compileUnit, final int lo, final int hi) {
                this.compileUnit = compileUnit;
                this.lo   = lo;
                this.hi   = hi;
            }

            /**
             * Get the high index position of the ArrayUnit (non inclusive)
             * @return high index position
             */
            public int getHi() {
                return hi;
            }

            /**
             * Get the low index position of the ArrayUnit (inclusive)
             * @return low index position
             */
            public int getLo() {
                return lo;
            }

            /**
             * The array compile unit
             * @return array compile unit
             */
            public CompileUnit getCompileUnit() {
                return compileUnit;
            }
        }

        /**
         * Constructor
         *
         * @param source  the source
         * @param token   token
         * @param finish  finish
         * @param value   array literal value, a Node array
         */
        protected ArrayLiteralNode(final Source source, final long token, final int finish, final Node[] value) {
            super(source, Token.recast(token, TokenType.ARRAY), finish, value);
            this.elementType = Type.UNKNOWN;
        }

        /**
         * Copy constructor
         * @param node source array literal node
         */
        protected ArrayLiteralNode(final ArrayLiteralNode node) {
            super(node);
            this.elementType = node.elementType;
        }

        @Override
        protected Node copy(final CopyState cs) {
            return new ArrayLiteralNode(this);
        }

        /**
         * Compute things like widest element type needed. Internal use from compiler only
         */
        public void analyze() {
            elementType = Type.INT;
            analyzeElements();

            if (elementType == Type.INT) {
                presetIntArray();
            } else if (elementType.isNumeric()) {
                presetNumberArray();
            } else {
                presetObjectArray();
            }
        }

        private void presetIntArray() {
            final int[] array = new int[value.length];
            final int[] computed = new int[value.length];
            int nComputed = 0;

            for (int i = 0; i < value.length; i++) {
                final Object element = objectAsConstant(value[i]);

                if (element instanceof Number) {
                    array[i] = ((Number)element).intValue();
                } else {
                    computed[nComputed++] = i;
                }
            }

            presets = array;
            postsets = Arrays.copyOf(computed, nComputed);
        }

        private void presetNumberArray() {
            final double[] array = new double[value.length];
            final int[] computed = new int[value.length];
            int nComputed = 0;

            for (int i = 0; i < value.length; i++) {
                final Object element = objectAsConstant(value[i]);

                if (element instanceof Number) {
                    array[i] = ((Number)element).doubleValue();
                } else {
                    computed[nComputed++] = i;
                }
            }

            presets = array;
            postsets = Arrays.copyOf(computed, nComputed);
        }

        private void presetObjectArray() {
            final Object[] array = new Object[value.length];
            final int[] computed = new int[value.length];
            int nComputed = 0;

            for (int i = 0; i < value.length; i++) {
                final Node node = value[i];

                if (node == null) {
                    computed[nComputed++] = i;
                } else {
                    final Object element = objectAsConstant(node);

                    if (element != POSTSET_MARKER) {
                        array[i] = element;
                    } else {
                        computed[nComputed++] = i;
                    }
                }
            }

            presets = array;
            postsets = Arrays.copyOf(computed, nComputed);
        }

        private void analyzeElements() {
            for (final Node node : value) {
                if (node == null) {
                    elementType = elementType.widest(Type.OBJECT); //no way to represent undefined as number
                    break;
                }

                final Symbol symbol = node.getSymbol();
                assert symbol != null; //don't run this on unresolved nodes or you are in trouble
                Type symbolType = symbol.getSymbolType();
                if (symbolType.isUnknown()) {
                    symbolType = Type.OBJECT;
                }

                if (symbolType.isBoolean()) {
                    elementType = elementType.widest(Type.OBJECT);
                    break;
                }

                elementType = elementType.widest(symbolType);

                if (elementType.isObject()) {
                    break;
                }
            }
        }

        private Object objectAsConstant(final Object object) {
            if (object == null) {
                return null;
            } else if (object instanceof Number || object instanceof String || object instanceof Boolean) {
                return object;
            } else if (object instanceof LiteralNode) {
                return objectAsConstant(((LiteralNode<?>)object).getValue());
            } else if (object instanceof UnaryNode) {
                final UnaryNode unaryNode = (UnaryNode)object;

                if (unaryNode.isTokenType(TokenType.CONVERT) && unaryNode.getType().isObject()) {
                    return objectAsConstant(unaryNode.rhs());
                }
            }

            return POSTSET_MARKER;
        }

        @Override
        public Node[] getArray() {
            return value;
        }

        @Override
        public Type getType() {
            if (elementType.isInteger()) {
                return Type.INT_ARRAY;
            } else if (elementType.isNumeric()) {
                return Type.NUMBER_ARRAY;
            } else {
                return Type.OBJECT_ARRAY;
            }
        }

        /**
         * Get the element type of this array literal
         * @return element type
         */
        public Type getElementType() {
            return elementType;
        }

        /**
         * Get indices of arrays containing computed post sets
         * @return post set indices
         */
        public int[] getPostsets() {
            return postsets;
        }

        /**
         * Get presets constant array
         * @return presets array, always returns an array type
         */
        public Object getPresets() {
            return presets;
        }

        /**
         * Get the array units that make up this ArrayLiteral
         * @see ArrayUnit
         * @return list of array units
         */
        public List<ArrayUnit> getUnits() {
            return units == null ? null : Collections.unmodifiableList(units);
        }

        /**
         * Set the ArrayUnits that make up this ArrayLiteral
         * @see ArrayUnit
         * @param units list of array units
         */
        public void setUnits(final List<ArrayUnit> units) {
            this.units = units;
        }

        @Override
        public Node accept(final NodeVisitor visitor) {
            if (visitor.enter(this) != null) {
                for (int i = 0; i < value.length; i++) {
                    final Node element = value[i];
                    if (element != null) {
                        value[i] = element.accept(visitor);
                    }
                }
                return visitor.leave(this);
            }
            return this;
        }

        @Override
        public void toString(final StringBuilder sb) {
            sb.append('[');
            boolean first = true;
            for (final Node node : value) {
                if (!first) {
                    sb.append(',');
                    sb.append(' ');
                }
                if (node == null) {
                    sb.append("undefined");
                } else {
                    node.toString(sb);
                }
                first = false;
            }
            sb.append(']');
        }
    }

    /**
     * Create a new array literal of Nodes from a list of Node values
     *
     * @param source  the source
     * @param token   token
     * @param finish  finish
     * @param value   literal value list
     *
     * @return the new literal node
     */
    public static LiteralNode<Node[]> newInstance(final Source source, final long token, final int finish, final List<Node> value) {
        return new ArrayLiteralNode(source, token, finish, value.toArray(new Node[value.size()]));
    }


    /**
     * Create a new array literal based on a parent node (source, token, finish)
     *
     * @param parent parent node
     * @param value  literal value list
     *
     * @return the new literal node
     */
    public static LiteralNode<?> newInstance(final Node parent, final List<Node> value) {
        return new ArrayLiteralNode(parent.getSource(), parent.getToken(), parent.getFinish(), value.toArray(new Node[value.size()]));
    }

    /**
     * Create a new array literal of Nodes
     *
     * @param source  the source
     * @param token   token
     * @param finish  finish
     * @param value   literal value array
     *
     * @return the new literal node
     */
    public static LiteralNode<Node[]> newInstance(final Source source, final long token, final int finish, final Node[] value) {
        return new ArrayLiteralNode(source, token, finish, value);
    }
}
