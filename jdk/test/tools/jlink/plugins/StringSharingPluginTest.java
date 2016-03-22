/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @summary Test StringSharingPluginTest
 * @author Jean-Francois Denise
 * @library ../../lib
 * @modules java.base/jdk.internal.jimage
 *          java.base/jdk.internal.jimage.decompressor
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.internal.plugins
 *          jdk.jlink/jdk.tools.jmod
 *          jdk.jlink/jdk.tools.jimage
 *          jdk.jdeps/com.sun.tools.classfile
 *          jdk.compiler
 * @run build tests.*
 * @run main StringSharingPluginTest
 */

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import jdk.internal.jimage.decompressor.CompressedResourceHeader;
import jdk.internal.jimage.decompressor.StringSharingDecompressor;
import jdk.tools.jlink.internal.PoolImpl;
import jdk.tools.jlink.internal.StringTable;
import jdk.tools.jlink.internal.plugins.StringSharingPlugin;
import jdk.tools.jlink.plugin.Pool;
import jdk.tools.jlink.plugin.Pool.ModuleData;
import jdk.tools.jlink.plugin.TransformerPlugin;
import tests.Helper;
import tests.JImageValidator;

public class StringSharingPluginTest {

    private static int strID = 1;

    public static void main(String[] args) throws Exception {
        // JPRT not yet ready for jmods
        Helper helper = Helper.newHelper();
        if (helper == null) {
            System.err.println("Test not run, NO jmods directory");
            return;
        }

        List<String> classes = Arrays.asList("toto.Main", "toto.com.foo.bar.X");
        Path compiledClasses = helper.generateModuleCompiledClasses(
                helper.getJmodSrcDir(), helper.getJmodClassesDir(), "composite2", classes);

        Map<String, Integer> map = new HashMap<>();
        Map<Integer, String> reversedMap = new HashMap<>();

        PoolImpl resources = new PoolImpl(ByteOrder.nativeOrder(), new StringTable() {
            @Override
            public int addString(String str) {
                Integer id = map.get(str);
                if (id == null) {
                    id = strID;
                    map.put(str, id);
                    reversedMap.put(id, str);
                    strID += 1;
                }
                return id;
            }

            @Override
            public String getString(int id) {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        });
        Consumer<Path> c = (p) -> {
            // take only the .class resources.
            if (Files.isRegularFile(p) && p.toString().endsWith(".class")
                    && !p.toString().endsWith("module-info.class")) {
                try {
                    byte[] content = Files.readAllBytes(p);
                    String path = p.toString().replace('\\', '/');
                    path = path.substring("/modules".length());
                    ModuleData res = Pool.newResource(path, content);
                    resources.add(res);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        };
        try (java.util.stream.Stream<Path> stream = Files.walk(compiledClasses)) {
            stream.forEach(c);
        }
        TransformerPlugin plugin = new StringSharingPlugin();
        PoolImpl result = new PoolImpl(resources.getByteOrder(), resources.getStringTable());
        plugin.visit(resources, result);

        if (result.isEmpty()) {
            throw new AssertionError("No result");
        }

        for (ModuleData res : result.getContent()) {
            if (res.getPath().endsWith(".class")) {
                byte[] uncompacted = StringSharingDecompressor.normalize(reversedMap::get, res.getBytes(),
                        CompressedResourceHeader.getSize());
                JImageValidator.readClass(uncompacted);
            }
        }
    }
}
