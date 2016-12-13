/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package jdk.tools.jaotc;

import jdk.tools.jaotc.binformat.BinaryContainer;
import jdk.tools.jaotc.binformat.Symbol;
import jdk.tools.jaotc.CompiledMethodInfo.StubInformation;

import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;

/**
 * Symbol for a regular Java call. This method also creates additional relocations for {@code .plt}
 * to {@code .got} and {@code .got} to {@code .plt}.
 */
final class JavaCallSiteRelocationSymbol extends CallSiteRelocationSymbol {

    private static final byte[] zeroSlot = new byte[8];
    private static final byte[] minusOneSlot = {-1, -1, -1, -1, -1, -1, -1, -1};

    public JavaCallSiteRelocationSymbol(CompiledMethodInfo mi, Call call, CallSiteRelocationInfo callSiteRelocation, BinaryContainer binaryContainer) {
        super(createPltEntrySymbol(binaryContainer, mi, call, callSiteRelocation));
        StubInformation stub = getStub(mi, call);
        addRelocations(mi, stub, binaryContainer, call, callSiteRelocation);
    }

    /**
     * Returns a unique symbol name with the {@code suffix} appended.
     */
    private static String relocationSymbolName(String suffix, CompiledMethodInfo mi, Call call, CallSiteRelocationInfo callSiteRelocation) {
        return "M" + mi.getCodeId() + "_" + call.pcOffset + "_" + callSiteRelocation.targetSymbol + "_" + suffix;
    }

    private static Symbol createPltEntrySymbol(BinaryContainer binaryContainer, CompiledMethodInfo mi, Call call, CallSiteRelocationInfo callSiteRelocation) {
        String symbolName = relocationSymbolName("plt.entry", mi, call, callSiteRelocation);
        StubInformation stub = getStub(mi, call);
        return createCodeContainerSymbol(binaryContainer, symbolName, stub.getOffset());
    }

    private static StubInformation getStub(CompiledMethodInfo mi, Call call) {
        HotSpotResolvedJavaMethod callTarget = (HotSpotResolvedJavaMethod) call.target;
        String callTargetSymbol = MiscUtils.uniqueMethodName(callTarget) + ".at." + call.pcOffset;
        return mi.getStubFor(callTargetSymbol);
    }

    /**
     * Add all the required relocations.
     */
    private static void addRelocations(CompiledMethodInfo mi, StubInformation stub, BinaryContainer binaryContainer, Call call, CallSiteRelocationInfo callSiteRelocation) {
        final boolean isVirtualCall = MiscUtils.isVirtualCall(mi, call);

        final int gotStartOffset = binaryContainer.appendExtLinkageGotBytes(zeroSlot, 0, zeroSlot.length);
        if (isVirtualCall) {
            // Nothing.
        } else {
            // For c2i stub we need slot with -1 value.
            binaryContainer.appendExtLinkageGotBytes(minusOneSlot, 0, minusOneSlot.length);
        }

        // Add relocation to GOT cell for call resolution jump.
        String gotSymbolName = "got." + getResolveSymbolName(binaryContainer, mi, call);
        Symbol gotSymbol = binaryContainer.getGotSymbol(gotSymbolName);
        addExternalPltToGotRelocation(binaryContainer, gotSymbol, stub.getResolveJumpOffset());

        // Add relocation to resolve call jump instruction address for GOT cell.
        String pltJmpSymbolName = relocationSymbolName("plt.jmp", mi, call, callSiteRelocation);
        addCodeContainerRelocation(binaryContainer, pltJmpSymbolName, stub.getResolveJumpStart(), gotStartOffset);

        // Add relocation to GOT cell for dispatch jump.
        String gotEntrySymbolName = relocationSymbolName("got.entry", mi, call, callSiteRelocation);
        addExtLinkageGotContainerRelocation(binaryContainer, gotEntrySymbolName, gotStartOffset, stub.getDispatchJumpOffset());

        // Virtual call needs initial -1 value.
        byte[] slot = isVirtualCall ? minusOneSlot : zeroSlot;
        final int gotMetaOffset = binaryContainer.appendMetaspaceGotBytes(slot, 0, slot.length);

        // Add relocation to GOT cell for move instruction (Klass* for virtual, Method* otherwise).
        String gotMoveSymbolName = relocationSymbolName("got.move", mi, call, callSiteRelocation);
        addMetaspaceGotRelocation(binaryContainer, gotMoveSymbolName, gotMetaOffset, stub.getMovOffset());

        if (isVirtualCall) {
            // Nothing.
        } else {
            // Add relocation to GOT cell for c2i adapter jump.
            String gotC2ISymbolName = relocationSymbolName("got.c2i", mi, call, callSiteRelocation);
            addExtLinkageGotContainerRelocation(binaryContainer, gotC2ISymbolName, gotStartOffset + 8, stub.getC2IJumpOffset());
        }
    }

    /**
     * Returns the name of the resolve method for this particular call.
     */
    private static String getResolveSymbolName(BinaryContainer binaryContainer, CompiledMethodInfo mi, Call call) {
        String resolveSymbolName;
        if (MiscUtils.isStaticCall(call)) {
            resolveSymbolName = binaryContainer.getResolveStaticEntrySymbolName();
        } else if (MiscUtils.isSpecialCall(call)) {
            resolveSymbolName = binaryContainer.getResolveOptVirtualEntrySymbolName();
        } else if (MiscUtils.isOptVirtualCall(mi, call)) {
            resolveSymbolName = binaryContainer.getResolveOptVirtualEntrySymbolName();
        } else if (MiscUtils.isVirtualCall(mi, call)) {
            resolveSymbolName = binaryContainer.getResolveVirtualEntrySymbolName();
        } else {
            throw new InternalError("Unknown call type in " + mi.asTag() + " @ " + call.pcOffset + " for call" + call.target);
        }
        return resolveSymbolName;
    }

}
