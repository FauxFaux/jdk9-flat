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

package jdk.nashorn.internal.runtime.linker;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import jdk.nashorn.internal.runtime.JSType;
import netscape.javascript.JSObject;
import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.LinkRequest;
import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.linker.TypeBasedGuardingDynamicLinker;
import org.dynalang.dynalink.support.CallSiteDescriptorFactory;

/**
 * A Dynalink linker to handle web browser built-in JS (DOM etc.) objects as well
 * as ScriptObjects from other Nashorn contexts.
 */
public final class JSObjectLinker implements TypeBasedGuardingDynamicLinker {
   /**
     * Instances of this class are used to represent a method member of a JSObject
     */
    private static final class JSObjectMethod {
        // The name of the JSObject method property
        private final String name;

        JSObjectMethod(final String name) {
            this.name = name;
        }

        String getName() {
            return name;
        }

        static GuardedInvocation lookup(final CallSiteDescriptor desc) {
            final String operator = CallSiteDescriptorFactory.tokenizeOperators(desc).get(0);
            switch (operator) {
                case "call": {
                    // collect everything except the first two - JSObjectMethod instance and the actual 'self'
                    final int paramCount = desc.getMethodType().parameterCount();
                    final MethodHandle caller = MH.asCollector(JSOBJECTMETHOD_CALL, Object[].class, paramCount - 2);
                    return new GuardedInvocation(caller, null, IS_JSOBJECTMETHOD_GUARD);
                }
                default:
                    return null;
            }
        }
    }

    @Override
    public boolean canLinkType(final Class<?> type) {
        return canLinkTypeStatic(type);
    }

    static boolean canLinkTypeStatic(final Class<?> type) {
        // can link JSObject and JSObjectMethod
        return JSObject.class.isAssignableFrom(type) || JSObjectMethod.class.isAssignableFrom(type);
    }

    @Override
    public GuardedInvocation getGuardedInvocation(final LinkRequest request, final LinkerServices linkerServices) throws Exception {
        final LinkRequest requestWithoutContext = request.withoutRuntimeContext(); // Nashorn has no runtime context
        final Object self = requestWithoutContext.getReceiver();
        final CallSiteDescriptor desc = requestWithoutContext.getCallSiteDescriptor();

        if (desc.getNameTokenCount() < 2 || !"dyn".equals(desc.getNameToken(CallSiteDescriptor.SCHEME))) {
            // We only support standard "dyn:*[:*]" operations
            return null;
        }

        final GuardedInvocation inv;
        if (self instanceof JSObject) {
            inv = lookup(desc);
        } else if (self instanceof JSObjectMethod) {
            inv = JSObjectMethod.lookup(desc);
        } else {
            throw new AssertionError(); // Should never reach here.
        }

        return Bootstrap.asType(inv, linkerServices, desc);
    }

    private static GuardedInvocation lookup(final CallSiteDescriptor desc) {
        final String operator = CallSiteDescriptorFactory.tokenizeOperators(desc).get(0);
        final int c = desc.getNameTokenCount();
        switch (operator) {
            case "getProp":
            case "getElem":
            case "getMethod":
                return c > 2 ? findGetMethod(desc, operator) : findGetIndexMethod();
            case "setProp":
            case "setElem":
                return c > 2 ? findSetMethod(desc) : findSetIndexMethod();
            case "call":
            case "callMethod":
                return findCallMethod(desc, operator);
            case "new":
            default:
                return null;
        }
    }

    private static GuardedInvocation findGetMethod(final CallSiteDescriptor desc, final String operator) {
        // if "getMethod" then return JSObjectMethod object - which just holds the name of the method
        // subsequently, link on dyn:call for JSObjectMethod will actually call that method
        final MethodHandle getter = MH.insertArguments("getMethod".equals(operator)? JSOBJECT_GETMETHOD : JSOBJECT_GET, 1, desc.getNameToken(2));
        return new GuardedInvocation(getter, null, IS_JSOBJECT_GUARD);
    }

    private static GuardedInvocation findGetIndexMethod() {
        return new GuardedInvocation(JSOBJECT_GET, null, IS_JSOBJECT_GUARD);
    }

    private static GuardedInvocation findSetMethod(final CallSiteDescriptor desc) {
        final MethodHandle getter = MH.insertArguments(JSOBJECT_PUT, 1, desc.getNameToken(2));
        return new GuardedInvocation(getter, null, IS_JSOBJECT_GUARD);
    }

    private static GuardedInvocation findSetIndexMethod() {
        return new GuardedInvocation(JSOBJECT_PUT, null, IS_JSOBJECT_GUARD);
    }

    private static GuardedInvocation findCallMethod(final CallSiteDescriptor desc, final String operator) {
        // if operator is "call", then 'self' is a JSObject function object already. Use 'call' as the method name
        final String methodName = "callMethod".equals(operator)? desc.getNameToken(2) : "call";
        MethodHandle func = MH.insertArguments(JSOBJECT_CALL, 1, methodName);
        func = MH.asCollector(func, Object[].class, desc.getMethodType().parameterCount() - 1);
        return new GuardedInvocation(func, null, IS_JSOBJECT_GUARD);
    }

    @SuppressWarnings("unused")
    private static boolean isJSObjectMethod(final Object self) {
        return self instanceof JSObjectMethod;
    }

    @SuppressWarnings("unused")
    private static boolean isJSObject(final Object self) {
        return self instanceof JSObject;
    }


    @SuppressWarnings("unused")
    private static Object getMethod(final Object jsobj, final Object key) {
        return new JSObjectMethod(Objects.toString(key));
    }

    @SuppressWarnings("unused")
    private static Object get(final Object jsobj, final Object key) {
        if (key instanceof String) {
            return ((JSObject)jsobj).getMember((String)key);
        } else if (key instanceof Number) {
            final int index = getIndex((Number)key);
            if (index > -1) {
                return ((JSObject)jsobj).getSlot(index);
            }
        }
        return null;
    }

    @SuppressWarnings("unused")
    private static void put(final Object jsobj, final Object key, final Object value) {
        if (key instanceof String) {
            ((JSObject)jsobj).setMember((String)key, value);
        } else if (key instanceof Number) {
            ((JSObject)jsobj).setSlot(getIndex((Number)key), value);
        }
    }

    @SuppressWarnings("unused")
    private static Object call(final Object jsobj, final Object method, final Object... args) {
        return ((JSObject)jsobj).call(Objects.toString(method), args);
    }

    @SuppressWarnings("unused")
    private static Object jsObjectMethodCall(final Object jsObjMethod, final Object jsobj, final Object... args) {
        // we have JSObjectMethod, JSObject and args. Get method name from JSObjectMethod instance
        final String methodName = ((JSObjectMethod)jsObjMethod).getName();
        // call the method on JSObject
        return ((JSObject)jsobj).call(methodName, args);
    }

    private static int getIndex(final Number n) {
        final double value = n.doubleValue();
        return JSType.isRepresentableAsInt(value) ? (int)value : -1;
    }

    private static final MethodHandleFunctionality MH = MethodHandleFactory.getFunctionality();

    private static final MethodHandle IS_JSOBJECTMETHOD_GUARD = findOwnMH("isJSObjectMethod", boolean.class, Object.class);
    private static final MethodHandle IS_JSOBJECT_GUARD = findOwnMH("isJSObject", boolean.class, Object.class);
    private static final MethodHandle JSOBJECT_GETMETHOD = findOwnMH("getMethod", Object.class, Object.class, Object.class);
    private static final MethodHandle JSOBJECT_GET = findOwnMH("get", Object.class, Object.class, Object.class);
    private static final MethodHandle JSOBJECT_PUT = findOwnMH("put", Void.TYPE, Object.class, Object.class, Object.class);
    private static final MethodHandle JSOBJECT_CALL = findOwnMH("call", Object.class, Object.class, Object.class, Object[].class);
    private static final MethodHandle JSOBJECTMETHOD_CALL = findOwnMH("jsObjectMethodCall", Object.class, Object.class, Object.class, Object[].class);

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        final Class<?>   own = JSObjectLinker.class;
        final MethodType mt  = MH.type(rtype, types);
        try {
            return MH.findStatic(MethodHandles.lookup(), own, name, mt);
        } catch (final MethodHandleFactory.LookupException e) {
            return MH.findVirtual(MethodHandles.lookup(), own, name, mt);
        }
    }
}
