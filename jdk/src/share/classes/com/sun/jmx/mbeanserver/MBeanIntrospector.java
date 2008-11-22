/*
 * Copyright 2005-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.jmx.mbeanserver;


import static com.sun.jmx.mbeanserver.Util.*;

import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.management.Description;

import javax.management.Descriptor;
import javax.management.ImmutableDescriptor;
import javax.management.IntrospectionException;
import javax.management.InvalidAttributeValueException;
import javax.management.JMX;
import javax.management.MBean;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MXBean;
import javax.management.ManagedAttribute;
import javax.management.ManagedOperation;
import javax.management.NotCompliantMBeanException;
import javax.management.NotificationBroadcaster;
import javax.management.NotificationInfo;
import javax.management.NotificationInfos;
import javax.management.ReflectionException;

/**
 * An introspector for MBeans of a certain type.  There is one instance
 * of this class for Standard MBeans, and one for every MXBeanMappingFactory;
 * these two cases correspond to the two concrete subclasses of this abstract
 * class.
 *
 * @param <M> the representation of methods for this kind of MBean:
 * Method for Standard MBeans, ConvertingMethod for MXBeans.
 *
 * @since 1.6
 */
/*
 * Using a type parameter <M> allows us to deal with the fact that
 * Method and ConvertingMethod have no useful common ancestor, on
 * which we could call getName, getGenericReturnType, etc.  A simpler approach
 * would be to wrap every Method in an object that does have a common
 * ancestor with ConvertingMethod.  But that would mean an extra object
 * for every Method in every Standard MBean interface.
 */
abstract class MBeanIntrospector<M> {
    static final class PerInterfaceMap<M>
            extends WeakHashMap<Class<?>, WeakReference<PerInterface<M>>> {}

    /** The map from interface to PerInterface for this type of MBean. */
    abstract PerInterfaceMap<M> getPerInterfaceMap();
    /**
     * The map from concrete implementation class and interface to
     * MBeanInfo for this type of MBean.
     */
    abstract MBeanInfoMap getMBeanInfoMap();

    /** Make an interface analyzer for this type of MBean. */
    abstract MBeanAnalyzer<M> getAnalyzer(Class<?> mbeanInterface)
    throws NotCompliantMBeanException;

    /** True if MBeans with this kind of introspector are MXBeans. */
    abstract boolean isMXBean();

    /** Find the M corresponding to the given Method. */
    abstract M mFrom(Method m);

    /** Get the name of this method. */
    abstract String getName(M m);

    /**
     * Get the return type of this method.  This is the return type
     * of a method in a Java interface, so for MXBeans it is the
     * declared Java type, not the mapped Open Type.
     */
    abstract Type getGenericReturnType(M m);

    /**
     * Get the parameter types of this method in the Java interface
     * it came from.
     */
    abstract Type[] getGenericParameterTypes(M m);

    /**
     * Get the signature of this method as a caller would have to supply
     * it in MBeanServer.invoke.  For MXBeans, the named types will be
     * the mapped Open Types for the parameters.
     */
    abstract String[] getSignature(M m);

    /**
     * Check that this method is valid.  For example, a method in an
     * MXBean interface is not valid if one of its parameters cannot be
     * mapped to an Open Type.
     */
    abstract void checkMethod(M m);

    /**
     * Invoke the method with the given target and arguments.
     *
     * @param cookie Additional information about the target.  For an
     * MXBean, this is the MXBeanLookup associated with the MXBean.
     */
    /*
     * It would be cleaner if the type of the cookie were a
     * type parameter to this class, but that would involve a lot of
     * messy type parameter propagation just to avoid a couple of casts.
     */
    abstract Object invokeM2(M m, Object target, Object[] args, Object cookie)
    throws InvocationTargetException, IllegalAccessException,
            MBeanException;

    /**
     * Test whether the given value is valid for the given parameter of this
     * M.
     */
    abstract boolean validParameter(M m, Object value, int paramNo,
            Object cookie);

    /**
     * Construct an MBeanAttributeInfo for the given attribute based on the
     * given getter and setter.  One but not both of the getter and setter
     * may be null.
     */
    abstract MBeanAttributeInfo getMBeanAttributeInfo(String attributeName,
            M getter, M setter) throws IntrospectionException;

    final String getAttributeDescription(
            String attributeName, String defaultDescription,
            Method getter, Method setter) throws IntrospectionException {
        String g = Introspector.descriptionForElement(getter);
        String s = Introspector.descriptionForElement(setter);
        if (g == null) {
            if (s == null)
                return defaultDescription;
            else
                return s;
        } else if (s == null || g.equals(s)) {
            return g;
        } else {
            throw new IntrospectionException(
                    "Inconsistent @Description on getter and setter for " +
                    "attribute " + attributeName);
        }
    }

    /**
     * Construct an MBeanOperationInfo for the given operation based on
     * the M it was derived from.
     */
    abstract MBeanOperationInfo getMBeanOperationInfo(String operationName,
            M operation);

    /**
     * Get a Descriptor containing fields that MBeans of this kind will
     * always have.  For example, MXBeans will always have "mxbean=true".
     */
    abstract Descriptor getBasicMBeanDescriptor();

    /**
     * Get a Descriptor containing additional fields beyond the ones
     * from getBasicMBeanDescriptor that MBeans whose concrete class
     * is resourceClass will always have.
     */
    abstract Descriptor getMBeanDescriptor(Class<?> resourceClass);

    /**
     * Get any additional Descriptor entries for this introspector instance.
     * If there is a non-default MXBeanMappingFactory, it will appear in
     * this Descriptor.
     * @return Additional Descriptor entries, or an empty Descriptor if none.
     */
    Descriptor getSpecificMBeanDescriptor() {
        return ImmutableDescriptor.EMPTY_DESCRIPTOR;
    }

    void checkCompliance(Class<?> mbeanType) throws NotCompliantMBeanException {
        if (!mbeanType.isInterface() &&
                !mbeanType.isAnnotationPresent(MBean.class) &&
                !Introspector.hasMXBeanAnnotation(mbeanType)) {
            throw new NotCompliantMBeanException("Not an interface and " +
                    "does not have @" + MBean.class.getSimpleName() +
                    " or @" + MXBean.class.getSimpleName() + " annotation: " +
                    mbeanType.getName());
        }
    }

    /**
     * Get the methods to be analyzed to build the MBean interface.
     */
    List<Method> getMethods(final Class<?> mbeanType) throws Exception {
        if (mbeanType.isInterface())
            return Arrays.asList(mbeanType.getMethods());

        final List<Method> methods = newList();
        getAnnotatedMethods(mbeanType, methods);
        return methods;
    }

    final PerInterface<M> getPerInterface(Class<?> mbeanInterface)
    throws NotCompliantMBeanException {
        PerInterfaceMap<M> map = getPerInterfaceMap();
        synchronized (map) {
            WeakReference<PerInterface<M>> wr = map.get(mbeanInterface);
            PerInterface<M> pi = (wr == null) ? null : wr.get();
            if (pi == null) {
                try {
                    MBeanAnalyzer<M> analyzer = getAnalyzer(mbeanInterface);
                    MBeanInfo mbeanInfo =
                            makeInterfaceMBeanInfo(mbeanInterface, analyzer);
                    pi = new PerInterface<M>(mbeanInterface, this, analyzer,
                            mbeanInfo);
                    wr = new WeakReference<PerInterface<M>>(pi);
                    map.put(mbeanInterface, wr);
                } catch (Exception x) {
                    throw Introspector.throwException(mbeanInterface,x);
                }
            }
            return pi;
        }
    }

    /**
     * Make the MBeanInfo skeleton for the given MBean interface using
     * the given analyzer.  This will never be the MBeanInfo of any real
     * MBean (because the getClassName() must be a concrete class), but
     * its MBeanAttributeInfo[] and MBeanOperationInfo[] can be inserted
     * into such an MBeanInfo, and its Descriptor can be the basis for
     * the MBeanInfo's Descriptor.
     */
    private MBeanInfo makeInterfaceMBeanInfo(Class<?> mbeanInterface,
            MBeanAnalyzer<M> analyzer) throws IntrospectionException {
        final MBeanInfoMaker maker = new MBeanInfoMaker();
        analyzer.visit(maker);
        final String defaultDescription =
                "Information on the management interface of the MBean";
        String description = Introspector.descriptionForElement(mbeanInterface);
        if (description == null)
            description = defaultDescription;
        return maker.makeMBeanInfo(mbeanInterface, description);
    }

    /** True if the given getter and setter are consistent. */
    final boolean consistent(M getter, M setter) {
        return (getter == null || setter == null ||
                getGenericReturnType(getter).equals(getGenericParameterTypes(setter)[0]));
    }

    /**
     * Invoke the given M on the given target with the given args and cookie.
     * Wrap exceptions appropriately.
     */
    final Object invokeM(M m, Object target, Object[] args, Object cookie)
    throws MBeanException, ReflectionException {
        try {
            return invokeM2(m, target, args, cookie);
        } catch (InvocationTargetException e) {
            unwrapInvocationTargetException(e);
            throw new RuntimeException(e); // not reached
        } catch (IllegalAccessException e) {
            throw new ReflectionException(e, e.toString());
        }
        /* We do not catch and wrap RuntimeException or Error,
         * because we're in a DynamicMBean, so the logic for DynamicMBeans
         * will do the wrapping.
         */
    }

    /**
     * Invoke the given setter on the given target with the given argument
     * and cookie.  Wrap exceptions appropriately.
     */
    /* If the value is of the wrong type for the method we are about to
     * invoke, we are supposed to throw an InvalidAttributeValueException.
     * Rather than making the check always, we invoke the method, then
     * if it throws an exception we check the type to see if that was
     * what caused the exception.  The assumption is that an exception
     * from an invalid type will arise before any user method is ever
     * called (either in reflection or in OpenConverter).
     */
    final void invokeSetter(String name, M setter, Object target, Object arg,
            Object cookie)
            throws MBeanException, ReflectionException,
            InvalidAttributeValueException {
        try {
            invokeM2(setter, target, new Object[] {arg}, cookie);
        } catch (IllegalAccessException e) {
            throw new ReflectionException(e, e.toString());
        } catch (RuntimeException e) {
            maybeInvalidParameter(name, setter, arg, cookie);
            throw e;
        } catch (InvocationTargetException e) {
            maybeInvalidParameter(name, setter, arg, cookie);
            unwrapInvocationTargetException(e);
        }
    }

    private void maybeInvalidParameter(String name, M setter, Object arg,
            Object cookie)
            throws InvalidAttributeValueException {
        if (!validParameter(setter, arg, 0, cookie)) {
            final String msg =
                    "Invalid value for attribute " + name + ": " + arg;
            throw new InvalidAttributeValueException(msg);
        }
    }

    static boolean isValidParameter(Method m, Object value, int paramNo) {
        Class<?> c = m.getParameterTypes()[paramNo];
        try {
            // Following is expensive but we only call this method to determine
            // if an exception is due to an incompatible parameter type.
            // Plain old c.isInstance doesn't work for primitive types.
            Object a = Array.newInstance(c, 1);
            Array.set(a, 0, value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void
            unwrapInvocationTargetException(InvocationTargetException e)
            throws MBeanException {
        Throwable t = e.getCause();
        if (t instanceof RuntimeException)
            throw (RuntimeException) t;
        else if (t instanceof Error)
            throw (Error) t;
        else
            throw new MBeanException((Exception) t,
                    (t == null ? null : t.toString()));
    }

    /** A visitor that constructs the per-interface MBeanInfo. */
    private class MBeanInfoMaker
            implements MBeanAnalyzer.MBeanVisitor<M, IntrospectionException> {

        public void visitAttribute(String attributeName,
                M getter,
                M setter) throws IntrospectionException {
            MBeanAttributeInfo mbai =
                    getMBeanAttributeInfo(attributeName, getter, setter);

            attrs.add(mbai);
        }

        public void visitOperation(String operationName,
                M operation) {
            MBeanOperationInfo mboi =
                    getMBeanOperationInfo(operationName, operation);

            ops.add(mboi);
        }

        /** Make an MBeanInfo based on the attributes and operations
         *  found in the interface. */
        MBeanInfo makeMBeanInfo(Class<?> mbeanInterface,
                String description) {
            final MBeanAttributeInfo[] attrArray =
                    attrs.toArray(new MBeanAttributeInfo[0]);
            final MBeanOperationInfo[] opArray =
                    ops.toArray(new MBeanOperationInfo[0]);
            final String interfaceClassName =
                    "interfaceClassName=" + mbeanInterface.getName();
            final Descriptor classNameDescriptor =
                    new ImmutableDescriptor(interfaceClassName);
            final Descriptor mbeanDescriptor = getBasicMBeanDescriptor();
            final Descriptor annotatedDescriptor =
                    Introspector.descriptorForElement(mbeanInterface);
            final Descriptor descriptor =
                DescriptorCache.getInstance().union(
                    classNameDescriptor,
                    mbeanDescriptor,
                    annotatedDescriptor);

            return new MBeanInfo(mbeanInterface.getName(),
                    description,
                    attrArray,
                    null,
                    opArray,
                    null,
                    descriptor);
        }

        private final List<MBeanAttributeInfo> attrs = newList();
        private final List<MBeanOperationInfo> ops = newList();
    }

    /*
     * Looking up the MBeanInfo for a given base class (implementation class)
     * is complicated by the fact that we may use the same base class with
     * several different explicit MBean interfaces via the
     * javax.management.StandardMBean class.  It is further complicated
     * by the fact that we have to be careful not to retain a strong reference
     * to any Class object for fear we would prevent a ClassLoader from being
     * garbage-collected.  So we have a first lookup from the base class
     * to a map for each interface that base class might specify giving
     * the MBeanInfo constructed for that base class and interface.
     */
    static class MBeanInfoMap
            extends WeakHashMap<Class<?>, WeakHashMap<Class<?>, MBeanInfo>> {
    }

    /**
     * Return the MBeanInfo for the given resource, based on the given
     * per-interface data.
     */
    final MBeanInfo getMBeanInfo(Object resource, PerInterface<M> perInterface)
    throws NotCompliantMBeanException {
        MBeanInfo mbi =
                getClassMBeanInfo(resource.getClass(), perInterface);
        MBeanNotificationInfo[] notifs;
        try {
            notifs = findNotifications(resource);
        } catch (RuntimeException e) {
            NotCompliantMBeanException x =
                    new NotCompliantMBeanException(e.getMessage());
            x.initCause(e);
            throw x;
        }
        Descriptor d = getSpecificMBeanDescriptor();
        boolean anyNotifs = (notifs != null && notifs.length > 0);
        if (!anyNotifs && ImmutableDescriptor.EMPTY_DESCRIPTOR.equals(d))
            return mbi;
        else {
            d = ImmutableDescriptor.union(d, mbi.getDescriptor());
            return new MBeanInfo(mbi.getClassName(),
                    mbi.getDescription(),
                    mbi.getAttributes(),
                    mbi.getConstructors(),
                    mbi.getOperations(),
                    notifs,
                    d);
        }
    }

    /**
     * Return the basic MBeanInfo for resources of the given class and
     * per-interface data.  This MBeanInfo might not be the final MBeanInfo
     * for instances of the class, because if the class is a
     * NotificationBroadcaster then each instance gets to decide what
     * MBeanNotificationInfo[] to put in its own MBeanInfo.
     */
    final MBeanInfo getClassMBeanInfo(Class<?> resourceClass,
            PerInterface<M> perInterface) {
        MBeanInfoMap map = getMBeanInfoMap();
        synchronized (map) {
            WeakHashMap<Class<?>, MBeanInfo> intfMap = map.get(resourceClass);
            if (intfMap == null) {
                intfMap = new WeakHashMap<Class<?>, MBeanInfo>();
                map.put(resourceClass, intfMap);
            }
            Class<?> intfClass = perInterface.getMBeanInterface();
            MBeanInfo mbi = intfMap.get(intfClass);
            if (mbi == null) {
                MBeanInfo imbi = perInterface.getMBeanInfo();
                Descriptor descriptor =
                        ImmutableDescriptor.union(imbi.getDescriptor(),
                        getMBeanDescriptor(resourceClass));
                mbi = new MBeanInfo(resourceClass.getName(),
                        imbi.getDescription(),
                        imbi.getAttributes(),
                        findConstructors(resourceClass),
                        imbi.getOperations(),
                        (MBeanNotificationInfo[]) null,
                        descriptor);
                intfMap.put(intfClass, mbi);
            }
            return mbi;
        }
    }

    /*
     * Add to "methods" every public method that has the @ManagedAttribute
     * or @ManagedOperation annotation, in the given class or any of
     * its superclasses or superinterfaces.
     *
     * We always add superclass or superinterface methods first, so that
     * the stable sort used by eliminateCovariantMethods will put the
     * method from the most-derived class last.  This means that we will
     * see the version of the @ManagedAttribute (or ...Operation) annotation
     * from that method, which might have a different description or whatever.
     */
    private static void getAnnotatedMethods(Class<?> c, List<Method> methods)
    throws Exception {
        Class<?> sup = c.getSuperclass();
        if (sup != null)
            getAnnotatedMethods(sup, methods);
        Class<?>[] intfs = c.getInterfaces();
        for (Class<?> intf : intfs)
            getAnnotatedMethods(intf, methods);
        for (Method m : c.getMethods()) {
            // We are careful not to add m if it is inherited from a parent
            // class or interface, because duplicate methods lead to nasty
            // behaviour in eliminateCovariantMethods.
            if (m.getDeclaringClass() == c &&
                    (m.isAnnotationPresent(ManagedAttribute.class) ||
                     m.isAnnotationPresent(ManagedOperation.class)))
                methods.add(m);
        }
    }

    static MBeanNotificationInfo[] findNotifications(Object moi) {
        if (moi instanceof NotificationBroadcaster) {
            MBeanNotificationInfo[] mbn =
                    ((NotificationBroadcaster) moi).getNotificationInfo();
            if (mbn != null && mbn.length > 0) {
                MBeanNotificationInfo[] result =
                        new MBeanNotificationInfo[mbn.length];
                for (int i = 0; i < mbn.length; i++) {
                    MBeanNotificationInfo ni = mbn[i];
                    if (ni.getClass() != MBeanNotificationInfo.class)
                        ni = (MBeanNotificationInfo) ni.clone();
                    result[i] = ni;
                }
                return result;
            }
        }
        return findNotificationsFromAnnotations(moi.getClass());
    }

    private static MBeanNotificationInfo[] findNotificationsFromAnnotations(
            Class<?> mbeanClass) {
        Class<?> c = getAnnotatedNotificationInfoClass(mbeanClass);
        if (c == null)
            return null;
        NotificationInfo ni = c.getAnnotation(NotificationInfo.class);
        NotificationInfos nis = c.getAnnotation(NotificationInfos.class);
        List<NotificationInfo> list = newList();
        if (ni != null)
            list.add(ni);
        if (nis != null)
            list.addAll(Arrays.asList(nis.value()));
        if (list.isEmpty())
            return null;
        List<MBeanNotificationInfo> mbnis = newList();
        for (NotificationInfo x : list) {
            // The Descriptor includes any fields explicitly specified by
            // x.descriptorFields(), plus any fields from the contained
            // @Description annotation.
            Descriptor d = new ImmutableDescriptor(x.descriptorFields());
            d = ImmutableDescriptor.union(
                    d, Introspector.descriptorForAnnotation(x.description()));
            MBeanNotificationInfo mbni = new MBeanNotificationInfo(
                    x.types(), x.notificationClass().getName(),
                    x.description().value(), d);
            mbnis.add(mbni);
        }
        return mbnis.toArray(new MBeanNotificationInfo[mbnis.size()]);
    }

    private static final Map<Class<?>, WeakReference<Class<?>>>
            annotatedNotificationInfoClasses = newWeakHashMap();

    private static Class<?> getAnnotatedNotificationInfoClass(Class<?> baseClass) {
        synchronized (annotatedNotificationInfoClasses) {
            WeakReference<Class<?>> wr =
                    annotatedNotificationInfoClasses.get(baseClass);
            if (wr != null)
                return wr.get();
            Class<?> c = null;
            if (baseClass.isAnnotationPresent(NotificationInfo.class) ||
                    baseClass.isAnnotationPresent(NotificationInfos.class)) {
                c = baseClass;
            } else {
                Class<?>[] intfs = baseClass.getInterfaces();
                for (Class<?> intf : intfs) {
                    Class<?> c1 = getAnnotatedNotificationInfoClass(intf);
                    if (c1 != null) {
                        if (c != null) {
                            throw new IllegalArgumentException(
                                    "Class " + baseClass.getName() + " inherits " +
                                    "@NotificationInfo(s) from both " +
                                    c.getName() + " and " + c1.getName());
                        }
                        c = c1;
                    }
                }
            }
            // Record the result of the search.  If no @NotificationInfo(s)
            // were found, c is null, and we store a WeakReference(null).
            // This prevents us from having to search again and fail again.
            annotatedNotificationInfoClasses.put(baseClass,
                    new WeakReference<Class<?>>(c));
            return c;
        }
    }

    private static MBeanConstructorInfo[] findConstructors(Class<?> c) {
        Constructor<?>[] cons = c.getConstructors();
        MBeanConstructorInfo[] mbc = new MBeanConstructorInfo[cons.length];
        for (int i = 0; i < cons.length; i++) {
            String descr = "Public constructor of the MBean";
            Description d = cons[i].getAnnotation(Description.class);
            if (d != null)
                descr = d.value();
            mbc[i] = new MBeanConstructorInfo(descr, cons[i]);
        }
        return mbc;
    }

}
