/*
 * Copyright 2007-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.nio.file.attribute;

import java.io.IOException;

/**
 * A file store attribute view that supports reading of space attributes.
 *
 * <p> Where dynamic access to file attributes is required, the attributes
 * supported by this attribute view have the following names and types:
 * <blockquote>
 * <table border="1" cellpadding="8">
 *   <tr>
 *     <th> Name </th>
 *     <th> Type </th>
 *   </tr>
 *  <tr>
 *     <td> "totalSpace" </td>
 *     <td> {@link Long} </td>
 *   </tr>
 *  <tr>
 *     <td> "usableSpace" </td>
 *     <td> {@link Long} </td>
 *   </tr>
 *  <tr>
 *     <td> "unallocatedSpace" </td>
 *     <td> {@link Long} </td>
 *   </tr>
 * </table>
 * </blockquote>
 * <p> The {@link #getAttribute getAttribute} or {@link #readAttributes
 * readAttributes(String,String[])} methods may be used to read any of these
 * attributes as if by invoking the {@link #readAttributes readAttributes()}
 * method.
 *
 * @since 1.7
 */

public interface FileStoreSpaceAttributeView
    extends FileStoreAttributeView
{
    /**
     * Returns the name of the attribute view. Attribute views of this type
     * have the name {@code "space"}.
     */
    @Override
    String name();

    /**
     * Reads the disk space attributes as a bulk operation.
     *
     * <p> It is file system specific if all attributes are read as an
     * atomic operation with respect to other file system operations.
     *
     * @return  The disk space attributes
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    FileStoreSpaceAttributes readAttributes() throws IOException;
}
