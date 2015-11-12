/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot;

import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Implementation of {@link JavaType} for resolved non-primitive HotSpot classes.
 */
public interface HotSpotResolvedObjectType extends ResolvedJavaType {

    /**
     * Gets the JVMCI mirror for a {@link Class} object.
     *
     * @return the {@link HotSpotResolvedJavaType} corresponding to {@code javaClass}
     */
    static HotSpotResolvedObjectType fromObjectClass(Class<?> javaClass) {
        return HotSpotResolvedObjectTypeImpl.fromObjectClass(javaClass);
    }

    HotSpotResolvedObjectType getArrayClass();

    ResolvedJavaType getComponentType();

    AssumptionResult<ResolvedJavaType> findLeafConcreteSubtype();

    HotSpotResolvedObjectType getSuperclass();

    HotSpotResolvedObjectType[] getInterfaces();

    HotSpotResolvedObjectType getSupertype();

    HotSpotResolvedObjectType findLeastCommonAncestor(ResolvedJavaType otherType);

    HotSpotResolvedObjectType asExactType();

    default boolean isPrimitive() {
        return false;
    }

    default JavaKind getJavaKind() {
        return JavaKind.Object;
    }

    ConstantPool getConstantPool();

    /**
     * Gets the instance size of this type. If an instance of this type cannot be fast path
     * allocated, then the returned value is negative (its absolute value gives the size). Must not
     * be called if this is an array or interface type.
     */
    int instanceSize();

    int getVtableLength();

    @Override
    AssumptionResult<ResolvedJavaMethod> findUniqueConcreteMethod(ResolvedJavaMethod method);

    /**
     * Performs a fast-path check that this type is resolved in the context of a given accessing
     * class. A negative result does not mean this type is not resolved with respect to
     * {@code accessingClass}. That can only be determined by
     * {@linkplain HotSpotJVMCIRuntime#lookupType(String, HotSpotResolvedObjectType, boolean)
     * re-resolving} the type.
     */
    boolean isDefinitelyResolvedWithRespectTo(ResolvedJavaType accessingClass);

    /**
     * Gets the metaspace Klass boxed in a {@link JavaConstant}.
     */
    Constant klass();

    boolean isPrimaryType();

    int superCheckOffset();

    long prototypeMarkWord();

    int layoutHelper();

    HotSpotResolvedObjectType getEnclosingType();

    ResolvedJavaMethod getClassInitializer();

    ResolvedJavaField createField(String name, JavaType type, long offset, int modifiers);
}
