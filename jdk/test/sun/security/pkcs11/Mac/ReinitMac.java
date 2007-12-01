/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 4856966
 * @summary
 * @author Andreas Sterbenz
 * @library ..
 */

import java.util.*;

import java.security.*;

import javax.crypto.*;
import javax.crypto.spec.*;

public class ReinitMac extends PKCS11Test {

    public static void main(String[] args) throws Exception {
        main(new ReinitMac());
    }

    public void main(Provider p) throws Exception {
        if (p.getService("Mac", "HmacMD5") == null) {
            System.out.println(p + " does not support HmacMD5, skipping");
            return;
        }
        Random random = new Random();
        byte[] data1 = new byte[10 * 1024];
        random.nextBytes(data1);
        byte[] keyData = new byte[16];
        random.nextBytes(keyData);
        SecretKeySpec key = new SecretKeySpec(keyData, "Hmac");
        Mac mac = Mac.getInstance("HmacMD5", p);
        mac.init(key);
        mac.init(key);
        mac.update(data1);
        mac.init(key);
        mac.doFinal();
        mac.doFinal();
        mac.update(data1);
        mac.doFinal();
        mac.reset();
        mac.reset();
        mac.init(key);
        mac.reset();
        mac.update(data1);
        mac.reset();

        System.out.println("All tests passed");
    }
}
