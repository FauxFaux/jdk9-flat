/*
 * Copyright 2003-2007 Sun Microsystems, Inc.  All Rights Reserved.
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


package javax.management.remote;

import com.sun.jmx.remote.util.EnvHelp;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.util.NoSuchElementException;
import javax.management.MBeanInfo;  // for javadoc
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.ObjectName;
import javax.management.event.EventClientDelegate;

/**
 * <p>Superclass of every connector server.  A connector server is
 * attached to an MBean server.  It listens for client connection
 * requests and creates a connection for each one.</p>
 *
 * <p>A connector server is associated with an MBean server either by
 * registering it in that MBean server, or by passing the MBean server
 * to its constructor.</p>
 *
 * <p>A connector server is inactive when created.  It only starts
 * listening for client connections when the {@link #start() start}
 * method is called.  A connector server stops listening for client
 * connections when the {@link #stop() stop} method is called or when
 * the connector server is unregistered from its MBean server.</p>
 *
 * <p>Stopping a connector server does not unregister it from its
 * MBean server.  A connector server once stopped cannot be
 * restarted.</p>
 *
 * <p>Each time a client connection is made or broken, a notification
 * of class {@link JMXConnectionNotification} is emitted.</p>
 *
 * @since 1.5
 */
public abstract class JMXConnectorServer
        extends NotificationBroadcasterSupport
        implements JMXConnectorServerMBean, MBeanRegistration, JMXAddressable {

    /**
     * <p>Name of the attribute that specifies the authenticator for a
     * connector server.  The value associated with this attribute, if
     * any, must be an object that implements the interface {@link
     * JMXAuthenticator}.</p>
     */
    public static final String AUTHENTICATOR =
        "jmx.remote.authenticator";

     /**
      * <p>Name of the attribute that specifies whether this connector
      * server can delegate notification handling to the
      * {@linkplain javax.management.event Event Service}.
      * The value associated with
      * this attribute, if any, is a String, which must be equal,
      * ignoring case, to {@code "true"} or {@code "false"}.</p>
      *
      * <p>Not all connector servers will understand this attribute, but the
      * standard {@linkplain javax.management.remote.rmi.RMIConnectorServer
      * RMI Connector Server} does.</p>
      *
      * <p>If this attribute is not present, then the system property of the
      * same name (<code>{@value}</code>) is consulted. If that is not set
      * either, then the Event Service is used if the connector server
      * supports it.</p>
      *
      * @since 1.7
      */
     public static final String DELEGATE_TO_EVENT_SERVICE =
         "jmx.remote.delegate.event.service";

     /**
      * <p>Name of the attribute that specifies whether this connector
      * server simulates the existence of the {@link EventClientDelegate}
      * MBean. The value associated with this attribute, if any, must
      * be a string that is equal to {@code "true"} or {@code "false"},
      * ignoring case. If it is {@code "true"}, then the connector server
      * will simulate an EventClientDelegate MBean, as described in {@link
      * EventClientDelegate#newForwarder}. This MBean is needed for {@link
      * javax.management.event.EventClient EventClient} to function correctly.</p>
      *
      * <p>Not all connector servers will understand this attribute, but the
      * standard {@linkplain javax.management.remote.rmi.RMIConnectorServer
      * RMI Connector Server} does.  For a connector server that understands
      * this attribute, the default value is {@code "true"}.</p>
      *
      * @since 1.7
      */
     public static final String EVENT_CLIENT_DELEGATE_FORWARDER =
         "jmx.remote.event.client.delegate.forwarder";

    /**
     * <p>Constructs a connector server that will be registered as an
     * MBean in the MBean server it is attached to.  This constructor
     * is typically called by one of the <code>createMBean</code>
     * methods when creating, within an MBean server, a connector
     * server that makes it available remotely.</p>
     */
    public JMXConnectorServer() {
        this(null);
    }

    /**
     * <p>Constructs a connector server that is attached to the given
     * MBean server.  A connector server that is created in this way
     * can be registered in a different MBean server, or not registered
     * in any MBean server.</p>
     *
     * @param mbeanServer the MBean server that this connector server
     * is attached to.  Null if this connector server will be attached
     * to an MBean server by being registered in it.
     */
    public JMXConnectorServer(MBeanServer mbeanServer) {
        insertUserMBeanServer(mbeanServer);
    }

    /**
     * <p>Returns the MBean server that this connector server is
     * attached to, or the first in a chain of user-added
     * {@link MBeanServerForwarder}s, if any.</p>
     *
     * @return the MBean server that this connector server is attached
     * to, or null if it is not yet attached to an MBean server.
     *
     * @see #setMBeanServerForwarder
     * @see #getSystemMBeanServer
     */
    public synchronized MBeanServer getMBeanServer() {
        return userMBeanServer;
    }

    public synchronized void setMBeanServerForwarder(MBeanServerForwarder mbsf) {
        if (mbsf == null)
            throw new IllegalArgumentException("Invalid null argument: mbsf");

        if (userMBeanServer != null)
            mbsf.setMBeanServer(userMBeanServer);
        insertUserMBeanServer(mbsf);
    }

    /**
     * <p>Remove a forwarder from the chain of forwarders.  The forwarder can
     * be in the system chain or the user chain.  On successful return from
     * this method, the first occurrence in the chain of an object that is
     * {@linkplain Object#equals equal} to {@code mbsf} will have been
     * removed.</p>
     * @param mbsf the forwarder to remove
     * @throws NoSuchElementException if there is no occurrence of {@code mbsf}
     * in the chain.
     * @throws IllegalArgumentException if {@code mbsf} is null.
     */
    public synchronized void removeMBeanServerForwarder(MBeanServerForwarder mbsf) {
        if (mbsf == null)
            throw new IllegalArgumentException("Invalid null argument: mbsf");

        MBeanServerForwarder prev = null;
        MBeanServer curr = systemMBeanServer;
        while (curr instanceof MBeanServerForwarder && !mbsf.equals(curr)) {
            prev = (MBeanServerForwarder) curr;
            curr = prev.getMBeanServer();
        }
        if (!(curr instanceof MBeanServerForwarder))
            throw new NoSuchElementException("MBeanServerForwarder not in chain");
        MBeanServerForwarder deleted = (MBeanServerForwarder) curr;
        MBeanServer next = deleted.getMBeanServer();
        if (prev != null)
            prev.setMBeanServer(next);
        if (systemMBeanServer == deleted)
            systemMBeanServer = next;
        if (userMBeanServer == deleted)
            userMBeanServer = next;
    }

    /*
     * Set userMBeanServer to mbs and arrange for the end of the chain of
     * system MBeanServerForwarders to point to it.  See the comment before
     * the systemMBeanServer and userMBeanServer field declarations.
     */
    private void insertUserMBeanServer(MBeanServer mbs) {
        MBeanServerForwarder lastSystemMBSF = null;
        for (MBeanServer mbsi = systemMBeanServer;
             mbsi != userMBeanServer;
             mbsi = lastSystemMBSF.getMBeanServer()) {
            lastSystemMBSF = (MBeanServerForwarder) mbsi;
        }
        userMBeanServer = mbs;
        if (lastSystemMBSF == null)
            systemMBeanServer = mbs;
        else
            lastSystemMBSF.setMBeanServer(mbs);
    }

    /**
     * <p>Returns the first item in the chain of system and then user
     * forwarders.  In the simplest case, a {@code JMXConnectorServer}
     * is connected directly to an {@code MBeanServer}.  But there can
     * also be a chain of {@link MBeanServerForwarder}s between the two.
     * This chain consists of two sub-chains: first the <em>system chain</em>
     * and then the <em>user chain</em>.  Incoming requests are given to the
     * first forwarder in the system chain.  Each forwarder can handle
     * a request itself, or more usually forward it to the next forwarder,
     * perhaps with some extra behavior such as logging or security
     * checking before or after the forwarding.  The last forwarder in
     * the system chain is followed by the first forwarder in the user
     * chain.</p>
     *
     * <p>The <em>system chain</em> is usually
     * defined by a connector server based on the environment Map;
     * see {@link JMXConnectorServerFactory#newJMXConnectorServer}.  Allowing the
     * connector server to define its forwarders in this way ensures that
     * they are in the correct order - some forwarders need to be inserted
     * before others for correct behavior.  It is possible to modify the
     * system chain, for example using {@link #setSystemMBeanServerForwarder} or
     * {@link #removeMBeanServerForwarder}, but in that case the system
     * chain is no longer guaranteed to be correct.</p>
     *
     * <p>The <em>user chain</em> is defined by calling {@link
     * #setMBeanServerForwarder} to insert forwarders at the head of the user
     * chain.</p>
     *
     * <p>If there are no forwarders in either chain, then both
     * {@link #getMBeanServer()} and {@code getSystemMBeanServer()} will
     * return the {@code MBeanServer} for this connector server.  If there
     * are forwarders in the user chain but not the system chain, then
     * both methods will return the first forwarder in the user chain.
     * If there are forwarders in the system chain but not the user chain,
     * then {@code getSystemMBeanServer()} will return the first forwarder
     * in the system chain, and {@code getMBeanServer()} will return the
     * {@code MBeanServer} for this connector server.  Finally, if there
     * are forwarders in each chain then {@code getSystemMBeanServer()}
     * will return the first forwarder in the system chain, and {@code
     * getMBeanServer()} will return the first forwarder in the user chain.</p>
     *
     * <p>This code illustrates how the chains can be traversed:</p>
     *
     * <pre>
     * JMXConnectorServer cs;
     * System.out.println("system chain:");
     * MBeanServer mbs = cs.getSystemMBeanServer();
     * while (true) {
     *     if (mbs == cs.getMBeanServer())
     *         System.out.println("user chain:");
     *     if (!(mbs instanceof MBeanServerForwarder))
     *         break;
     *     MBeanServerForwarder mbsf = (MBeanServerForwarder) mbs;
     *     System.out.println("--forwarder: " + mbsf);
     *     mbs = mbsf.getMBeanServer();
     * }
     * System.out.println("--MBean Server");
     * </pre>
     *
     * @return the first item in the system chain of forwarders.
     *
     * @see #setSystemMBeanServerForwarder
     */
    public synchronized MBeanServer getSystemMBeanServer() {
        return systemMBeanServer;
    }

    /**
     * <p>Inserts an object that intercepts requests for the MBean server
     * that arrive through this connector server.  This object will be
     * supplied as the <code>MBeanServer</code> for any new connection
     * created by this connector server.  Existing connections are
     * unaffected.</p>
     *
     * <p>This method can be called more than once with different
     * {@link MBeanServerForwarder} objects.  The result is a chain
     * of forwarders.  The last forwarder added is the first in the chain.</p>
     *
     * <p>This method modifies the system chain of {@link MBeanServerForwarder}s.
     * Usually user code should change the user chain instead, via
     * {@link #setMBeanServerForwarder}.</p>
     *
     * <p>Not all connector servers support a system chain of forwarders.
     * Calling this method on a connector server that does not will produce an
     * {@link UnsupportedOperationException}.</p>
     *
     * <p>Suppose {@code mbs} is the result of {@link #getSystemMBeanServer()}
     * before calling this method.  If {@code mbs} is not null, then
     * {@code mbsf.setMBeanServer(mbs)} will be called.  If doing so
     * produces an exception, this method throws the same exception without
     * any other effect.  If {@code mbs} is null, or if the call to
     * {@code mbsf.setMBeanServer(mbs)} succeeds, then this method will
     * return normally and {@code getSystemMBeanServer()} will then return
     * {@code mbsf}.</p>
     *
     * <p>The result of {@link #getMBeanServer()} is unchanged by this method.</p>
     *
     * @param mbsf the new <code>MBeanServerForwarder</code>.
     *
     * @throws IllegalArgumentException if the call to {@link
     * MBeanServerForwarder#setMBeanServer mbsf.setMBeanServer} fails
     * with <code>IllegalArgumentException</code>, or if
     * <code>mbsf</code> is null.
     *
     * @throws UnsupportedOperationException if
     * {@link #supportsSystemMBeanServerForwarder} returns false.
     *
     * @see #getSystemMBeanServer()
     */
    public synchronized void setSystemMBeanServerForwarder(
            MBeanServerForwarder mbsf) {
        if (mbsf == null)
            throw new IllegalArgumentException("Invalid null argument: mbsf");
        mustSupportSystemMBSF();

        if (systemMBeanServer != null)
            mbsf.setMBeanServer(systemMBeanServer);
        systemMBeanServer = mbsf;
    }

    /**
     * <p>Returns true if this connector server supports a system chain of
     * {@link MBeanServerForwarder}s.  The default implementation of this
     * method returns false.  Connector servers that do support the system
     * chain must override this method to return true.
     *
     * @return true if this connector server supports the system chain of
     * forwarders.
     */
    public boolean supportsSystemMBeanServerForwarder() {
        return false;
    }

    private void mustSupportSystemMBSF() {
        if (!supportsSystemMBeanServerForwarder()) {
            throw new UnsupportedOperationException(
                    "System MBeanServerForwarder not supported by this " +
                    "connector server");
        }
    }

    /**
     * <p>Install {@link MBeanServerForwarder}s in the system chain
     * based on the attributes in the given {@code Map}.  A connector
     * server that {@linkplain #supportsSystemMBeanServerForwarder supports}
     * a system chain of {@code MBeanServerForwarder}s can call this method
     * to add forwarders to that chain based on the contents of {@code env}.
     * In order:</p>
     *
     * <ul>
     *
     * <li>If {@link #EVENT_CLIENT_DELEGATE_FORWARDER} is absent, or is
     * present with the value {@code "true"}, then a forwarder with the
     * functionality of {@link EventClientDelegate#newForwarder} is inserted
     * at the start of the system chain.</li>
     *
     * </ul>
     *
     * <p>For {@code EVENT_CLIENT_DELEGATE_FORWARDER}, if the
     * attribute is absent from the {@code Map} and a system property
     * of the same name is defined, then the value of the system
     * property is used as if it were in the {@code Map}.
     *
     * <p>Attributes in {@code env} that are not listed above are ignored
     * by this method.</p>
     *
     * @throws UnsupportedOperationException if {@link
     * #supportsSystemMBeanServerForwarder} is false.
     */
    protected void installStandardForwarders(Map<String, ?> env) {
        mustSupportSystemMBSF();

        // Remember that forwarders must be added in reverse order!

        boolean ecd = EnvHelp.computeBooleanFromString(
                env, EVENT_CLIENT_DELEGATE_FORWARDER, false, true);

        if (ecd) {
            MBeanServerForwarder mbsf = EventClientDelegate.newForwarder();
            setSystemMBeanServerForwarder(mbsf);
        }
    }

    public String[] getConnectionIds() {
        synchronized (connectionIds) {
            return connectionIds.toArray(new String[connectionIds.size()]);
        }
    }

    /**
     * <p>Returns a client stub for this connector server.  A client
     * stub is a serializable object whose {@link
     * JMXConnector#connect(Map) connect} method can be used to make
     * one new connection to this connector server.</p>
     *
     * <p>A given connector need not support the generation of client
     * stubs.  However, the connectors specified by the JMX Remote API do
     * (JMXMP Connector and RMI Connector).</p>
     *
     * <p>The default implementation of this method uses {@link
     * #getAddress} and {@link JMXConnectorFactory} to generate the
     * stub, with code equivalent to the following:</p>
     *
     * <pre>
     * JMXServiceURL addr = {@link #getAddress() getAddress()};
     * return {@link JMXConnectorFactory#newJMXConnector(JMXServiceURL, Map)
     *          JMXConnectorFactory.newJMXConnector(addr, env)};
     * </pre>
     *
     * <p>A connector server for which this is inappropriate must
     * override this method so that it either implements the
     * appropriate logic or throws {@link
     * UnsupportedOperationException}.</p>
     *
     * @param env client connection parameters of the same sort that
     * could be provided to {@link JMXConnector#connect(Map)
     * JMXConnector.connect(Map)}.  Can be null, which is equivalent
     * to an empty map.
     *
     * @return a client stub that can be used to make a new connection
     * to this connector server.
     *
     * @exception UnsupportedOperationException if this connector
     * server does not support the generation of client stubs.
     *
     * @exception IllegalStateException if the JMXConnectorServer is
     * not started (see {@link JMXConnectorServerMBean#isActive()}).
     *
     * @exception IOException if a communications problem means that a
     * stub cannot be created.
     **/
    public JMXConnector toJMXConnector(Map<String,?> env)
        throws IOException
    {
        if (!isActive()) throw new
            IllegalStateException("Connector is not active");
        JMXServiceURL addr = getAddress();
        return JMXConnectorFactory.newJMXConnector(addr, env);
    }

    /**
     * <p>Returns an array indicating the notifications that this MBean
     * sends. The implementation in <code>JMXConnectorServer</code>
     * returns an array with one element, indicating that it can emit
     * notifications of class {@link JMXConnectionNotification} with
     * the types defined in that class.  A subclass that can emit other
     * notifications should return an array that contains this element
     * plus descriptions of the other notifications.</p>
     *
     * @return the array of possible notifications.
     */
    public MBeanNotificationInfo[] getNotificationInfo() {
        final String[] types = {
            JMXConnectionNotification.OPENED,
            JMXConnectionNotification.CLOSED,
            JMXConnectionNotification.FAILED,
        };
        final String className = JMXConnectionNotification.class.getName();
        final String description =
            "A client connection has been opened or closed";
        return new MBeanNotificationInfo[] {
            new MBeanNotificationInfo(types, className, description),
        };
    }

    /**
     * <p>Called by a subclass when a new client connection is opened.
     * Adds <code>connectionId</code> to the list returned by {@link
     * #getConnectionIds()}, then emits a {@link
     * JMXConnectionNotification} with type {@link
     * JMXConnectionNotification#OPENED}.</p>
     *
     * @param connectionId the ID of the new connection.  This must be
     * different from the ID of any connection previously opened by
     * this connector server.
     *
     * @param message the message for the emitted {@link
     * JMXConnectionNotification}.  Can be null.  See {@link
     * Notification#getMessage()}.
     *
     * @param userData the <code>userData</code> for the emitted
     * {@link JMXConnectionNotification}.  Can be null.  See {@link
     * Notification#getUserData()}.
     *
     * @exception NullPointerException if <code>connectionId</code> is
     * null.
     */
    protected void connectionOpened(String connectionId,
                                    String message,
                                    Object userData) {

        if (connectionId == null)
            throw new NullPointerException("Illegal null argument");

        synchronized (connectionIds) {
            connectionIds.add(connectionId);
        }

        sendNotification(JMXConnectionNotification.OPENED, connectionId,
                         message, userData);
    }

    /**
     * <p>Called by a subclass when a client connection is closed
     * normally.  Removes <code>connectionId</code> from the list returned
     * by {@link #getConnectionIds()}, then emits a {@link
     * JMXConnectionNotification} with type {@link
     * JMXConnectionNotification#CLOSED}.</p>
     *
     * @param connectionId the ID of the closed connection.
     *
     * @param message the message for the emitted {@link
     * JMXConnectionNotification}.  Can be null.  See {@link
     * Notification#getMessage()}.
     *
     * @param userData the <code>userData</code> for the emitted
     * {@link JMXConnectionNotification}.  Can be null.  See {@link
     * Notification#getUserData()}.
     *
     * @exception NullPointerException if <code>connectionId</code>
     * is null.
     */
    protected void connectionClosed(String connectionId,
                                    String message,
                                    Object userData) {

        if (connectionId == null)
            throw new NullPointerException("Illegal null argument");

        synchronized (connectionIds) {
            connectionIds.remove(connectionId);
        }

        sendNotification(JMXConnectionNotification.CLOSED, connectionId,
                         message, userData);
    }

    /**
     * <p>Called by a subclass when a client connection fails.
     * Removes <code>connectionId</code> from the list returned by
     * {@link #getConnectionIds()}, then emits a {@link
     * JMXConnectionNotification} with type {@link
     * JMXConnectionNotification#FAILED}.</p>
     *
     * @param connectionId the ID of the failed connection.
     *
     * @param message the message for the emitted {@link
     * JMXConnectionNotification}.  Can be null.  See {@link
     * Notification#getMessage()}.
     *
     * @param userData the <code>userData</code> for the emitted
     * {@link JMXConnectionNotification}.  Can be null.  See {@link
     * Notification#getUserData()}.
     *
     * @exception NullPointerException if <code>connectionId</code> is
     * null.
     */
    protected void connectionFailed(String connectionId,
                                    String message,
                                    Object userData) {

        if (connectionId == null)
            throw new NullPointerException("Illegal null argument");

        synchronized (connectionIds) {
            connectionIds.remove(connectionId);
        }

        sendNotification(JMXConnectionNotification.FAILED, connectionId,
                         message, userData);
    }

    private void sendNotification(String type, String connectionId,
                                  String message, Object userData) {
        Notification notif =
            new JMXConnectionNotification(type,
                                          getNotificationSource(),
                                          connectionId,
                                          nextSequenceNumber(),
                                          message,
                                          userData);
        sendNotification(notif);
    }

    private synchronized Object getNotificationSource() {
        if (myName != null)
            return myName;
        else
            return this;
    }

    private static long nextSequenceNumber() {
        synchronized (sequenceNumberLock) {
            return sequenceNumber++;
        }
    }

    // implements MBeanRegistration
    /**
     * <p>Called by an MBean server when this connector server is
     * registered in that MBean server.  This connector server becomes
     * attached to the MBean server and its {@link #getMBeanServer()}
     * method will return <code>mbs</code>.</p>
     *
     * <p>If this connector server is already attached to an MBean
     * server, this method has no effect.  The MBean server it is
     * attached to is not necessarily the one it is being registered
     * in.</p>
     *
     * @param mbs the MBean server in which this connection server is
     * being registered.
     *
     * @param name The object name of the MBean.
     *
     * @return The name under which the MBean is to be registered.
     *
     * @exception NullPointerException if <code>mbs</code> or
     * <code>name</code> is null.
     */
    public synchronized ObjectName preRegister(MBeanServer mbs,
                                               ObjectName name) {
        if (mbs == null || name == null)
            throw new NullPointerException("Null MBeanServer or ObjectName");
        if (userMBeanServer == null) {
            insertUserMBeanServer(mbs);
            myName = name;
        }
        return name;
    }

    public void postRegister(Boolean registrationDone) {
        // do nothing
    }

    /**
     * <p>Called by an MBean server when this connector server is
     * unregistered from that MBean server.  If this connector server
     * was attached to that MBean server by being registered in it,
     * and if the connector server is still active,
     * then unregistering it will call the {@link #stop stop} method.
     * If the <code>stop</code> method throws an exception, the
     * unregistration attempt will fail.  It is recommended to call
     * the <code>stop</code> method explicitly before unregistering
     * the MBean.</p>
     *
     * @exception IOException if thrown by the {@link #stop stop} method.
     */
    public synchronized void preDeregister() throws Exception {
        if (myName != null && isActive()) {
            stop();
            myName = null; // just in case stop is buggy and doesn't stop
        }
    }

    public void postDeregister() {
        myName = null;
    }

    /*
     * Fields describing the chains of forwarders (MBeanServerForwarders).
     * In the general case, the forwarders look something like this:
     *
     * systemMBeanServer          userMBeanServer
     * |                          |
     * v                          v
     * mbsf1 -> mbsf2 -> mbsf3 -> mbsf4 -> mbsf5 -> mbs
     *
     * Here, each mbsfi is an MBeanServerForwarder, and the arrows
     * illustrate its getMBeanServer() method.  The last MBeanServerForwarder
     * can point to an MBeanServer that is not instanceof MBeanServerForwarder,
     * here mbs.
     *
     * Initially, the chain can be empty if this JMXConnectorServer was
     * constructed without an MBeanServer.  In this case, both systemMBS
     * and userMBS will be null.  If there is initially an MBeanServer,
     * then both systemMBS and userMBS will point to it.
     *
     * Whenever userMBS is changed, the system chain must be updated. If there
     * are forwarders in the system chain (between systemMBS and userMBS in the
     * picture above), then the last one must point to the old value of userMBS
     * (possibly null). It must be updated to point to the new value. If there
     * are no forwarders in the system chain, then systemMBS must be updated to
     * the new value of userMBS. The invariant is that starting from systemMBS
     * and repeatedly calling MBSF.getMBeanServer() you will end up at
     * userMBS.  The implication is that you will not see any MBeanServer
     * object on the way that is not also an MBeanServerForwarder.
     *
     * The method insertUserMBeanServer contains the logic to change userMBS
     * and adjust the system chain appropriately.
     *
     * If userMBS is null and this JMXConnectorServer is registered in an
     * MBeanServer, then userMBS becomes that MBeanServer, and the system
     * chain must be updated as just described.
     *
     * When systemMBS is updated, there is no effect on userMBS. The system
     * chain may contain forwarders even though the user chain is empty
     * (there is no MBeanServer). In that case an attempt to forward an
     * incoming request through the chain will fall off the end and fail with a
     * NullPointerException. Usually a connector server will refuse to start()
     * if it is not attached to an MBS, so this situation should not arise.
     */

    private MBeanServer userMBeanServer;

    private MBeanServer systemMBeanServer;

    /**
     * The name used to registered this server in an MBeanServer.
     * It is null if the this server is not registered or has been unregistered.
     */
    private ObjectName myName;

    private List<String> connectionIds = new ArrayList<String>();

    private static final int[] sequenceNumberLock = new int[0];
    private static long sequenceNumber;
}
