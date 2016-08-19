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
 * @test 8130450 8158906
 * @summary simple regression test
 * @build KullaTesting TestingInputStream
 * @run testng SimpleRegressionTest
 */


import java.util.List;

import javax.tools.Diagnostic;

import jdk.jshell.Snippet;
import jdk.jshell.VarSnippet;
import jdk.jshell.SnippetEvent;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static jdk.jshell.Snippet.Status.OVERWRITTEN;
import static jdk.jshell.Snippet.SubKind.TEMP_VAR_EXPRESSION_SUBKIND;
import static jdk.jshell.Snippet.Status.VALID;

@Test
public class SimpleRegressionTest extends KullaTesting {

    public void testSnippetMemberAssignment() {
        assertEval("class C { int y; }");
        assertEval("C c = new C();");
        assertVarKeyMatch("c.y = 4;", true, "$1", TEMP_VAR_EXPRESSION_SUBKIND, "int", added(VALID));
    }

    public void testUserTakesTempVarName() {
        assertEval("int $2 = 4;");
        assertEval("String $1;");
        assertVarKeyMatch("1234;", true, "$3", TEMP_VAR_EXPRESSION_SUBKIND, "int", added(VALID));
    }

    public void testCompileThrow() {
        assertEvalException("throw new Exception();");
    }

    public void testMultiSnippetDependencies() {
        List<SnippetEvent> events = assertEval("int a = 3, b = a+a, c = b *100;",
                DiagCheck.DIAG_OK, DiagCheck.DIAG_OK,
                chain(added(VALID)),
                chain(added(VALID)),
                chain(added(VALID)));
        assertEquals(events.get(0).value(), "3");
        assertEquals(events.get(1).value(), "6");
        assertEquals(events.get(2).value(), "600");
        assertEval("c;", "600");
    }

    public void testNotStmtCannotResolve() {
        assertDeclareFail("dfasder;", new ExpectedDiagnostic("compiler.err.cant.resolve.location", 0, 7, 0, -1, -1, Diagnostic.Kind.ERROR));
    }

    public void testNotStmtIncomparable() {
        assertDeclareFail("true == 5.0;", new ExpectedDiagnostic("compiler.err.incomparable.types", 0, 11, 5, -1, -1, Diagnostic.Kind.ERROR));
    }

    public void testStringAdd() {
        assertEval("String s = \"a\" + \"b\";", "\"ab\"");
    }

    public void testExprSanity() {
        assertEval("int x = 3;", "3");
        assertEval("int y = 4;", "4");
        assertEval("x + y;", "7");
        assertActiveKeys();
    }

    public void testGenericMethodCrash() {
        assertDeclareWarn1("<T> void f(T...a) {}", (ExpectedDiagnostic) null);
        Snippet sn = methodKey(assertEval("<R> R n(R x) { return x; }", added(VALID)));
        VarSnippet sne = varKey(assertEval("n(5)", added(VALID)));
        assertEquals(sne.typeName(), "Integer");
    }

    public void testLongRemoteStrings() { //8158906
        assertEval("String m(int x) { byte[] b = new byte[x]; for (int i = 0; i < x; ++i) b[i] = (byte) 'a'; return new String(b); }");
        boolean[] shut = new boolean[1];
        getState().onShutdown(j -> {
            shut[0] = true;
        });
        for (String len : new String[]{"12345", "64000", "65535", "65536", "120000"}) {
            List<SnippetEvent> el = assertEval("m(" + len + ");");
            assertFalse(shut[0], "JShell died with long string");
            assertEquals(el.size(), 1, "Excepted one event");
            assertTrue(el.get(0).value().length() > 10000,
                    "Expected truncated but long String, got: " + el.get(0).value().length());
        }
    }

    public void testLongRemoteJapaneseStrings() { //8158906
        assertEval("import java.util.stream.*;");
        assertEval("String m(int x) { return Stream.generate(() -> \"\u3042\").limit(x).collect(Collectors.joining()); }");
        boolean[] shut = new boolean[1];
        getState().onShutdown(j -> {
            shut[0] = true;
        });
        for (String len : new String[]{"12345", "21843", "21844", "21845", "21846", "64000", "65535", "65536", "120000"}) {
            List<SnippetEvent> el = assertEval("m(" + len + ");");
            assertFalse(shut[0], "JShell died with long string");
            assertEquals(el.size(), 1, "Excepted one event");
            assertTrue(el.get(0).value().length() > 10000,
                    "Expected truncated but long String, got: " + el.get(0).value().length());
        }
    }

    // 8130450
    public void testDuplicate() {
        Snippet snm = methodKey(assertEval("void mm() {}", added(VALID)));
        assertEval("void mm() {}",
                ste(MAIN_SNIPPET, VALID, VALID, false, null),
                ste(snm, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
        Snippet snv = varKey(assertEval("boolean b;", added(VALID)));
        assertEval("boolean b;",
                ste(MAIN_SNIPPET, VALID, VALID, false, null),
                ste(snv, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
    }
}
