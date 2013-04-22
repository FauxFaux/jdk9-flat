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

import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.annotations.Immutable;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.Source;

/**
 * IR representation of an indexed access (brackets operator.)
 */
@Immutable
public final class IndexNode extends BaseNode {
    /** Property index. */
    private final Node index;

    /**
     * Constructors
     *
     * @param source  the source
     * @param token   token
     * @param finish  finish
     * @param base    base node for access
     * @param index   index for access
     */
    public IndexNode(final Source source, final long token, final int finish, final Node base, final Node index) {
        super(source, token, finish, base, false, false);
        this.index = index;
    }

    private IndexNode(final IndexNode indexNode, final Node base, final Node index, final boolean isFunction, final boolean hasCallSiteType) {
        super(indexNode, base, isFunction, hasCallSiteType);
        this.index = index;
    }

    @Override
    public Node accept(final NodeVisitor visitor) {
        if (visitor.enterIndexNode(this)) {
            final Node      newBase  = base.accept(visitor);
            final Node      newIndex = index.accept(visitor);
            final IndexNode newNode;
            if (newBase != base || newIndex != index) {
                newNode = new IndexNode(this, newBase, newIndex, isFunction(), hasCallSiteType());
            } else {
                newNode = this;
            }
            return visitor.leaveIndexNode(newNode);
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        final boolean needsParen = tokenType().needsParens(base.tokenType(), true);

        if (hasCallSiteType()) {
            sb.append('{');
            final String desc = getType().getDescriptor();
            sb.append(desc.charAt(desc.length() - 1) == ';' ? "O" : getType().getDescriptor());
            sb.append('}');
        }

        if (needsParen) {
            sb.append('(');
        }

        base.toString(sb);

        if (needsParen) {
            sb.append(')');
        }

        sb.append('[');
        index.toString(sb);
        sb.append(']');
    }

    /**
     * Get the index expression for this IndexNode
     * @return the index
     */
    public Node getIndex() {
        return index;
    }

    @Override
    public BaseNode setIsFunction() {
        if (isFunction()) {
            return this;
        }
        return new IndexNode(this, base, index, true, hasCallSiteType());
    }

    @Override
    public IndexNode setType(final Type type) {
        logTypeChange(type);
        getSymbol().setTypeOverride(type); //always a temp so this is fine.
        return new IndexNode(this, base, index, isFunction(), true);
    }

}
