/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.jmx.namespace;


import com.sun.jmx.defaults.JmxProperties;
import com.sun.jmx.interceptor.MBeanServerInterceptor;

import com.sun.jmx.mbeanserver.Util;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.InvalidAttributeValueException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.NotCompliantMBeanException;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.OperationsException;
import javax.management.QueryExp;
import javax.management.ReflectionException;
import javax.management.RuntimeOperationsException;
import javax.management.loading.ClassLoaderRepository;
import javax.management.namespace.JMXNamespace;

/**
 * This interceptor wraps a JMXNamespace, and performs
 * {@code ObjectName} rewriting. {@code HandlerInterceptor} are
 * usually created and managed by a {@link NamespaceDispatcher} or
 * {@link DomainDispatcher}.
 * <p><b>
 * This API is a Sun internal API and is subject to changes without notice.
 * </b></p>
 * @since 1.7
 */
public abstract class HandlerInterceptor<T extends JMXNamespace>
        extends RoutingMBeanServerConnection<MBeanServer>
        implements MBeanServerInterceptor {

    /**
     * A logger for this class.
     **/
    private static final Logger LOG = JmxProperties.NAMESPACE_LOGGER;

    // The wrapped JMXNamespace
    private final T handler;

    /**
     * Creates a new instance of HandlerInterceptor
     */
    public HandlerInterceptor(T handler) {
        if (handler == null) throw new IllegalArgumentException("null");
        this.handler = handler;
    }

    @Override
    protected MBeanServer source() {
         return handler.getSourceServer();
    }

    // The MBeanServer on which getClassLoader / getClassLoaderFor
    // will be called.
    // The NamespaceInterceptor overrides this method - so that it
    // getClassLoader / getClassLoaderFor don't trigger the loop
    // detection mechanism.
    //
    MBeanServer getServerForLoading() {
         return source();
    }

    T getNamespace() {
        return handler;
    }

    // If the underlying JMXNamespace throws an IO, the IO will be
    // wrapped in a RuntimeOperationsException.
    RuntimeException handleIOException(IOException x,String fromMethodName,
            Object... params) {
            // Must do something here?
        if (LOG.isLoggable(Level.FINEST)) {
            LOG.finest("IO Exception in "+fromMethodName+": "+x+
                    " - "+" rethrowing as RuntimeOperationsException.");
        }
        throw new RuntimeOperationsException(
                    Util.newRuntimeIOException(x));
    }

    // From MBeanServer: catch & handles IOException
    @Override
    public AttributeList getAttributes(ObjectName name, String[] attributes)
        throws InstanceNotFoundException, ReflectionException {
        try {
            return super.getAttributes(name, attributes);
        } catch (IOException ex) {
            throw handleIOException(ex,"getAttributes",name,attributes);
        }
    }

    // From MBeanServer
    public ClassLoader getClassLoaderFor(ObjectName mbeanName)
        throws InstanceNotFoundException {
        final ObjectName sourceName = toSourceOrRuntime(mbeanName);
        try {
            check(mbeanName,null,"getClassLoaderFor");
            return getServerForLoading().getClassLoaderFor(sourceName);
        } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
        }
    }


    // From MBeanServer
    public ClassLoader getClassLoader(ObjectName loaderName)
        throws InstanceNotFoundException {
        final ObjectName sourceName = toSourceOrRuntime(loaderName);
        try {
            check(loaderName,null,"getClassLoader");
            return getServerForLoading().getClassLoader(sourceName);
        } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
        }
    }

    // From MBeanServer
    public ObjectInstance registerMBean(Object object, ObjectName name)
        throws InstanceAlreadyExistsException, MBeanRegistrationException,
            NotCompliantMBeanException {
        final ObjectName sourceName = newSourceMBeanName(name);
        try {
            checkCreate(name,object.getClass().getName(),"registerMBean");
            return processOutputInstance(
                    source().registerMBean(object,sourceName));
        } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
        }
    }

    // From MBeanServer: catch & handles IOException
    @Override
    public void removeNotificationListener(ObjectName name, ObjectName listener)
        throws InstanceNotFoundException, ListenerNotFoundException {
        try {
            super.removeNotificationListener(name, listener);
        } catch (IOException ex) {
            throw handleIOException(ex,"removeNotificationListener",name,listener);
        }
    }

    // From MBeanServer: catch & handles IOException
    @Override
    public String getDefaultDomain() {
        try {
            return super.getDefaultDomain();
        } catch (IOException ex) {
            throw handleIOException(ex,"getDefaultDomain");
        }
    }

    // From MBeanServer: catch & handles IOException
    @Override
    public String[] getDomains() {
        try {
            return super.getDomains();
        } catch (IOException ex) {
            throw handleIOException(ex,"getDomains");
        }
    }

    // From MBeanServer: catch & handles IOException
    @Override
    public Integer getMBeanCount() {
        try {
            return super.getMBeanCount();
        } catch (IOException ex) {
            throw handleIOException(ex,"getMBeanCount");
        }
    }

    // From MBeanServer: catch & handles IOException
    @Override
    public void setAttribute(ObjectName name, Attribute attribute)
        throws InstanceNotFoundException, AttributeNotFoundException,
            InvalidAttributeValueException, MBeanException,
            ReflectionException {
        try {
            super.setAttribute(name, attribute);
        } catch (IOException ex) {
            throw handleIOException(ex,"setAttribute",name, attribute);
        }
    }

    // From MBeanServer: catch & handles IOException
    @Override
    public Set<ObjectName> queryNames(ObjectName name, QueryExp query) {
        try {
            return super.queryNames(name, query);
        } catch (IOException ex) {
            throw handleIOException(ex,"queryNames",name, query);
        }
    }

    // From MBeanServer: catch & handles IOException
    @Override
    public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query) {
        try {
            return super.queryMBeans(name, query);
        } catch (IOException ex) {
            throw handleIOException(ex,"queryMBeans",name, query);
        }
    }

    // From MBeanServer: catch & handles IOException
    @Override
    public boolean isInstanceOf(ObjectName name, String className)
        throws InstanceNotFoundException {
        try {
            return super.isInstanceOf(name, className);
        } catch (IOException ex) {
            throw handleIOException(ex,"isInstanceOf",name, className);
        }
    }

    // From MBeanServer: catch & handles IOException
    @Override
    public ObjectInstance createMBean(String className, ObjectName name)
        throws ReflectionException, InstanceAlreadyExistsException,
            MBeanRegistrationException, MBeanException,
            NotCompliantMBeanException {
        try {
            return super.createMBean(className, name);
        } catch (IOException ex) {
            throw handleIOException(ex,"createMBean",className, name);
        }
    }

    // From MBeanServer: catch & handles IOException
    @Override
    public ObjectInstance createMBean(String className, ObjectName name,
                        ObjectName loaderName)
        throws ReflectionException, InstanceAlreadyExistsException,
                MBeanRegistrationException, MBeanException,
                NotCompliantMBeanException, InstanceNotFoundException {
        try {
            return super.createMBean(className, name, loaderName);
        } catch (IOException ex) {
            throw handleIOException(ex,"createMBean",className, name, loaderName);
        }
    }

    // From MBeanServer: catch & handles IOException
    @Override
    public Object getAttribute(ObjectName name, String attribute)
        throws MBeanException, AttributeNotFoundException,
            InstanceNotFoundException, ReflectionException {
        try {
            return super.getAttribute(name, attribute);
        } catch (IOException ex) {
            throw handleIOException(ex,"getAttribute",name, attribute);
        }
    }

    // From MBeanServer: catch & handles IOException
    @Override
    public void removeNotificationListener(ObjectName name, ObjectName listener,
                            NotificationFilter filter, Object handback)
        throws InstanceNotFoundException, ListenerNotFoundException {
        try {
            super.removeNotificationListener(name, listener, filter, handback);
        } catch (IOException ex) {
            throw handleIOException(ex,"removeNotificationListener",name,
                    listener, filter, handback);
        }
    }

    // From MBeanServer: catch & handles IOException
    @Override
    public void removeNotificationListener(ObjectName name,
                      NotificationListener listener, NotificationFilter filter,
                      Object handback)
        throws InstanceNotFoundException, ListenerNotFoundException {
        try {
            super.removeNotificationListener(name, listener, filter, handback);
        } catch (IOException ex) {
            throw handleIOException(ex,"removeNotificationListener",name,
                    listener, filter, handback);
        }
    }

    // From MBeanServer: catch & handles IOException
    @Override
    public void removeNotificationListener(ObjectName name,
                NotificationListener listener)
        throws InstanceNotFoundException, ListenerNotFoundException {
        try {
            super.removeNotificationListener(name, listener);
        } catch (IOException ex) {
            throw handleIOException(ex,"removeNotificationListener",name,
                    listener);
        }
    }

    // From MBeanServer: catch & handles IOException
    @Override
    public void addNotificationListener(ObjectName name,
                    NotificationListener listener, NotificationFilter filter,
                    Object handback) throws InstanceNotFoundException {
        try {
            super.addNotificationListener(name, listener, filter, handback);
        } catch (IOException ex) {
            throw handleIOException(ex,"addNotificationListener",name,
                    listener, filter, handback);
        }
    }

    // From MBeanServer: catch & handles IOException
    @Override
    public void addNotificationListener(ObjectName name, ObjectName listener,
                NotificationFilter filter, Object handback)
        throws InstanceNotFoundException {
        try {
            super.addNotificationListener(name, listener, filter, handback);
        } catch (IOException ex) {
            throw handleIOException(ex,"addNotificationListener",name,
                    listener, filter, handback);
        }
    }

    // From MBeanServer: catch & handles IOException
    @Override
    public boolean isRegistered(ObjectName name) {
        try {
            return super.isRegistered(name);
        } catch (IOException ex) {
            throw handleIOException(ex,"isRegistered",name);
        }
    }

    // From MBeanServer: catch & handles IOException
    @Override
    public void unregisterMBean(ObjectName name)
        throws InstanceNotFoundException, MBeanRegistrationException {
        try {
            super.unregisterMBean(name);
        } catch (IOException ex) {
            throw handleIOException(ex,"unregisterMBean",name);
        }
    }

    // From MBeanServer: catch & handles IOException
    @Override
    public MBeanInfo getMBeanInfo(ObjectName name)
        throws InstanceNotFoundException, IntrospectionException,
            ReflectionException {
        try {
            return super.getMBeanInfo(name);
        } catch (IOException ex) {
            throw handleIOException(ex,"getMBeanInfo",name);
        }
    }

    // From MBeanServer: catch & handles IOException
    @Override
    public ObjectInstance getObjectInstance(ObjectName name)
        throws InstanceNotFoundException {
        try {
            return super.getObjectInstance(name);
        } catch (IOException ex) {
            throw handleIOException(ex,"getObjectInstance",name);
        }
    }

    // From MBeanServer: catch & handles IOException
    @Override
    public ObjectInstance createMBean(String className, ObjectName name,
                Object[] params, String[] signature)
        throws ReflectionException, InstanceAlreadyExistsException,
            MBeanRegistrationException, MBeanException,
            NotCompliantMBeanException {
        try {
            return super.createMBean(className, name, params, signature);
        } catch (IOException ex) {
            throw handleIOException(ex,"createMBean",className, name,
                    params, signature);
        }
    }

    // From MBeanServer: catch & handles IOException
    @Override
    public ObjectInstance createMBean(String className, ObjectName name,
                ObjectName loaderName, Object[] params, String[] signature)
        throws ReflectionException, InstanceAlreadyExistsException,
            MBeanRegistrationException, MBeanException,
            NotCompliantMBeanException, InstanceNotFoundException {
        try {
            return super.createMBean(className, name, loaderName, params,
                    signature);
        } catch (IOException ex) {
            throw handleIOException(ex,"createMBean",className, name,loaderName,
                    params, signature);
        }
    }

    // From MBeanServer: catch & handles IOException
    @Override
    public AttributeList setAttributes(ObjectName name,AttributeList attributes)
    throws InstanceNotFoundException, ReflectionException {
        try {
            return super.setAttributes(name, attributes);
        } catch (IOException ex) {
            throw handleIOException(ex,"setAttributes",name, attributes);
        }
    }

    // From MBeanServer: catch & handles IOException
    @Override
    public Object invoke(ObjectName name, String operationName, Object[] params,
                String[] signature)
        throws InstanceNotFoundException, MBeanException, ReflectionException {
        try {
            return super.invoke(name, operationName, params, signature);
        } catch (IOException ex) {
            throw handleIOException(ex,"invoke",name, operationName,
                    params, signature);
        }
    }

    //
    //  These methods are inherited from MBeanServer....
    //

    /**
     * This method should never be called.
     * Throws UnsupportedOperationException.
     */
    public Object instantiate(String className)
            throws ReflectionException, MBeanException {
        if (LOG.isLoggable(Level.FINE))
            LOG.fine("call to unsupported instantiate method: " +
                    "trowing UnsupportedOperationException");
        throw new UnsupportedOperationException("Not applicable.");
    }

    /**
     * This method should never be called.
     * Throws UnsupportedOperationException.
     */
    public Object instantiate(String className, ObjectName loaderName)
            throws ReflectionException, MBeanException,
            InstanceNotFoundException {
        if (LOG.isLoggable(Level.FINE))
            LOG.fine("call to unsupported method: instantiate(...) -" +
                    "throwing UnsupportedOperationException");
        throw new UnsupportedOperationException("Not applicable.");
    }

    /**
     * This method should never be called.
     * Throws UnsupportedOperationException.
     */
    public Object instantiate(String className, Object[] params,
            String[] signature) throws ReflectionException, MBeanException {
        if (LOG.isLoggable(Level.FINE))
            LOG.fine("call to unsupported method: instantiate(...) -" +
                    "throwing UnsupportedOperationException");
        throw new UnsupportedOperationException("Not applicable.");
    }

    /**
     * This method should never be called.
     * Throws UnsupportedOperationException.
     */
    public Object instantiate(String className, ObjectName loaderName,
            Object[] params, String[] signature)
            throws ReflectionException, MBeanException,
            InstanceNotFoundException {
        if (LOG.isLoggable(Level.FINE))
            LOG.fine("call to unsupported method: instantiate(...) -" +
                    "throwing UnsupportedOperationException");
        throw new UnsupportedOperationException("Not applicable.");
    }

    /**
     * This method should never be called.
     * Throws UnsupportedOperationException.
     */
    @Deprecated
    public ObjectInputStream deserialize(ObjectName name, byte[] data)
            throws InstanceNotFoundException, OperationsException {
        if (LOG.isLoggable(Level.FINE))
            LOG.fine("call to unsupported method: deserialize(...) -" +
                    "throwing UnsupportedOperationException");
        throw new UnsupportedOperationException("Not applicable.");
    }

    /**
     * This method should never be called.
     * Throws UnsupportedOperationException.
     */
    @Deprecated
    public ObjectInputStream deserialize(String className, byte[] data)
            throws OperationsException, ReflectionException {
        if (LOG.isLoggable(Level.FINE))
            LOG.fine("call to unsupported method: deserialize(...) -" +
                    "throwing UnsupportedOperationException");
        throw new UnsupportedOperationException("Not applicable.");
    }

    /**
     * This method should never be called.
     * Throws UnsupportedOperationException.
     */
    @Deprecated
    public ObjectInputStream deserialize(String className,
            ObjectName loaderName, byte[] data)
            throws InstanceNotFoundException, OperationsException,
            ReflectionException {
        if (LOG.isLoggable(Level.FINE))
            LOG.fine("call to unsupported method: deserialize(...) -" +
                    "throwing UnsupportedOperationException");
        throw new UnsupportedOperationException("Not applicable.");
    }

    /**
     * This method should never be called.
     * Throws UnsupportedOperationException.
     */
    public ClassLoaderRepository getClassLoaderRepository() {
        if (LOG.isLoggable(Level.FINE))
            LOG.fine("call to unsupported method: getClassLoaderRepository() -" +
                    "throwing UnsupportedOperationException");
        throw new UnsupportedOperationException("Not applicable.");
    }

    static RuntimeException newUnsupportedException(String namespace) {
        return new RuntimeOperationsException(
            new UnsupportedOperationException(
                "Not supported in this namespace: "+namespace));
    }

}
