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

import java.io.Serializable;

/**
 * <p>Result of a query for buffered notifications.  Notifications in
 * a notification buffer have positive, monotonically increasing
 * sequence numbers.  The result of a notification query contains the
 * following elements:</p>
 *
 * <ul>
 *
 * <li>The sequence number of the earliest notification still in
 * the buffer.
 *
 * <li>The sequence number of the next notification available for
 * querying.  This will be the starting sequence number for the next
 * notification query.
 *
 * <li>An array of (Notification,listenerID) pairs corresponding to
 * the returned notifications and the listeners they correspond to.
 *
 * </ul>
 *
 * <p>It is possible for the <code>nextSequenceNumber</code> to be less
 * than the <code>earliestSequenceNumber</code>.  This signifies that
 * notifications between the two might have been lost.</p>
 *
 * @since 1.5
 */
public class NotificationResult implements Serializable {

    private static final long serialVersionUID = 1191800228721395279L;

    /**
     * <p>Constructs a notification query result.</p>
     *
     * @param earliestSequenceNumber the sequence number of the
     * earliest notification still in the buffer.
     * @param nextSequenceNumber the sequence number of the next
     * notification available for querying.
     * @param targetedNotifications the notifications resulting from
     * the query, and the listeners they correspond to.  This array
     * can be empty.
     *
     * @exception IllegalArgumentException if
     * <code>targetedNotifications</code> is null or if
     * <code>earliestSequenceNumber</code> or
     * <code>nextSequenceNumber</code> is negative.
     */
    public NotificationResult(long earliestSequenceNumber,
                              long nextSequenceNumber,
                              TargetedNotification[] targetedNotifications) {
        if (targetedNotifications == null) {
            final String msg = "Notifications null";
            throw new IllegalArgumentException(msg);
        }

        if (earliestSequenceNumber < 0 || nextSequenceNumber < 0)
            throw new IllegalArgumentException("Bad sequence numbers");
        /* We used to check nextSequenceNumber >= earliestSequenceNumber
           here.  But in fact the opposite can legitimately be true if
           notifications have been lost.  */

        this.earliestSequenceNumber = earliestSequenceNumber;
        this.nextSequenceNumber = nextSequenceNumber;
        this.targetedNotifications = targetedNotifications;
    }

    /**
     * Returns the sequence number of the earliest notification still
     * in the buffer.
     *
     * @return the sequence number of the earliest notification still
     * in the buffer.
     */
    public long getEarliestSequenceNumber() {
        return earliestSequenceNumber;
    }

    /**
     * Returns the sequence number of the next notification available
     * for querying.
     *
     * @return the sequence number of the next notification available
     * for querying.
     */
    public long getNextSequenceNumber() {
        return nextSequenceNumber;
    }

    /**
     * Returns the notifications resulting from the query, and the
     * listeners they correspond to.
     *
     * @return the notifications resulting from the query, and the
     * listeners they correspond to.  This array can be empty.
     */
    public TargetedNotification[] getTargetedNotifications() {
        return targetedNotifications;
    }

    /**
     * Returns a string representation of the object.  The result
     * should be a concise but informative representation that is easy
     * for a person to read.
     *
     * @return a string representation of the object.
     */
    public String toString() {
        return "NotificationResult: earliest=" + getEarliestSequenceNumber() +
            "; next=" + getNextSequenceNumber() + "; nnotifs=" +
            getTargetedNotifications().length;
    }

    private final long earliestSequenceNumber;
    private final long nextSequenceNumber;
    private final TargetedNotification[] targetedNotifications;
}
