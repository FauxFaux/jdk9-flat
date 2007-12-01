/*
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

/*
 * Copyright (c) 2003 by BEA Systems, Inc. All Rights Reserved.
 */

package javax.xml.stream.events;
/**
 * An interface for the start document event
 *
 * @author Copyright (c) 2003 by BEA Systems. All Rights Reserved.
 * @since 1.6
 */
public interface StartDocument extends XMLEvent {

  /**
   * Returns the system ID of the XML data
   * @return the system ID, defaults to ""
   */
  public String getSystemId();

  /**
   * Returns the encoding style of the XML data
   * @return the character encoding, defaults to "UTF-8"
   */
  public String getCharacterEncodingScheme();

  /**
   * Returns true if CharacterEncodingScheme was set in
   * the encoding declaration of the document
   */
  public boolean encodingSet();

  /**
   * Returns if this XML is standalone
   * @return the standalone state of XML, defaults to "no"
   */
  public boolean isStandalone();

  /**
   * Returns true if the standalone attribute was set in
   * the encoding declaration of the document.
   */
  public boolean standaloneSet();

  /**
   * Returns the version of XML of this XML stream
   * @return the version of XML, defaults to "1.0"
   */
  public String getVersion();
}
