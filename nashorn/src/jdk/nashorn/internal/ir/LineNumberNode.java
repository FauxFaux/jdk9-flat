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

import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.runtime.Source;

/**
 * IR Node representing a line number
 */

public class LineNumberNode extends Node {
    /** Line number */
    private final int lineNumber;

    /**
     * Constructor
     *
     * @param source     the source
     * @param token      token
     * @param lineNumber the line number
     */
    public LineNumberNode(final Source source, final long token, final int lineNumber) {
        super(source, token, Token.descPosition(token));

        this.lineNumber = lineNumber;
    }

   private LineNumberNode(final LineNumberNode lineNumberNode) {
        super(lineNumberNode);

        this.lineNumber = lineNumberNode.getLineNumber();
    }

    @Override
    protected Node copy(final CopyState cs) {
        return new LineNumberNode(this);
    }

    @Override
    public Node accept(final NodeVisitor visitor) {
        if (visitor.enter(this) != null) {
            return visitor.leave(this);
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        sb.append("[|");
        sb.append(lineNumber);
        sb.append("|]");
    }

    @Override
    public boolean isAtom() {
        return true;
    }

    /**
     * Get the line number
     * @return line number
     */
    public int getLineNumber() {
        return lineNumber;
    }

    @Override
    public boolean isDebug() {
        return true;
    }
}
