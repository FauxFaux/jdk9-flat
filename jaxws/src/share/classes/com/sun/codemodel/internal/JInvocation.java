/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.codemodel.internal;

import java.util.ArrayList;
import java.util.List;


/**
 * JMethod invocation
 */
public final class JInvocation extends JExpressionImpl implements JStatement {

    /**
     * Object expression upon which this method will be invoked, or null if
     * this is a constructor invocation
     */
    private JGenerable object;

    /**
     * Name of the method to be invoked
     */
    private String name;

    private boolean isConstructor = false;

    /**
     * List of argument expressions for this method invocation
     */
    private List<JExpression> args = new ArrayList<JExpression>();

    /**
     * If isConstructor==true, this field keeps the type to be created.
     */
    private JType type = null;

    /**
     * Invokes a method on an object.
     *
     * @param object
     *        JExpression for the object upon which
     *        the named method will be invoked,
     *        or null if none
     *
     * @param name
     *        Name of method to invoke
     */
    JInvocation(JExpression object, String name) {
        this( (JGenerable)object, name );
    }

    /**
     * Invokes a static method on a class.
     */
    JInvocation(JClass type, String name) {
        this( (JGenerable)type, name );
    }

    private JInvocation(JGenerable object, String name) {
        this.object = object;
        if (name.indexOf('.') >= 0)
            throw new IllegalArgumentException("JClass name contains '.': "
                                               + name);
        this.name = name;
    }

    /**
     * Invokes a constructor of an object (i.e., creates
     * a new object.)
     *
     * @param c
     *      Type of the object to be created. If this type is
     *      an array type, added arguments are treated as array
     *      initializer. Thus you can create an expression like
     *      <code>new int[]{1,2,3,4,5}</code>.
     */
    JInvocation(JType c) {
        this.isConstructor = true;
        this.type = c;
    }

    /**
     *  Add an expression to this invocation's argument list
     *
     * @param arg
     *        Argument to add to argument list
     */
    public JInvocation arg(JExpression arg) {
        if(arg==null)   throw new IllegalArgumentException();
        args.add(arg);
        return this;
    }

    /**
     * Adds a literal argument.
     *
     * Short for {@code arg(JExpr.lit(v))}
     */
    public JInvocation arg(String v) {
        return arg(JExpr.lit(v));
    }


    public void generate(JFormatter f) {
        if (isConstructor && type.isArray()) {
            // [RESULT] new T[]{arg1,arg2,arg3,...};
            f.p("new").g(type).p('{');
        } else {
            if (isConstructor)
                f.p("new").g(type).p('(');
            else if (object != null)
                f.g(object).p('.').p(name).p('(');
            else
                f.id(name).p('(');
        }

        f.g(args);

        if (isConstructor && type.isArray())
            f.p('}');
        else
            f.p(')');

        if( type instanceof JDefinedClass && ((JDefinedClass)type).isAnonymous() ) {
            ((JAnonymousClass)type).declareBody(f);
        }
    }

    public void state(JFormatter f) {
        f.g(this).p(';').nl();
    }

}
