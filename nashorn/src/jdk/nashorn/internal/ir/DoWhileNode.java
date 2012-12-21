/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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

import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.Source;

/**
 * Loop representing do while loops. This is mostly split from WhileNode
 * because of the different order of the Phi Traversals
 *
 */
public class DoWhileNode extends WhileNode {

    /**
     * Constructor
     *
     * @param source     the source
     * @param token      token
     * @param finish     finish
     */
    public DoWhileNode(final Source source, final long token, final int finish) {
        super(source, token, finish);
    }

    /**
     * Copy constructor
     *
     * @param doWhileNode source node
     * @param cs          copy state
     */
    protected DoWhileNode(final DoWhileNode doWhileNode, final CopyState cs) {
        super(doWhileNode, cs);
    }

    @Override
    protected Node copy(final CopyState cs) {
        return new DoWhileNode(this, cs);
    }

    @Override
    public Node accept(final NodeVisitor visitor) {
        if (visitor.enter(this) != null) {
            body = (Block)body.accept(visitor);
            test = test.accept(visitor);

            return visitor.leave(this);
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        sb.append("while (");
        test.toString(sb);
        sb.append(')');
    }
}
