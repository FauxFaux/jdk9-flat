/*
 * @test    /nodynamiccopyright/
 * @bug     6380059
 * @summary Emit warnings for proprietary packages in the boot class path
 * @author  Peter von der Ah\u00e9
 * @compile WarnVariable.java
 * @compile/fail/ref=WarnVariable.out -XDrawDiagnostics  -Werror WarnVariable.java
 * @compile/fail/ref=WarnVariable.out -XDrawDiagnostics  -Werror -nowarn WarnVariable.java
 * @compile/fail/ref=WarnVariable.out -XDrawDiagnostics  -Werror -Xlint:none WarnVariable.java
 */

public class WarnVariable {
    public static void main(String... args) {
        System.out.println(sun.misc.FloatConsts.POSITIVE_INFINITY);
    }
}
