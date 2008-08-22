/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 * @test
 * @bug     6733837
 * @summary Compiler API ignores locale settings
 * @author  Maurizio Cimadamore
 * @library ../lib
 */

import java.io.StringWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import static javax.tools.JavaFileObject.Kind;
import com.sun.source.util.JavacTask;

public class T6733837 extends ToolTester {

    public static void main(String... args) {
        new T6733837().exec();
    }

    public void exec() {
        JavaFileObject sfo = new SimpleJavaFileObject(URI.create(""),Kind.SOURCE) {
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return "\tclass ErroneousWithTab";
            }
            @Override
            public String getName() {
                return "RELATIVEPATH";
            }
        };
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        List<? extends JavaFileObject> files = Arrays.asList(sfo);
        task = tool.getTask(sw, fm, null, null, null, files);
        try {
            ((JavacTask)task).analyze();
        }
        catch (Throwable t) {
            throw new Error("Compiler threw an exception");
        }
        System.err.println(sw.toString());
        if (sw.toString().contains("RELATIVEPATH"))
            throw new Error("Bad source name in diagnostic");
    }
}
