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

package jdk.nashorn.internal.runtime;

import static jdk.nashorn.internal.lookup.Lookup.MH;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import jdk.internal.dynalink.CallSiteDescriptor;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.linker.LinkRequest;
import jdk.internal.dynalink.support.CallSiteDescriptorFactory;
import jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor;

/**
 * This class supports the handling of scope in a with body.
 *
 */
public final class WithObject extends ScriptObject implements Scope {
    private static final MethodHandle WITHEXPRESSIONGUARD    = findOwnMH("withExpressionGuard",  boolean.class, ScriptObject.class, PropertyMap.class, SwitchPoint.class);
    private static final MethodHandle WITHEXPRESSIONFILTER   = findOwnMH("withFilterExpression", ScriptObject.class, ScriptObject.class);
    private static final MethodHandle WITHSCOPEFILTER        = findOwnMH("withFilterScope",      ScriptObject.class, ScriptObject.class);
    private static final MethodHandle BIND_TO_EXPRESSION_OBJ = findOwnMH("bindToExpression",     Object.class, Object.class, ScriptObject.class);
    private static final MethodHandle BIND_TO_EXPRESSION_FN  = findOwnMH("bindToExpression",     Object.class, ScriptFunction.class, ScriptObject.class);

    private static final MethodHandle CONSTANT_FALSE_TAKE_SCRIPT_OBJECT = MH.dropArguments(MH.constant(boolean.class, false), 0, ScriptObject.class);


    /** With expression object. */
    private final ScriptObject expression;

    /**
     * Constructor
     *
     * @param scope scope object
     * @param expression with expression
     */
    WithObject(final ScriptObject scope, final ScriptObject expression) {
        super(scope, null);
        setIsScope();
        this.expression = expression;
    }


    /**
     * Delete a property based on a key.
     * @param key Any valid JavaScript value.
     * @param strict strict mode execution.
     * @return True if deleted.
     */
    @Override
    public boolean delete(final Object key, final boolean strict) {
        final ScriptObject self = expression;
        final String propName = JSType.toString(key);

        final FindProperty find = self.findProperty(propName, true);

        if (find != null) {
            return self.delete(propName, strict);
        }

        return false;
    }


    @Override
    public GuardedInvocation lookup(final CallSiteDescriptor desc, final LinkRequest request) {
        // With scopes can never be observed outside of Nashorn code, so all call sites that can address it will of
        // necessity have a Nashorn descriptor - it is safe to cast.
        final NashornCallSiteDescriptor ndesc = (NashornCallSiteDescriptor)desc;
        FindProperty find = null;
        GuardedInvocation link = null;
        ScriptObject self;

        final boolean isNamedOperation;
        final String name;
        if(desc.getNameTokenCount() > 2) {
            isNamedOperation = true;
            name = desc.getNameToken(CallSiteDescriptor.NAME_OPERAND);
        } else {
            isNamedOperation = false;
            name = null;
        }

        self = expression;
        if (isNamedOperation) {
             find = self.findProperty(name, true);
        }

        if (find != null) {
            link = self.lookup(desc, request);

            if (link != null) {
                return fixExpressionCallSite(ndesc, link);
            }
        }

        final ScriptObject scope = getProto();
        if (isNamedOperation) {
            find = scope.findProperty(name, true);
        }

        if (find != null) {
            return fixScopeCallSite(scope.lookup(desc, request), name);
        }

        // the property is not found - now check for
        // __noSuchProperty__ and __noSuchMethod__ in expression
        if (self != null) {
            final String fallBack;

            final String operator = CallSiteDescriptorFactory.tokenizeOperators(desc).get(0);

            switch (operator) {
            case "callMethod":
                throw new AssertionError(); // Nashorn never emits callMethod
            case "getMethod":
                fallBack = NO_SUCH_METHOD_NAME;
                break;
            case "getProp":
            case "getElem":
                fallBack = NO_SUCH_PROPERTY_NAME;
                break;
            default:
                fallBack = null;
                break;
            }

            if (fallBack != null) {
                find = self.findProperty(fallBack, true);
                if (find != null) {
                    switch (operator) {
                    case "getMethod":
                        link = self.noSuchMethod(desc, request);
                        break;
                    case "getProp":
                    case "getElem":
                        link = self.noSuchProperty(desc, request);
                        break;
                    default:
                        break;
                    }
                }
            }

            if (link != null) {
                return fixExpressionCallSite(ndesc, link);
            }
        }

        // still not found, may be scope can handle with it's own
        // __noSuchProperty__, __noSuchMethod__ etc.
        link = scope.lookup(desc, request);

        if (link != null) {
            return fixScopeCallSite(link, name);
        }

        return null;
    }

    /**
     * Overridden to try to find the property first in the expression object (and its prototypes), and only then in this
     * object (and its prototypes).
     *
     * @param key  Property key.
     * @param deep Whether the search should look up proto chain.
     * @param stopOnNonScope should a deep search stop on the first non-scope object?
     * @param start the object on which the lookup was originally initiated
     *
     * @return FindPropertyData or null if not found.
     */
    @Override
    FindProperty findProperty(final String key, final boolean deep, final boolean stopOnNonScope, final ScriptObject start) {
        final FindProperty exprProperty = expression.findProperty(key, deep, stopOnNonScope, start);
        if (exprProperty != null) {
             return exprProperty;
        }
        return super.findProperty(key, deep, stopOnNonScope, start);
    }

    @Override
    public void setSplitState(final int state) {
        getNonWithParent().setSplitState(state);
    }

    @Override
    public int getSplitState() {
        return getNonWithParent().getSplitState();
    }

    /**
     * Get first parent scope that is not an instance of WithObject.
     */
    private Scope getNonWithParent() {
        ScriptObject proto = getParentScope();

        while (proto != null && proto instanceof WithObject) {
            proto = ((WithObject)proto).getParentScope();
        }

        assert proto instanceof Scope : "with scope without parent scope";
        return (Scope) proto;
    }


    private static GuardedInvocation fixReceiverType(final GuardedInvocation link, final MethodHandle filter) {
        // The receiver may be an Object or a ScriptObject.
        final MethodType invType = link.getInvocation().type();
        final MethodType newInvType = invType.changeParameterType(0, filter.type().returnType());
        return link.asType(newInvType);
    }

    private static GuardedInvocation fixExpressionCallSite(final NashornCallSiteDescriptor desc, final GuardedInvocation link) {
        // If it's not a getMethod, just add an expression filter that converts WithObject in "this" position to its
        // expression.
        if(!"getMethod".equals(desc.getFirstOperator())) {
            return fixReceiverType(link, WITHEXPRESSIONFILTER).filterArguments(0, WITHEXPRESSIONFILTER);
        }

        final MethodHandle linkInvocation = link.getInvocation();
        final MethodType linkType = linkInvocation.type();
        final boolean linkReturnsFunction = ScriptFunction.class.isAssignableFrom(linkType.returnType());

        return link.replaceMethods(
                // Make sure getMethod will bind the script functions it receives to WithObject.expression
                MH.foldArguments(linkReturnsFunction ? BIND_TO_EXPRESSION_FN : BIND_TO_EXPRESSION_OBJ,
                        filterReceiver(linkInvocation.asType(linkType.changeReturnType(
                                linkReturnsFunction ? ScriptFunction.class : Object.class).changeParameterType(0, ScriptObject.class)), WITHEXPRESSIONFILTER)),
                // No clever things for the guard -- it is still identically filtered.
                filterGuardReceiver(link, WITHEXPRESSIONFILTER));
    }

    private GuardedInvocation fixScopeCallSite(final GuardedInvocation link, final String name) {
        final GuardedInvocation newLink = fixReceiverType(link, WITHSCOPEFILTER);
        final MethodHandle expressionGuard = expressionGuard(name);
        final MethodHandle filteredGuard = filterGuardReceiver(newLink, WITHSCOPEFILTER);
        final MethodHandle newGuard;
        if (filteredGuard == null) {
            newGuard = expressionGuard;
        } else {
            newGuard = MH.guardWithTest(expressionGuard, filteredGuard, CONSTANT_FALSE_TAKE_SCRIPT_OBJECT);
        }
        return link.replaceMethods(filterReceiver(newLink.getInvocation(), WITHSCOPEFILTER), newGuard);
    }

    private static MethodHandle filterGuardReceiver(final GuardedInvocation link, final MethodHandle receiverFilter) {
        final MethodHandle test = link.getGuard();
        return test == null ? null : filterReceiver(test, receiverFilter);
    }

    private static MethodHandle filterReceiver(final MethodHandle mh, final MethodHandle receiverFilter) {
        return MH.filterArguments(mh, 0, receiverFilter);
    }

    /**
     * Drops the WithObject wrapper from the expression.
     * @param receiver WithObject wrapper.
     * @return The with expression.
     */
    public static ScriptObject withFilterExpression(final ScriptObject receiver) {
        return ((WithObject)receiver).expression;
    }

    @SuppressWarnings("unused")
    private static Object bindToExpression(final Object fn, final ScriptObject receiver) {
        return fn instanceof ScriptFunction ? bindToExpression((ScriptFunction) fn, receiver) : fn;
    }

    private static Object bindToExpression(final ScriptFunction fn, final ScriptObject receiver) {
        return fn.makeBoundFunction(withFilterExpression(receiver), ScriptRuntime.EMPTY_ARRAY);
    }

    private MethodHandle expressionGuard(final String name) {
        final PropertyMap map = expression.getMap();
        final SwitchPoint sp = map.getProtoGetSwitchPoint(expression.getProto(), name);
        return MH.insertArguments(WITHEXPRESSIONGUARD, 1, map, sp);
    }

    @SuppressWarnings("unused")
    private static boolean withExpressionGuard(final ScriptObject receiver, final PropertyMap map, final SwitchPoint sp) {
        return ((WithObject)receiver).expression.getMap() == map && (sp == null || !sp.hasBeenInvalidated());
    }

    /**
     * Drops the WithObject wrapper from the scope.
     * @param receiver WithObject wrapper.
     * @return The with scope.
     */
    public static ScriptObject withFilterScope(final ScriptObject receiver) {
        return ((WithObject)receiver).getProto();
    }

    /**
     * Get the with expression for this {@code WithObject}
     * @return the with expression
     */
    public ScriptObject getExpression() {
        return expression;
    }

    /**
     * Get the parent scope for this {@code WithObject}
     * @return the parent scope
     */
    public ScriptObject getParentScope() {
        return getProto();
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), WithObject.class, name, MH.type(rtype, types));
    }
}
