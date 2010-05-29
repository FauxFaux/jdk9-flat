/*
 * Copyright (c) 1999, 2001, Oracle and/or its affiliates. All rights reserved.
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
/*
 * COMPONENT_NAME: idl.toJava
 *
 * ORIGINS: 27
 *
 * Licensed Materials - Property of IBM
 * 5639-D57 (C) COPYRIGHT International Business Machines Corp. 1997, 1999
 * RMI-IIOP v1.0
 *
 */

package com.sun.tools.corba.se.idl.toJavaPortable;

// NOTES:

import java.io.File;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Hashtable;

import com.sun.tools.corba.se.idl.ModuleEntry;
import com.sun.tools.corba.se.idl.SymtabEntry;

/**
 *
 **/
public class ModuleGen implements com.sun.tools.corba.se.idl.ModuleGen
{
  /**
   * Public zero-argument constructor.
   **/
  public ModuleGen ()
  {
  } // ctor

  /**
   * Generate Java code for all members of an IDL module.
   **/
  public void generate (Hashtable symbolTable, ModuleEntry entry, PrintWriter stream)
  {
    // Generate the package directory
    String name = Util.containerFullName( entry ) ;
    Util.mkdir (name);

    // Generate all of the contained types
    Enumeration e = entry.contained ().elements ();
    while (e.hasMoreElements ())
    {
      SymtabEntry element = (SymtabEntry)e.nextElement ();
      if (element.emit ())
        element.generate (symbolTable, stream);
    }
  } // generate
} // class ModuleGen
