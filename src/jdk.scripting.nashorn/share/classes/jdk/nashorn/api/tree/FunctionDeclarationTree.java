/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.api.tree;

import java.util.List;

/**
 * A tree node for a <a href="http://www.ecma-international.org/ecma-262/6.0/#sec-function-definitions">function declaration</a>.
 *
 * For example:
 * <pre>
 *   <em>function</em> <em>name</em>
 *      ( <em>parameters</em> )
 *      <em>body</em>
 * </pre>
 *
 * <pre>
 *   <em>function*</em> <em>name</em>
 *      ( <em>parameters</em> )
 *      <em>body</em>
 * </pre>
 *
 * @since 9
 */
public interface FunctionDeclarationTree extends StatementTree {
    /**
     * Returns the name of the function being declared.
     *
     * @return name the function declared
     */
    IdentifierTree getName();

    /**
     * Returns the parameters of this function.
     *
     * @return the list of parameters
     */
    List<? extends ExpressionTree> getParameters();

    /**
     * Returns the body of code of this function.
     *
     * @return the body of code
     */
    BlockTree getBody();

    /**
     * Is this a strict function?
     *
     * @return true if this function is strict
     */
    boolean isStrict();

    /**
     * Is this a generator function?
     *
     * @return true if this is a generator function
     */
    boolean isGenerator();
}
