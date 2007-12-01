/*
 * Copyright 1999 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.awt.event;

/**
 * An abstract adapter class for receiving ancestor moved and resized events.
 * The methods in this class are empty. This class exists as a
 * convenience for creating listener objects.
 * <p>
 * Extend this class and override the method for the event of interest. (If
 * you implement the <code>HierarchyBoundsListener</code> interface, you have
 * to define both methods in it. This abstract class defines null methods for
 * them both, so you only have to define the method for the event you care
 * about.)
 * <p>
 * Create a listener object using your class and then register it with a
 * Component using the Component's <code>addHierarchyBoundsListener</code>
 * method. When the hierarchy to which the Component belongs changes by
 * resize or movement of an ancestor, the relevant method in the listener
 * object is invoked, and the <code>HierarchyEvent</code> is passed to it.
 *
 * @author      David Mendenhall
 * @see         HierarchyBoundsListener
 * @see         HierarchyEvent
 * @since       1.3
 */
public abstract class HierarchyBoundsAdapter implements HierarchyBoundsListener
{
    /**
     * Called when an ancestor of the source is moved.
     */
    public void ancestorMoved(HierarchyEvent e) {}

    /**
     * Called when an ancestor of the source is resized.
     */
    public void ancestorResized(HierarchyEvent e) {}
}
