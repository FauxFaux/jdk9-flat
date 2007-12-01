/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/**
 * @test
 * @bug 6527316
 * @summary Copies isn't supported for PS flavors.
 * @run main PSCopiesFlavorTest
 */

import javax.print.*;
import javax.print.attribute.*;
import javax.print.attribute.standard.*;

public class PSCopiesFlavorTest {

   public static void main(String args[]) {

       DocFlavor flavor = DocFlavor.INPUT_STREAM.POSTSCRIPT;
       PrintService ps = PrintServiceLookup.lookupDefaultPrintService();
       if (!(ps.isDocFlavorSupported(flavor))) {
           System.out.println("unsupported flavor :" + flavor);
           return;
       }
       Copies c = new Copies(1);
       PrintRequestAttributeSet aset = new HashPrintRequestAttributeSet();
       aset.add(c);
       boolean suppVal = ps.isAttributeValueSupported(c, flavor, null);
       AttributeSet us = ps.getUnsupportedAttributes(flavor, aset);
       if (suppVal || us == null) {
           throw new RuntimeException("Copies should be unsupported value");
       }
   }
}
