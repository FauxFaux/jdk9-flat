/*
 * Copyright 2001 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.reflect;

/** <P> Java serialization (in java.io) expects to be able to
    instantiate a class and invoke a no-arg constructor of that
    class's first non-Serializable superclass. This is not a valid
    operation according to the VM specification; one can not (for
    classes A and B, where B is a subclass of A) write "new B;
    invokespecial A()" without getting a verification error. </P>

    <P> In all other respects, the bytecode-based reflection framework
    can be reused for this purpose. This marker class was originally
    known to the VM and verification disabled for it and all
    subclasses, but the bug fix for 4486457 necessitated disabling
    verification for all of the dynamically-generated bytecodes
    associated with reflection. This class has been left in place to
    make future debugging easier. </P> */

abstract class SerializationConstructorAccessorImpl
    extends ConstructorAccessorImpl {
}
