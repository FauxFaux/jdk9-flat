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

import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;
import static jdk.nashorn.internal.runtime.linker.Lookup.MH;

import java.lang.invoke.MethodHandle;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.GuardingDynamicLinker;
import org.dynalang.dynalink.linker.LinkRequest;
import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.support.Guards;

/**
 * Nashorn bottom linker; used as a last-resort catch-all linker for all linking requests that fall through all other
 * linkers (see how {@link Bootstrap} class configures the dynamic linker in its static initializer). It will throw
 * appropriate ECMAScript errors for attempts to invoke operations on {@code null}, link no-op property getters and
 * setters for Java objects that couldn't be linked by any other linker, and throw appropriate ECMAScript errors for
 * attempts to invoke arbitrary Java objects as functions or constructors.
 */
class NashornBottomLinker implements GuardingDynamicLinker {

    @Override
    public GuardedInvocation getGuardedInvocation(final LinkRequest linkRequest, final LinkerServices linkerServices)
            throws Exception {
        final Object self = linkRequest.getReceiver();

        if (self == null) {
            return linkNull(linkRequest);
        }

        // None of the objects that can be linked by NashornLinker should ever reach here. Basically, anything below
        // this point is a generic Java bean. Therefore, reaching here with a ScriptObject is a Nashorn bug.
        assert isExpectedObject(self) : "Couldn't link " + linkRequest.getCallSiteDescriptor() + " for " + self.getClass().getName();

        return linkBean(linkRequest, linkerServices);
    }

    private static final MethodHandle EMPTY_PROP_GETTER =
            MH.dropArguments(MH.constant(Object.class, UNDEFINED), 0, Object.class);
    private static final MethodHandle EMPTY_ELEM_GETTER =
            MH.dropArguments(EMPTY_PROP_GETTER, 0, Object.class);
    private static final MethodHandle EMPTY_PROP_SETTER =
            MH.asType(EMPTY_ELEM_GETTER, EMPTY_ELEM_GETTER.type().changeReturnType(void.class));
    private static final MethodHandle EMPTY_ELEM_SETTER =
            MH.dropArguments(EMPTY_PROP_SETTER, 0, Object.class);

    private static GuardedInvocation linkBean(final LinkRequest linkRequest, final LinkerServices linkerServices) {
        final NashornCallSiteDescriptor desc = (NashornCallSiteDescriptor)linkRequest.getCallSiteDescriptor();
        final Object self = linkRequest.getReceiver();
        final String operator = desc.getFirstOperator();
        switch (operator) {
        case "new":
            if(isJavaDynamicMethod(self)) {
                typeError(Context.getGlobal(), "method.not.constructor", ScriptRuntime.safeToString(self));
            } else {
                typeError(Context.getGlobal(), "not.a.function", ScriptRuntime.safeToString(self));
            }
            break;
        case "call":
            if(isJavaDynamicMethod(self)) {
                typeError(Context.getGlobal(), "no.method.matches.args", ScriptRuntime.safeToString(self));
            } else {
                typeError(Context.getGlobal(), "not.a.function", ScriptRuntime.safeToString(self));
            }
            break;
        case "callMethod":
        case "getMethod":
            typeError(Context.getGlobal(), "no.such.function", getArgument(linkRequest), ScriptRuntime.safeToString(self));
            break;
        case "getProp":
        case "getElem":
            if (desc.getOperand() != null) {
                return getInvocation(EMPTY_PROP_GETTER, self, linkerServices, desc);
            }
            return getInvocation(EMPTY_ELEM_GETTER, self, linkerServices, desc);
        case "setProp":
        case "setElem":
            if (desc.getOperand() != null) {
                return getInvocation(EMPTY_PROP_SETTER, self, linkerServices, desc);
            }
            return getInvocation(EMPTY_ELEM_SETTER, self, linkerServices, desc);
        default:
            break;
        }
        throw new AssertionError("unknown call type " + desc);
    }

    /**
     * Returns true if the object is a Dynalink dynamic method. Unfortunately, the dynamic method classes are package
     * private in Dynalink, so this is the closest we can get to determining it.
     * @param obj the object we want to test for being a dynamic method
     * @return true if it is a dynamic method, false otherwise.
     */
    private static boolean isJavaDynamicMethod(Object obj) {
        return obj.getClass().getName().endsWith("DynamicMethod");
    }

    private static GuardedInvocation getInvocation(final MethodHandle handle, final Object self, final LinkerServices linkerServices, final CallSiteDescriptor desc) {
        return Bootstrap.asType(new GuardedInvocation(handle, Guards.getClassGuard(self.getClass())), linkerServices, desc);
    }

    // Used solely in an assertion to figure out if the object we get here is something we in fact expect. Objects
    // linked by NashornLinker should never reach here.
    private static boolean isExpectedObject(final Object obj) {
        return !(NashornLinker.canLinkTypeStatic(obj.getClass()));
    }

    private static GuardedInvocation linkNull(final LinkRequest linkRequest) {
        final ScriptObject global = Context.getGlobal();
        final NashornCallSiteDescriptor desc = (NashornCallSiteDescriptor)linkRequest.getCallSiteDescriptor();
        final String operator = desc.getFirstOperator();
        switch (operator) {
        case "new":
        case "call":
            typeError(global, "not.a.function", "null");
            break;
        case "callMethod":
        case "getMethod":
            typeError(global, "no.such.function", getArgument(linkRequest), "null");
            break;
        case "getProp":
        case "getElem":
            typeError(global, "cant.get.property", getArgument(linkRequest), "null");
            break;
        case "setProp":
        case "setElem":
            typeError(global, "cant.set.property", getArgument(linkRequest), "null");
            break;
        default:
            break;
        }
        throw new AssertionError("unknown call type " + desc);
    }

    private static String getArgument(final LinkRequest linkRequest) {
        final CallSiteDescriptor desc = linkRequest.getCallSiteDescriptor();
        if (desc.getNameTokenCount() > 2) {
            return desc.getNameToken(2);
        }
        return ScriptRuntime.safeToString(linkRequest.getArguments()[1]);
    }
}
