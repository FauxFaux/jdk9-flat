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

package com.sun.tools.javac.api;

import java.util.Locale;

/**
 * This interface must be implemented by any javac class that has non-trivial
 * formatting needs (e.g. where toString() does not apply because of localization).
 *
 * @author Maurizio Cimadamore
 */
public interface Formattable {

    /**
     * Used to obtain a localized String representing the object accordingly
     * to a given locale
     *
     * @param locale locale in which the object's representation is to be rendered
     * @param messages messages object used for localization
     * @return a locale-dependent string representing the object
     */
    public String toString(Locale locale, Messages messages);
    /**
     * Retrieve a pretty name of this object's kind
     * @return a string representing the object's kind
     */
    String getKind();
}
