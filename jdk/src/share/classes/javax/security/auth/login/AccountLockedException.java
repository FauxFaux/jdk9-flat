/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

package javax.security.auth.login;

/**
 * Signals that an account was locked.
 *
 * <p> This exception may be thrown by a LoginModule if it
 * determines that authentication is being attempted on a
 * locked account.
 *
 * @since 1.5
 */
public class AccountLockedException extends AccountException {

    private static final long serialVersionUID = 8280345554014066334L;

    /**
     * Constructs a AccountLockedException with no detail message.
     * A detail message is a String that describes this particular exception.
     */
    public AccountLockedException() {
        super();
    }

    /**
     * Constructs a AccountLockedException with the specified
     * detail message. A detail message is a String that describes
     * this particular exception.
     *
     * <p>
     *
     * @param msg the detail message.
     */
    public AccountLockedException(String msg) {
        super(msg);
    }
}
