/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

package sun.lwawt.macosx;

import sun.lwawt.LWCursorManager;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.geom.Point2D;

final class CCursorManager extends LWCursorManager {

    private static native Point2D nativeGetCursorPosition();
    private static native void nativeSetBuiltInCursor(final int type, final String name);
    private static native void nativeSetCustomCursor(final long imgPtr, final double x, final double y);

    private static final int NAMED_CURSOR = -1;

    private static final CCursorManager theInstance = new CCursorManager();
    public static CCursorManager getInstance() {
        return theInstance;
    }

    private volatile Cursor currentCursor;

    private CCursorManager() { }

    @Override
    protected Point getCursorPosition() {
        synchronized(this) {
            if (isDragging) {
                // during the drag operation, the appkit thread is blocked,
                // so nativeGetCursorPosition invocation may cause a deadlock.
                // In order to avoid this, we returns last know cursor position.
                return new Point(dragPos);
            }
        }

        final Point2D nativePosition = nativeGetCursorPosition();
        return new Point((int)nativePosition.getX(), (int)nativePosition.getY());
    }

    @Override
    protected void setCursor(final Cursor cursor) {
        if (cursor == currentCursor) {
            return;
        }
        currentCursor = cursor;

        if (cursor == null) {
            nativeSetBuiltInCursor(Cursor.DEFAULT_CURSOR, null);
            return;
        }

        if (cursor instanceof CCustomCursor) {
            final CCustomCursor customCursor = (CCustomCursor) cursor;
            final long imagePtr = customCursor.getImageData();
            if (imagePtr != 0L) {
                final Point hotSpot = customCursor.getHotSpot();
                nativeSetCustomCursor(imagePtr, hotSpot.x, hotSpot.y);
            }
            return;
        }

        final int type = cursor.getType();
        if (type != Cursor.CUSTOM_CURSOR) {
            nativeSetBuiltInCursor(type, null);
            return;
        }

        final String name = cursor.getName();
        if (name != null) {
            nativeSetBuiltInCursor(NAMED_CURSOR, name);
            return;
        }

        // do something special
        throw new RuntimeException("Unimplemented");
    }

    // package private methods to handle cursor change during drag-and-drop
    private boolean isDragging = false;
    private Point dragPos = null;

    synchronized void startDrag(int x, int y) {
        if (isDragging) {
            throw new RuntimeException("Invalid Drag state in CCursorManager!");
        }
        isDragging = true;
        dragPos = new Point(x, y);
    }

    synchronized void updateDragPosition(int x, int y) {
        if (!isDragging) {
            throw new RuntimeException("Invalid Drag state in CCursorManager!");
        }
        dragPos.move(x, y);
    }

    synchronized void stopDrag() {
        if (!isDragging) {
            throw new RuntimeException("Invalid Drag state in CCursorManager!");
        }
        isDragging = false;
        dragPos = null;
    }
}
