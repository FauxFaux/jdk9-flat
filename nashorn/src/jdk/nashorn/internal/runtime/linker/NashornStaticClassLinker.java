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

import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ECMAErrors;
import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.beans.BeansLinker;
import org.dynalang.dynalink.beans.StaticClass;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.GuardingDynamicLinker;
import org.dynalang.dynalink.linker.LinkRequest;
import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.linker.TypeBasedGuardingDynamicLinker;
import org.dynalang.dynalink.support.Guards;

/**
 * Internal linker for {@link StaticClass} objects, only ever used by Nashorn engine and not exposed to other engines.
 * It is used for extending the "new" operator on StaticClass in order to be able to instantiate interfaces and abstract
 * classes by passing a ScriptObject or ScriptFunction as their implementation, e.g.:
 * <pre>
 *   var r = new Runnable() { run: function() { print("Hello World" } }
 * </pre>
 * or for SAM types, even just passing a function:
 * <pre>
 *   var r = new Runnable(function() { print("Hello World" })
 * </pre>
 */
class NashornStaticClassLinker implements TypeBasedGuardingDynamicLinker {
    private static final GuardingDynamicLinker staticClassLinker = BeansLinker.getLinkerForClass(StaticClass.class);

    @Override
    public boolean canLinkType(Class<?> type) {
        return type == StaticClass.class;
    }

    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest linkRequest, LinkerServices linkerServices) throws Exception {
        final LinkRequest request = linkRequest.withoutRuntimeContext(); // Nashorn has no runtime context
        final Object self = request.getReceiver();
        if (self.getClass() != StaticClass.class) {
            return null;
        }
        final CallSiteDescriptor desc = request.getCallSiteDescriptor();
        // We intercept "new" on StaticClass instances to provide additional capabilities
        if ("new".equals(desc.getNameToken(CallSiteDescriptor.OPERATOR))) {
            final Class<?> receiverClass = ((StaticClass) self).getRepresentedClass();
            // Is the class abstract? (This includes interfaces.)
            if (JavaAdapterFactory.isAbstractClass(receiverClass)) {
                // Change this link request into a link request on the adapter class.
                final Object[] args = request.getArguments();
                args[0] = JavaAdapterFactory.getAdapterClassFor(new Class<?>[] { receiverClass });
                final LinkRequest adapterRequest = request.replaceArguments(request.getCallSiteDescriptor(), args);
                final GuardedInvocation gi = checkNullConstructor(delegate(linkerServices, adapterRequest), receiverClass);
                // Finally, modify the guard to test for the original abstract class.
                return gi.replaceMethods(gi.getInvocation(), Guards.getIdentityGuard(self));
            }
            // If the class was not abstract, just delegate linking to the standard StaticClass linker. Make an
            // additional check to ensure we have a constructor. We could just fall through to the next "return"
            // statement, except we also insert a call to checkNullConstructor() which throws an ECMAScript TypeError
            // with a more intuitive message when no suitable constructor is found.
            return checkNullConstructor(delegate(linkerServices, request), receiverClass);
        }
        // In case this was not a "new" operation, just delegate to the the standard StaticClass linker.
        return delegate(linkerServices, request);
    }

    private static GuardedInvocation delegate(LinkerServices linkerServices, final LinkRequest request) throws Exception {
        return staticClassLinker.getGuardedInvocation(request, linkerServices);
    }

    private static GuardedInvocation checkNullConstructor(final GuardedInvocation ctorInvocation, final Class<?> receiverClass) {
        if(ctorInvocation == null) {
            ECMAErrors.typeError(Context.getGlobal(), "no.constructor.matches.args", receiverClass.getName());
        }
        return ctorInvocation;
    }
}
