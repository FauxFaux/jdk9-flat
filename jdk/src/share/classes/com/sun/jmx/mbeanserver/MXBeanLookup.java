/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

import com.sun.jmx.remote.util.EnvHelp;
import java.io.InvalidObjectException;
import static com.sun.jmx.mbeanserver.Util.*;
import java.util.Map;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.security.AccessController;
import javax.management.InstanceAlreadyExistsException;
import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.openmbean.OpenDataException;

/**
 * @since 1.6
 */

/*
 * This class handles the mapping between MXBean references and
 * ObjectNames.  Consider an MXBean interface like this:
 *
 * public interface ModuleMXBean {
 *     ProductMXBean getProduct();
 *     void setProduct(ProductMXBean product);
 * }
 *
 * This defines an attribute called "Product" whose originalType will
 * be ProductMXBean and whose openType will be ObjectName.  The
 * mapping happens as follows.
 *
 * When the MXBean's getProduct method is called, it is supposed to
 * return a reference to another MXBean, or a proxy for another
 * MXBean.  The MXBean layer has to convert this into an ObjectName.
 * If it's a reference to another MXBean, it needs to be able to look
 * up the name under which that MXBean has been registered in this
 * MBeanServer; this is the purpose of the mxbeanToObjectName map.  If
 * it's a proxy, it can check that the MBeanServer matches and if so
 * extract the ObjectName from the proxy.
 *
 * When the setProduct method is called on a proxy for this MXBean,
 * the argument can be either an MXBean reference (only really logical
 * if the proxy has a local MBeanServer) or another proxy.  So the
 * mapping logic is the same as for getProduct on the MXBean.
 *
 * When the MXBean's setProduct method is called, it needs to convert
 * the ObjectName into an object implementing the ProductMXBean
 * interface.  We could have a lookup table that reverses
 * mxbeanToObjectName, but this could violate the general JMX property
 * that you cannot obtain a reference to an MBean object.  So we
 * always use a proxy for this.  However we do have an
 * objectNameToProxy map that allows us to reuse proxy instances.
 *
 * When the getProduct method is called on a proxy for this MXBean, it
 * must convert the returned ObjectName into an instance of
 * ProductMXBean.  Again it can do this by making a proxy.
 *
 * From the above, it is clear that the logic for getX on an MXBean is
 * the same as for setX on a proxy, and vice versa.
 *
 * The above describes the logic for "plain" MXBeanLookup, represented
 * by MXBeanLookup.Plain.  When namespaces enter the picture, we see
 * MXBeanLookup.Prefix.  Here, the idea is that the name of the ModuleMXBean
 * might be a//m:m=m.  In this case, we don't accept a reference to
 * an MXBean object, since that would require different namespaces to know
 * each others' objects.  We only accept proxies.  Suppose you have a proxy
 * for a//m:m=m, call it moduleProxy, and you call
 * moduleProxy.setProduct(productProxy).  Then if productProxy is for
 * a//p:p=p we should convert this to just p:p=p.  If productProxy is for
 * a//b//p:p=p we should convert it to b//p:p=p.  Conversely, if getProduct
 * returns an ObjectName like b//p:p=p then we should convert it into a proxy
 * for a//b//p:p=p.
 */
public abstract class MXBeanLookup {
    private MXBeanLookup(MBeanServerConnection mbsc) {
        this.mbsc = mbsc;
    }

    static MXBeanLookup lookupFor(MBeanServerConnection mbsc, String prefix) {
        if (prefix == null)
            return Plain.lookupFor(mbsc);
        else
            return new Prefix(mbsc, prefix);
    }

    abstract <T> T objectNameToMXBean(ObjectName name, Class<T> type)
            throws InvalidObjectException;

    abstract ObjectName mxbeanToObjectName(Object mxbean)
            throws OpenDataException;

    static class Plain extends MXBeanLookup {
        Plain(MBeanServerConnection mbsc) {
            super(mbsc);
        }

        static Plain lookupFor(MBeanServerConnection mbsc) {
            synchronized (mbscToLookup) {
                WeakReference<Plain> weakLookup = mbscToLookup.get(mbsc);
                Plain lookup = (weakLookup == null) ? null : weakLookup.get();
                if (lookup == null) {
                    lookup = new Plain(mbsc);
                    mbscToLookup.put(mbsc, new WeakReference<Plain>(lookup));
                }
                return lookup;
            }
        }

        @Override
        synchronized <T> T objectNameToMXBean(ObjectName name, Class<T> type) {
            WeakReference<Object> wr = objectNameToProxy.get(name);
            if (wr != null) {
                Object proxy = wr.get();
                if (type.isInstance(proxy))
                    return type.cast(proxy);
            }
            T proxy = JMX.newMXBeanProxy(mbsc, name, type);
            objectNameToProxy.put(name, new WeakReference<Object>(proxy));
            return proxy;
        }

        @Override
        synchronized ObjectName mxbeanToObjectName(Object mxbean)
        throws OpenDataException {
            String wrong;
            if (mxbean instanceof Proxy) {
                InvocationHandler ih = Proxy.getInvocationHandler(mxbean);
                if (ih instanceof MBeanServerInvocationHandler) {
                    MBeanServerInvocationHandler mbsih =
                            (MBeanServerInvocationHandler) ih;
                    if (mbsih.getMBeanServerConnection().equals(mbsc))
                        return mbsih.getObjectName();
                    else
                        wrong = "proxy for a different MBeanServer";
                } else
                    wrong = "not a JMX proxy";
            } else {
                ObjectName name = mxbeanToObjectName.get(mxbean);
                if (name != null)
                    return name;
                wrong = "not an MXBean registered in this MBeanServer";
            }
            String s = (mxbean == null) ?
                "null" : "object of type " + mxbean.getClass().getName();
            throw new OpenDataException(
                    "Could not convert " + s + " to an ObjectName: " + wrong);
            // Message will be strange if mxbean is null but it is not
            // supposed to be.
        }

        synchronized void addReference(ObjectName name, Object mxbean)
        throws InstanceAlreadyExistsException {
            ObjectName existing = mxbeanToObjectName.get(mxbean);
            if (existing != null) {
                String multiname = AccessController.doPrivileged(
                        new GetPropertyAction("jmx.mxbean.multiname"));
                if (!"true".equalsIgnoreCase(multiname)) {
                    throw new InstanceAlreadyExistsException(
                            "MXBean already registered with name " + existing);
                }
            }
            mxbeanToObjectName.put(mxbean, name);
        }

        synchronized boolean removeReference(ObjectName name, Object mxbean) {
            if (name.equals(mxbeanToObjectName.get(mxbean))) {
                mxbeanToObjectName.remove(mxbean);
                return true;
            } else
                return false;
            /* removeReference can be called when the above condition fails,
             * notably if you try to register the same MXBean twice.
             */
        }

        private final WeakIdentityHashMap<Object, ObjectName>
            mxbeanToObjectName = WeakIdentityHashMap.make();
        private final Map<ObjectName, WeakReference<Object>>
            objectNameToProxy = newMap();
        private static WeakIdentityHashMap<MBeanServerConnection,
                                           WeakReference<Plain>>
            mbscToLookup = WeakIdentityHashMap.make();
    }

    private static class Prefix extends MXBeanLookup {
        private final String prefix;

        Prefix(MBeanServerConnection mbsc, String prefix) {
            super(mbsc);
            this.prefix = prefix;
        }

        @Override
        <T> T objectNameToMXBean(ObjectName name, Class<T> type)
        throws InvalidObjectException {
            String domain = prefix + name.getDomain();
            try {
                name = switchDomain(domain, name);
            } catch (MalformedObjectNameException e) {
                throw EnvHelp.initCause(
                        new InvalidObjectException(e.getMessage()), e);
            }
            return JMX.newMXBeanProxy(mbsc, name, type);
        }

        @Override
        ObjectName mxbeanToObjectName(Object mxbean)
        throws OpenDataException {
            ObjectName name = proxyToObjectName(mxbean);
            String domain = name.getDomain();
            if (!domain.startsWith(prefix)) {
                throw new OpenDataException(
                        "Proxy's name does not start with " + prefix + ": " + name);
            }
            try {
                name = switchDomain(domain.substring(prefix.length()), name);
            } catch (MalformedObjectNameException e) {
                throw EnvHelp.initCause(new OpenDataException(e.getMessage()), e);
            }
            return name;
        }
    }

    ObjectName proxyToObjectName(Object proxy) {
        InvocationHandler ih = Proxy.getInvocationHandler(proxy);
        if (ih instanceof MBeanServerInvocationHandler) {
            MBeanServerInvocationHandler mbsih =
                    (MBeanServerInvocationHandler) ih;
            if (mbsih.getMBeanServerConnection().equals(mbsc))
                return mbsih.getObjectName();
        }
        return null;
    }

    static MXBeanLookup getLookup() {
        return currentLookup.get();
    }

    static void setLookup(MXBeanLookup lookup) {
        currentLookup.set(lookup);
    }

    // Method temporarily added until we have ObjectName.switchDomain in the
    // public API.  Note that this method DOES NOT PRESERVE the order of
    // keys in the ObjectName so it must not be used in the final release.
    static ObjectName switchDomain(String domain, ObjectName name)
            throws MalformedObjectNameException {
        return new ObjectName(domain, name.getKeyPropertyList());
    }

    private static final ThreadLocal<MXBeanLookup> currentLookup =
            new ThreadLocal<MXBeanLookup>();

    final MBeanServerConnection mbsc;
}
