/*
 * Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.
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
package javax.print.attribute.standard;

import java.util.Locale;

import javax.print.attribute.Attribute;
import javax.print.attribute.TextSyntax;
import javax.print.attribute.PrintServiceAttribute;

/**
 * Class PrinterMessageFromOperator is a printing attribute class, a text
 * attribute, that provides a message from an operator, system administrator,
 * or "intelligent" process to indicate to the end user information about or
 * status of the printer, such as why it is unavailable or when it is
 * expected to be available.
 * <P>
 * A Print Service's attribute set includes zero instances or one instance of
 * a
 * PrinterMessageFromOperator attribute, not more than one instance. A new
 * PrinterMessageFromOperator attribute replaces an existing
 * PrinterMessageFromOperator attribute, if any. In other words,
 * PrinterMessageFromOperator is not intended to be a history log.
 * If it wishes, the client can detect changes to a Print Service's
 * PrinterMessageFromOperator
 * attribute and maintain the client's own history log of the
 * PrinterMessageFromOperator attribute values.
 * <P>
 * <B>IPP Compatibility:</B> The string value gives the IPP name value. The
 * locale gives the IPP natural language. The category name returned by
 * {@code getName()} gives the IPP attribute name.
 *
 * @author  Alan Kaminsky
 */
public final class PrinterMessageFromOperator   extends TextSyntax
    implements PrintServiceAttribute {

    static final long serialVersionUID = -4486871203218629318L;

    /**
     * Constructs a new printer message from operator attribute with the
     * given message and locale.
     *
     * @param  message  Message.
     * @param  locale   Natural language of the text string. null
     * is interpreted to mean the default locale as returned
     * by {@code Locale.getDefault()}
     *
     * @exception  NullPointerException
     *     (unchecked exception) Thrown if {@code message} is null.
     */
    public PrinterMessageFromOperator(String message, Locale locale) {
        super (message, locale);
    }

    /**
     * Returns whether this printer message from operator attribute is
     * equivalent to the passed in object. To be equivalent, all of the
     * following conditions must be true:
     * <OL TYPE=1>
     * <LI>
     * {@code object} is not null.
     * <LI>
     * {@code object} is an instance of class
     * PrinterMessageFromOperator.
     * <LI>
     * This printer message from operator attribute's underlying string and
     * {@code object}'s underlying string are equal.
     * <LI>
     * This printer message from operator attribute's locale and
     * {@code object}'s locale are equal.
     * </OL>
     *
     * @param  object  Object to compare to.
     *
     * @return  True if {@code object} is equivalent to this printer
     *          message from operator attribute, false otherwise.
     */
    public boolean equals(Object object) {
        return (super.equals(object) &&
                object instanceof PrinterMessageFromOperator);
    }

    /**
     * Get the printing attribute class which is to be used as the "category"
     * for this printing attribute value.
     * <P>
     * For class PrinterMessageFromOperator,
     * the category is class PrinterMessageFromOperator itself.
     *
     * @return  Printing attribute class (category), an instance of class
     *          {@link java.lang.Class java.lang.Class}.
     */
    public final Class<? extends Attribute> getCategory() {
        return PrinterMessageFromOperator.class;
    }

    /**
     * Get the name of the category of which this attribute value is an
     * instance.
     * <P>
     * For class PrinterMessageFromOperator,
     * the category name is {@code "printer-message-from-operator"}.
     *
     * @return  Attribute category name.
     */
    public final String getName() {
        return "printer-message-from-operator";
    }

}
