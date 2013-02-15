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

import static jdk.nashorn.internal.codegen.CompilerConstants.__DIR__;
import static jdk.nashorn.internal.codegen.CompilerConstants.__FILE__;
import static jdk.nashorn.internal.codegen.CompilerConstants.__LINE__;
import static jdk.nashorn.internal.codegen.ObjectClassGenerator.DEBUG_FIELDS;

import jdk.nashorn.internal.codegen.ObjectClassGenerator;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.Source;

/**
 * IR representation for an identifier.
 */
public class IdentNode extends Node implements PropertyKey, TypeOverride, FunctionCall {
    /** Identifier. */
    private final String name;

    /** Type for a callsite, e.g. X in a get()X or a set(X)V */
    private Type callSiteType;

    /** flag for an ident that is the property name of an AccessNode. */
    private boolean isPropertyName;

    /** flag for an ident on the left hand side of <code>var lhs = rhs;</code>. */
    private boolean isInitializedHere;

    /**
     * Constructor
     *
     * @param source  the source
     * @param token   token
     * @param finish  finish position
     * @param name    name of identifier
     */
    public IdentNode(final Source source, final long token, final int finish, final String name) {
        super(source, token, finish);
        this.name = name;
    }

    /**
     * Copy constructor - create a new IdentNode for the same location
     *
     * @param identNode  identNode
     */
    public IdentNode(final IdentNode identNode) {
        super(identNode);
        this.name              = identNode.getName();
        this.isPropertyName    = identNode.isPropertyName;
        this.isInitializedHere = identNode.isInitializedHere;
    }

    @Override
    public Type getType() {
        return callSiteType == null ? super.getType() : callSiteType;
    }

    @Override
    public boolean isAtom() {
        return true;
    }

    private boolean hasCallSiteType() {
        //this is an identity that's part of a getter or setter
        return callSiteType != null;
    }

    @Override
    public void setType(final Type type) {
        if (DEBUG_FIELDS && getSymbol() != null && !Type.areEquivalent(getSymbol().getSymbolType(), type)) {
            ObjectClassGenerator.LOG.info(getClass().getName() + " " + this + " => " + type + " instead of " + getType());
        }
        this.callSiteType = type;
        // do NOT, repeat NOT touch the symbol here. it might be a local variable or whatever. This is the override if it isn't
    }

    @Override
    protected Node copy(final CopyState cs) {
        return new IdentNode(this);
    }

    /**
     * Test to see if two IdentNode are the same.
     *
     * @param other Other ident.
     * @return true if the idents are the same.
     */
    @Override
    public boolean equals(final Object other) {
        if (other instanceof IdentNode) {
            return name.equals(((IdentNode)other).name);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
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
        if (hasCallSiteType()) {
            sb.append('{');
            final String desc = getType().getDescriptor();
            sb.append(desc.charAt(desc.length() - 1) == ';' ? "O" : getType().getDescriptor());
            sb.append('}');
        }

        sb.append(name);
    }

    /**
     * Get the name of the identifier
     * @return  IdentNode name
     */
    public String getName() {
        return name;
    }

    @Override
    public String getPropertyName() {
        return getName();
    }

    /**
     * We can only override type if the symbol lives in the scope, otherwise
     * it is strongly determined by the local variable already allocated
     *
     * @return true if can have callsite type
     */
    @Override
    public boolean canHaveCallSiteType() {
        return getSymbol() != null && getSymbol().isScope();
    }

    /**
     * Check if this IdentNode is a property name
     * @return true if this is a property name
     */
    public boolean isPropertyName() {
        return isPropertyName;
    }

    /**
     * Flag this IdentNode as a property name
     */
    public void setIsPropertyName() {
        isPropertyName = true;
    }

    /**
     * Helper function for local def analysis.
     * @return true if IdentNode is initialized on creation
     */
    public boolean isInitializedHere() {
        return isInitializedHere;
    }

    /**
     * Flag IdentNode to be initialized on creation
     */
    public void setIsInitializedHere() {
        isInitializedHere = true;
    }

    /**
     * Check if this IdentNode is a special identity, currently __DIR__, __FILE__
     * or __LINE__
     *
     * @return true if this IdentNode is special
     */
    public boolean isSpecialIdentity() {
        return name.equals(__DIR__.tag()) || name.equals(__FILE__.tag()) || name.equals(__LINE__.tag());
    }

    @Override
    public boolean isFunction() {
        return false;
    }
}
