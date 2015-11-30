/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

// The giant table of Node classes.
// One entry per class, sorted by class name.

macro(AbsD)
macro(AbsF)
macro(AbsI)
macro(AddD)
macro(AddF)
macro(AddI)
macro(AddL)
macro(AddP)
macro(Allocate)
macro(AllocateArray)
macro(AndI)
macro(AndL)
macro(ArrayCopy)
macro(AryEq)
macro(AtanD)
macro(Binary)
macro(Bool)
macro(BoxLock)
macro(ReverseBytesI)
macro(ReverseBytesL)
macro(ReverseBytesUS)
macro(ReverseBytesS)
macro(CProj)
macro(CallDynamicJava)
macro(CallJava)
macro(CallLeaf)
macro(CallLeafNoFP)
macro(CallRuntime)
macro(CallStaticJava)
macro(CastII)
macro(CastX2P)
macro(CastP2X)
macro(CastPP)
macro(Catch)
macro(CatchProj)
macro(CheckCastPP)
macro(ClearArray)
macro(ConstraintCast)
macro(CMoveD)
macro(CMoveVD)
macro(CMoveF)
macro(CMoveI)
macro(CMoveL)
macro(CMoveP)
macro(CMoveN)
macro(CmpN)
macro(CmpD)
macro(CmpD3)
macro(CmpF)
macro(CmpF3)
macro(CmpI)
macro(CmpL)
macro(CmpL3)
macro(CmpLTMask)
macro(CmpP)
macro(CmpU)
macro(CompareAndSwapI)
macro(CompareAndSwapL)
macro(CompareAndSwapP)
macro(CompareAndSwapN)
macro(GetAndAddI)
macro(GetAndAddL)
macro(GetAndSetI)
macro(GetAndSetL)
macro(GetAndSetP)
macro(GetAndSetN)
macro(Con)
macro(ConN)
macro(ConNKlass)
macro(ConD)
macro(ConF)
macro(ConI)
macro(ConL)
macro(ConP)
macro(Conv2B)
macro(ConvD2F)
macro(ConvD2I)
macro(ConvD2L)
macro(ConvF2D)
macro(ConvF2I)
macro(ConvF2L)
macro(ConvI2D)
macro(ConvI2F)
macro(ConvI2L)
macro(ConvL2D)
macro(ConvL2F)
macro(ConvL2I)
macro(CosD)
macro(CountedLoop)
macro(CountedLoopEnd)
macro(CountLeadingZerosI)
macro(CountLeadingZerosL)
macro(CountTrailingZerosI)
macro(CountTrailingZerosL)
macro(CreateEx)
macro(DecodeN)
macro(DecodeNKlass)
macro(DivD)
macro(DivF)
macro(DivI)
macro(DivL)
macro(DivMod)
macro(DivModI)
macro(DivModL)
macro(EncodeISOArray)
macro(EncodeP)
macro(EncodePKlass)
macro(FastLock)
macro(FastUnlock)
macro(Goto)
macro(Halt)
macro(HasNegatives)
macro(If)
macro(RangeCheck)
macro(IfFalse)
macro(IfTrue)
macro(Initialize)
macro(JProj)
macro(Jump)
macro(JumpProj)
macro(LShiftI)
macro(LShiftL)
macro(LoadB)
macro(LoadUB)
macro(LoadUS)
macro(LoadD)
macro(LoadD_unaligned)
macro(LoadF)
macro(LoadI)
macro(LoadKlass)
macro(LoadNKlass)
macro(LoadL)
macro(LoadL_unaligned)
macro(LoadPLocked)
macro(LoadP)
macro(LoadN)
macro(LoadRange)
macro(LoadS)
macro(Lock)
macro(Log10D)
macro(Loop)
macro(LoopLimit)
macro(Mach)
macro(MachProj)
macro(MaxI)
macro(MemBarAcquire)
macro(LoadFence)
macro(MemBarAcquireLock)
macro(MemBarCPUOrder)
macro(MemBarRelease)
macro(StoreFence)
macro(MemBarReleaseLock)
macro(MemBarVolatile)
macro(MemBarStoreStore)
macro(MergeMem)
macro(MinI)
macro(ModD)
macro(ModF)
macro(ModI)
macro(ModL)
macro(MoveI2F)
macro(MoveF2I)
macro(MoveL2D)
macro(MoveD2L)
macro(MulD)
macro(MulF)
macro(MulHiL)
macro(MulI)
macro(MulL)
macro(Multi)
macro(NegD)
macro(NegF)
macro(NeverBranch)
macro(Opaque1)
macro(Opaque2)
macro(Opaque3)
macro(ProfileBoolean)
macro(OrI)
macro(OrL)
macro(OverflowAddI)
macro(OverflowSubI)
macro(OverflowMulI)
macro(OverflowAddL)
macro(OverflowSubL)
macro(OverflowMulL)
macro(PCTable)
macro(Parm)
macro(PartialSubtypeCheck)
macro(Phi)
macro(PopCountI)
macro(PopCountL)
macro(PowD)
macro(PrefetchAllocation)
macro(Proj)
macro(RShiftI)
macro(RShiftL)
macro(Region)
macro(Rethrow)
macro(Return)
macro(Root)
macro(RoundDouble)
macro(RoundFloat)
macro(SafePoint)
macro(SafePointScalarObject)
macro(SCMemProj)
macro(SinD)
macro(SqrtD)
macro(Start)
macro(StartOSR)
macro(StoreB)
macro(StoreC)
macro(StoreCM)
macro(StorePConditional)
macro(StoreIConditional)
macro(StoreLConditional)
macro(StoreD)
macro(StoreF)
macro(StoreI)
macro(StoreL)
macro(StoreP)
macro(StoreN)
macro(StoreNKlass)
macro(StrComp)
macro(StrCompressedCopy)
macro(StrEquals)
macro(StrIndexOf)
macro(StrIndexOfChar)
macro(StrInflatedCopy)
macro(SubD)
macro(SubF)
macro(SubI)
macro(SubL)
macro(TailCall)
macro(TailJump)
macro(TanD)
macro(ThreadLocal)
macro(Unlock)
macro(URShiftI)
macro(URShiftL)
macro(XorI)
macro(XorL)
macro(Vector)
macro(AddVB)
macro(AddVS)
macro(AddVI)
macro(AddReductionVI)
macro(AddVL)
macro(AddReductionVL)
macro(AddVF)
macro(AddReductionVF)
macro(AddVD)
macro(AddReductionVD)
macro(SubVB)
macro(SubVS)
macro(SubVI)
macro(SubVL)
macro(SubVF)
macro(SubVD)
macro(MulVS)
macro(MulVI)
macro(MulReductionVI)
macro(MulVL)
macro(MulReductionVL)
macro(MulVF)
macro(MulReductionVF)
macro(MulVD)
macro(MulReductionVD)
macro(DivVF)
macro(DivVD)
macro(AbsVF)
macro(AbsVD)
macro(NegVF)
macro(NegVD)
macro(SqrtVD)
macro(LShiftCntV)
macro(RShiftCntV)
macro(LShiftVB)
macro(LShiftVS)
macro(LShiftVI)
macro(LShiftVL)
macro(RShiftVB)
macro(RShiftVS)
macro(RShiftVI)
macro(RShiftVL)
macro(URShiftVB)
macro(URShiftVS)
macro(URShiftVI)
macro(URShiftVL)
macro(AndV)
macro(OrV)
macro(XorV)
macro(LoadVector)
macro(StoreVector)
macro(Pack)
macro(PackB)
macro(PackS)
macro(PackI)
macro(PackL)
macro(PackF)
macro(PackD)
macro(Pack2L)
macro(Pack2D)
macro(ReplicateB)
macro(ReplicateS)
macro(ReplicateI)
macro(ReplicateL)
macro(ReplicateF)
macro(ReplicateD)
macro(Extract)
macro(ExtractB)
macro(ExtractUB)
macro(ExtractC)
macro(ExtractS)
macro(ExtractI)
macro(ExtractL)
macro(ExtractF)
macro(ExtractD)
