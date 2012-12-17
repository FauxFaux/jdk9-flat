/*
 * @test /nodynamiccopyright/
 * @library ..
 * @build DocLintTester
 * @run main DocLintTester -ref ParaInPre.out ParaInPre.java
 */

// tidy: Warning: replacing <p> by <br>
// tidy: Warning: using <br> in place of <p>

/**
 * <pre>
 *     text
 *     <p>
 *     more text
 * </pre>
 */
public class ParaInPre { }
