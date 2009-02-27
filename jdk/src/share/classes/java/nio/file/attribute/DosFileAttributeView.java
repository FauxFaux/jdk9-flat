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
 * A file attribute view that provides a view of the legacy "DOS" file attributes.
 * These attributes are supported by file systems such as the File Allocation
 * Table (FAT) format commonly used in <em>consumer devices</em>.
 *
 * <p> A {@code DosFileAttributeView} is a {@link BasicFileAttributeView} that
 * additionally supports access to the set of DOS attribute flags that are used
 * to indicate if the file is read-only, hidden, a system file, or archived.
 *
 * <p> Where dynamic access to file attributes is required, the attributes
 * supported by this attribute view are as defined by {@code
 * BasicFileAttributeView}, and in addition, the following attributes are
 * supported:
 * <blockquote>
 * <table border="1" cellpadding="8">
 *   <tr>
 *     <th> Name </th>
 *     <th> Type </th>
 *   </tr>
 *   <tr>
 *     <td> readonly </td>
 *     <td> {@link Boolean} </td>
 *   </tr>
 *   <tr>
 *     <td> hidden </td>
 *     <td> {@link Boolean} </td>
 *   </tr>
 *   <tr>
 *     <td> system </td>
 *     <td> {@link Boolean} </td>
 *   </tr>
 *   <tr>
 *     <td> archive </td>
 *     <td> {@link Boolean} </td>
 *   </tr>
 * </table>
 * </blockquote>
 *
 * <p> The {@link #getAttribute getAttribute} or {@link #readAttributes(String,String[])
 * readAttributes(String,String[])} methods may be used to read any of these
 * attributes, or any of the attributes defined by {@link BasicFileAttributeView}
 * as if by invoking the {@link #readAttributes readAttributes()} method.
 *
 * <p> The {@link #setAttribute setAttribute} method may be used to update the
 * file's last modified time, last access time or create time attributes as
 * defined by {@link BasicFileAttributeView}. It may also be used to update
 * the DOS attributes as if by invoking the {@link #setReadOnly setReadOnly},
 * {@link #setHidden setHidden}, {@link #setSystem setSystem}, and {@link
 * #setArchive setArchive} methods respectively.
 *
 * @since 1.7
 */

public interface DosFileAttributeView
    extends BasicFileAttributeView
{
    /**
     * Returns the name of the attribute view. Attribute views of this type
     * have the name {@code "dos"}.
     */
    @Override
    String name();

    /**
     * @throws  IOException                             {@inheritDoc}
     * @throws  SecurityException                       {@inheritDoc}
     */
    @Override
    DosFileAttributes readAttributes() throws IOException;

    /**
     * Updates the value of the read-only attribute.
     *
     * <p> It is implementation specific if the attribute can be updated as an
     * atomic operation with respect to other file system operations. An
     * implementation may, for example, require to read the existing value of
     * the DOS attribute in order to update this attribute.
     *
     * @param   value
     *          the new value of the attribute
     *
     * @throws  IOException
     *          if an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default, and a security manager is installed,
     *          its  {@link SecurityManager#checkWrite(String) checkWrite} method
     *          is invoked to check write access to the file
     */
    void setReadOnly(boolean value) throws IOException;

    /**
     * Updates the value of the hidden attribute.
     *
     * <p> It is implementation specific if the attribute can be updated as an
     * atomic operation with respect to other file system operations. An
     * implementation may, for example, require to read the existing value of
     * the DOS attribute in order to update this attribute.
     *
     * @param   value
     *          the new value of the attribute
     *
     * @throws  IOException
     *          if an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default, and a security manager is installed,
     *          its  {@link SecurityManager#checkWrite(String) checkWrite} method
     *          is invoked to check write access to the file
     */
    void setHidden(boolean value) throws IOException;

    /**
     * Updates the value of the system attribute.
     *
     * <p> It is implementation specific if the attribute can be updated as an
     * atomic operation with respect to other file system operations. An
     * implementation may, for example, require to read the existing value of
     * the DOS attribute in order to update this attribute.
     *
     * @param   value
     *          the new value of the attribute
     *
     * @throws  IOException
     *          if an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default, and a security manager is installed,
     *          its  {@link SecurityManager#checkWrite(String) checkWrite} method
     *          is invoked to check write access to the file
     */
    void setSystem(boolean value) throws IOException;

    /**
     * Updates the value of the archive attribute.
     *
     * <p> It is implementation specific if the attribute can be updated as an
     * atomic operation with respect to other file system operations. An
     * implementation may, for example, require to read the existing value of
     * the DOS attribute in order to update this attribute.
     *
     * @param   value
     *          the new value of the attribute
     *
     * @throws  IOException
     *          if an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default, and a security manager is installed,
     *          its  {@link SecurityManager#checkWrite(String) checkWrite} method
     *          is invoked to check write access to the file
     */
    void setArchive(boolean value) throws IOException;
}
