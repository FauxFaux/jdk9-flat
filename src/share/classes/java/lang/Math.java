/*
 * Copyright (c) 1994, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;
import java.util.Random;

import sun.misc.FloatConsts;
import sun.misc.DoubleConsts;

/**
 * The class {@code Math} contains methods for performing basic
 * numeric operations such as the elementary exponential, logarithm,
 * square root, and trigonometric functions.
 *
 * <p>Unlike some of the numeric methods of class
 * {@code StrictMath}, all implementations of the equivalent
 * functions of class {@code Math} are not defined to return the
 * bit-for-bit same results.  This relaxation permits
 * better-performing implementations where strict reproducibility is
 * not required.
 *
 * <p>By default many of the {@code Math} methods simply call
 * the equivalent method in {@code StrictMath} for their
 * implementation.  Code generators are encouraged to use
 * platform-specific native libraries or microprocessor instructions,
 * where available, to provide higher-performance implementations of
 * {@code Math} methods.  Such higher-performance
 * implementations still must conform to the specification for
 * {@code Math}.
 *
 * <p>The quality of implementation specifications concern two
 * properties, accuracy of the returned result and monotonicity of the
 * method.  Accuracy of the floating-point {@code Math} methods is
 * measured in terms of <i>ulps</i>, units in the last place.  For a
 * given floating-point format, an {@linkplain #ulp(double) ulp} of a
 * specific real number value is the distance between the two
 * floating-point values bracketing that numerical value.  When
 * discussing the accuracy of a method as a whole rather than at a
 * specific argument, the number of ulps cited is for the worst-case
 * error at any argument.  If a method always has an error less than
 * 0.5 ulps, the method always returns the floating-point number
 * nearest the exact result; such a method is <i>correctly
 * rounded</i>.  A correctly rounded method is generally the best a
 * floating-point approximation can be; however, it is impractical for
 * many floating-point methods to be correctly rounded.  Instead, for
 * the {@code Math} class, a larger error bound of 1 or 2 ulps is
 * allowed for certain methods.  Informally, with a 1 ulp error bound,
 * when the exact result is a representable number, the exact result
 * should be returned as the computed result; otherwise, either of the
 * two floating-point values which bracket the exact result may be
 * returned.  For exact results large in magnitude, one of the
 * endpoints of the bracket may be infinite.  Besides accuracy at
 * individual arguments, maintaining proper relations between the
 * method at different arguments is also important.  Therefore, most
 * methods with more than 0.5 ulp errors are required to be
 * <i>semi-monotonic</i>: whenever the mathematical function is
 * non-decreasing, so is the floating-point approximation, likewise,
 * whenever the mathematical function is non-increasing, so is the
 * floating-point approximation.  Not all approximations that have 1
 * ulp accuracy will automatically meet the monotonicity requirements.
 *
 * <p>
 * The platform uses signed two's complement integer arithmetic with
 * int and long primitive types.  The developer should choose
 * the primitive type to ensure that arithmetic operations consistently
 * produce correct results, which in some cases means the operations
 * will not overflow the range of values of the computation.
 * The best practice is to choose the primitive type and algorithm to avoid
 * overflow. In cases where the size is {@code int} or {@code long} and
 * overflow errors need to be detected, the methods {@code addExact},
 * {@code subtractExact}, {@code multiplyExact}, and {@code toIntExact}
 * throw an {@code ArithmeticException} when the results overflow.
 * For other arithmetic operations such as divide, absolute value,
 * increment, decrement, and negation overflow occurs only with
 * a specific minimum or maximum value and should be checked against
 * the minimum or maximum as appropriate.
 *
 * @author  unascribed
 * @author  Joseph D. Darcy
 * @since   JDK1.0
 */

public final class Math {

    /**
     * Don't let anyone instantiate this class.
     */
    private Math() {}

    /**
     * The {@code double} value that is closer than any other to
     * <i>e</i>, the base of the natural logarithms.
     */
    public static final double E = 2.7182818284590452354;

    /**
     * The {@code double} value that is closer than any other to
     * <i>pi</i>, the ratio of the circumference of a circle to its
     * diameter.
     */
    public static final double PI = 3.14159265358979323846;

    /**
     * Returns the trigonometric sine of an angle.  Special cases:
     * <ul><li>If the argument is NaN or an infinity, then the
     * result is NaN.
     * <li>If the argument is zero, then the result is a zero with the
     * same sign as the argument.</ul>
     *
     * <p>The computed result must be within 1 ulp of the exact result.
     * Results must be semi-monotonic.
     *
     * @param   a   an angle, in radians.
     * @return  the sine of the argument.
     */
    public static double sin(double a) {
        return StrictMath.sin(a); // default impl. delegates to StrictMath
    }

    /**
     * Returns the trigonometric cosine of an angle. Special cases:
     * <ul><li>If the argument is NaN or an infinity, then the
     * result is NaN.</ul>
     *
     * <p>The computed result must be within 1 ulp of the exact result.
     * Results must be semi-monotonic.
     *
     * @param   a   an angle, in radians.
     * @return  the cosine of the argument.
     */
    public static double cos(double a) {
        return StrictMath.cos(a); // default impl. delegates to StrictMath
    }

    /**
     * Returns the trigonometric tangent of an angle.  Special cases:
     * <ul><li>If the argument is NaN or an infinity, then the result
     * is NaN.
     * <li>If the argument is zero, then the result is a zero with the
     * same sign as the argument.</ul>
     *
     * <p>The computed result must be within 1 ulp of the exact result.
     * Results must be semi-monotonic.
     *
     * @param   a   an angle, in radians.
     * @return  the tangent of the argument.
     */
    public static double tan(double a) {
        return StrictMath.tan(a); // default impl. delegates to StrictMath
    }

    /**
     * Returns the arc sine of a value; the returned angle is in the
     * range -<i>pi</i>/2 through <i>pi</i>/2.  Special cases:
     * <ul><li>If the argument is NaN or its absolute value is greater
     * than 1, then the result is NaN.
     * <li>If the argument is zero, then the result is a zero with the
     * same sign as the argument.</ul>
     *
     * <p>The computed result must be within 1 ulp of the exact result.
     * Results must be semi-monotonic.
     *
     * @param   a   the value whose arc sine is to be returned.
     * @return  the arc sine of the argument.
     */
    public static double asin(double a) {
        return StrictMath.asin(a); // default impl. delegates to StrictMath
    }

    /**
     * Returns the arc cosine of a value; the returned angle is in the
     * range 0.0 through <i>pi</i>.  Special case:
     * <ul><li>If the argument is NaN or its absolute value is greater
     * than 1, then the result is NaN.</ul>
     *
     * <p>The computed result must be within 1 ulp of the exact result.
     * Results must be semi-monotonic.
     *
     * @param   a   the value whose arc cosine is to be returned.
     * @return  the arc cosine of the argument.
     */
    public static double acos(double a) {
        return StrictMath.acos(a); // default impl. delegates to StrictMath
    }

    /**
     * Returns the arc tangent of a value; the returned angle is in the
     * range -<i>pi</i>/2 through <i>pi</i>/2.  Special cases:
     * <ul><li>If the argument is NaN, then the result is NaN.
     * <li>If the argument is zero, then the result is a zero with the
     * same sign as the argument.</ul>
     *
     * <p>The computed result must be within 1 ulp of the exact result.
     * Results must be semi-monotonic.
     *
     * @param   a   the value whose arc tangent is to be returned.
     * @return  the arc tangent of the argument.
     */
    public static double atan(double a) {
        return StrictMath.atan(a); // default impl. delegates to StrictMath
    }

    /**
     * Converts an angle measured in degrees to an approximately
     * equivalent angle measured in radians.  The conversion from
     * degrees to radians is generally inexact.
     *
     * @param   angdeg   an angle, in degrees
     * @return  the measurement of the angle {@code angdeg}
     *          in radians.
     * @since   1.2
     */
    public static double toRadians(double angdeg) {
        return angdeg / 180.0 * PI;
    }

    /**
     * Converts an angle measured in radians to an approximately
     * equivalent angle measured in degrees.  The conversion from
     * radians to degrees is generally inexact; users should
     * <i>not</i> expect {@code cos(toRadians(90.0))} to exactly
     * equal {@code 0.0}.
     *
     * @param   angrad   an angle, in radians
     * @return  the measurement of the angle {@code angrad}
     *          in degrees.
     * @since   1.2
     */
    public static double toDegrees(double angrad) {
        return angrad * 180.0 / PI;
    }

    /**
     * Returns Euler's number <i>e</i> raised to the power of a
     * {@code double} value.  Special cases:
     * <ul><li>If the argument is NaN, the result is NaN.
     * <li>If the argument is positive infinity, then the result is
     * positive infinity.
     * <li>If the argument is negative infinity, then the result is
     * positive zero.</ul>
     *
     * <p>The computed result must be within 1 ulp of the exact result.
     * Results must be semi-monotonic.
     *
     * @param   a   the exponent to raise <i>e</i> to.
     * @return  the value <i>e</i><sup>{@code a}</sup>,
     *          where <i>e</i> is the base of the natural logarithms.
     */
    public static double exp(double a) {
        return StrictMath.exp(a); // default impl. delegates to StrictMath
    }

    /**
     * Returns the natural logarithm (base <i>e</i>) of a {@code double}
     * value.  Special cases:
     * <ul><li>If the argument is NaN or less than zero, then the result
     * is NaN.
     * <li>If the argument is positive infinity, then the result is
     * positive infinity.
     * <li>If the argument is positive zero or negative zero, then the
     * result is negative infinity.</ul>
     *
     * <p>The computed result must be within 1 ulp of the exact result.
     * Results must be semi-monotonic.
     *
     * @param   a   a value
     * @return  the value ln&nbsp;{@code a}, the natural logarithm of
     *          {@code a}.
     */
    public static double log(double a) {
        return StrictMath.log(a); // default impl. delegates to StrictMath
    }

    /**
     * Returns the base 10 logarithm of a {@code double} value.
     * Special cases:
     *
     * <ul><li>If the argument is NaN or less than zero, then the result
     * is NaN.
     * <li>If the argument is positive infinity, then the result is
     * positive infinity.
     * <li>If the argument is positive zero or negative zero, then the
     * result is negative infinity.
     * <li> If the argument is equal to 10<sup><i>n</i></sup> for
     * integer <i>n</i>, then the result is <i>n</i>.
     * </ul>
     *
     * <p>The computed result must be within 1 ulp of the exact result.
     * Results must be semi-monotonic.
     *
     * @param   a   a value
     * @return  the base 10 logarithm of  {@code a}.
     * @since 1.5
     */
    public static double log10(double a) {
        return StrictMath.log10(a); // default impl. delegates to StrictMath
    }

    /**
     * Returns the correctly rounded positive square root of a
     * {@code double} value.
     * Special cases:
     * <ul><li>If the argument is NaN or less than zero, then the result
     * is NaN.
     * <li>If the argument is positive infinity, then the result is positive
     * infinity.
     * <li>If the argument is positive zero or negative zero, then the
     * result is the same as the argument.</ul>
     * Otherwise, the result is the {@code double} value closest to
     * the true mathematical square root of the argument value.
     *
     * @param   a   a value.
     * @return  the positive square root of {@code a}.
     *          If the argument is NaN or less than zero, the result is NaN.
     */
    public static double sqrt(double a) {
        return StrictMath.sqrt(a); // default impl. delegates to StrictMath
                                   // Note that hardware sqrt instructions
                                   // frequently can be directly used by JITs
                                   // and should be much faster than doing
                                   // Math.sqrt in software.
    }


    /**
     * Returns the cube root of a {@code double} value.  For
     * positive finite {@code x}, {@code cbrt(-x) ==
     * -cbrt(x)}; that is, the cube root of a negative value is
     * the negative of the cube root of that value's magnitude.
     *
     * Special cases:
     *
     * <ul>
     *
     * <li>If the argument is NaN, then the result is NaN.
     *
     * <li>If the argument is infinite, then the result is an infinity
     * with the same sign as the argument.
     *
     * <li>If the argument is zero, then the result is a zero with the
     * same sign as the argument.
     *
     * </ul>
     *
     * <p>The computed result must be within 1 ulp of the exact result.
     *
     * @param   a   a value.
     * @return  the cube root of {@code a}.
     * @since 1.5
     */
    public static double cbrt(double a) {
        return StrictMath.cbrt(a);
    }

    /**
     * Computes the remainder operation on two arguments as prescribed
     * by the IEEE 754 standard.
     * The remainder value is mathematically equal to
     * <code>f1&nbsp;-&nbsp;f2</code>&nbsp;&times;&nbsp;<i>n</i>,
     * where <i>n</i> is the mathematical integer closest to the exact
     * mathematical value of the quotient {@code f1/f2}, and if two
     * mathematical integers are equally close to {@code f1/f2},
     * then <i>n</i> is the integer that is even. If the remainder is
     * zero, its sign is the same as the sign of the first argument.
     * Special cases:
     * <ul><li>If either argument is NaN, or the first argument is infinite,
     * or the second argument is positive zero or negative zero, then the
     * result is NaN.
     * <li>If the first argument is finite and the second argument is
     * infinite, then the result is the same as the first argument.</ul>
     *
     * @param   f1   the dividend.
     * @param   f2   the divisor.
     * @return  the remainder when {@code f1} is divided by
     *          {@code f2}.
     */
    public static double IEEEremainder(double f1, double f2) {
        return StrictMath.IEEEremainder(f1, f2); // delegate to StrictMath
    }

    /**
     * Returns the smallest (closest to negative infinity)
     * {@code double} value that is greater than or equal to the
     * argument and is equal to a mathematical integer. Special cases:
     * <ul><li>If the argument value is already equal to a
     * mathematical integer, then the result is the same as the
     * argument.  <li>If the argument is NaN or an infinity or
     * positive zero or negative zero, then the result is the same as
     * the argument.  <li>If the argument value is less than zero but
     * greater than -1.0, then the result is negative zero.</ul> Note
     * that the value of {@code Math.ceil(x)} is exactly the
     * value of {@code -Math.floor(-x)}.
     *
     *
     * @param   a   a value.
     * @return  the smallest (closest to negative infinity)
     *          floating-point value that is greater than or equal to
     *          the argument and is equal to a mathematical integer.
     */
    public static double ceil(double a) {
        return StrictMath.ceil(a); // default impl. delegates to StrictMath
    }

    /**
     * Returns the largest (closest to positive infinity)
     * {@code double} value that is less than or equal to the
     * argument and is equal to a mathematical integer. Special cases:
     * <ul><li>If the argument value is already equal to a
     * mathematical integer, then the result is the same as the
     * argument.  <li>If the argument is NaN or an infinity or
     * positive zero or negative zero, then the result is the same as
     * the argument.</ul>
     *
     * @param   a   a value.
     * @return  the largest (closest to positive infinity)
     *          floating-point value that less than or equal to the argument
     *          and is equal to a mathematical integer.
     */
    public static double floor(double a) {
        return StrictMath.floor(a); // default impl. delegates to StrictMath
    }

    /**
     * Returns the {@code double} value that is closest in value
     * to the argument and is equal to a mathematical integer. If two
     * {@code double} values that are mathematical integers are
     * equally close, the result is the integer value that is
     * even. Special cases:
     * <ul><li>If the argument value is already equal to a mathematical
     * integer, then the result is the same as the argument.
     * <li>If the argument is NaN or an infinity or positive zero or negative
     * zero, then the result is the same as the argument.</ul>
     *
     * @param   a   a {@code double} value.
     * @return  the closest floating-point value to {@code a} that is
     *          equal to a mathematical integer.
     */
    public static double rint(double a) {
        return StrictMath.rint(a); // default impl. delegates to StrictMath
    }

    /**
     * Returns the angle <i>theta</i> from the conversion of rectangular
     * coordinates ({@code x},&nbsp;{@code y}) to polar
     * coordinates (r,&nbsp;<i>theta</i>).
     * This method computes the phase <i>theta</i> by computing an arc tangent
     * of {@code y/x} in the range of -<i>pi</i> to <i>pi</i>. Special
     * cases:
     * <ul><li>If either argument is NaN, then the result is NaN.
     * <li>If the first argument is positive zero and the second argument
     * is positive, or the first argument is positive and finite and the
     * second argument is positive infinity, then the result is positive
     * zero.
     * <li>If the first argument is negative zero and the second argument
     * is positive, or the first argument is negative and finite and the
     * second argument is positive infinity, then the result is negative zero.
     * <li>If the first argument is positive zero and the second argument
     * is negative, or the first argument is positive and finite and the
     * second argument is negative infinity, then the result is the
     * {@code double} value closest to <i>pi</i>.
     * <li>If the first argument is negative zero and the second argument
     * is negative, or the first argument is negative and finite and the
     * second argument is negative infinity, then the result is the
     * {@code double} value closest to -<i>pi</i>.
     * <li>If the first argument is positive and the second argument is
     * positive zero or negative zero, or the first argument is positive
     * infinity and the second argument is finite, then the result is the
     * {@code double} value closest to <i>pi</i>/2.
     * <li>If the first argument is negative and the second argument is
     * positive zero or negative zero, or the first argument is negative
     * infinity and the second argument is finite, then the result is the
     * {@code double} value closest to -<i>pi</i>/2.
     * <li>If both arguments are positive infinity, then the result is the
     * {@code double} value closest to <i>pi</i>/4.
     * <li>If the first argument is positive infinity and the second argument
     * is negative infinity, then the result is the {@code double}
     * value closest to 3*<i>pi</i>/4.
     * <li>If the first argument is negative infinity and the second argument
     * is positive infinity, then the result is the {@code double} value
     * closest to -<i>pi</i>/4.
     * <li>If both arguments are negative infinity, then the result is the
     * {@code double} value closest to -3*<i>pi</i>/4.</ul>
     *
     * <p>The computed result must be within 2 ulps of the exact result.
     * Results must be semi-monotonic.
     *
     * @param   y   the ordinate coordinate
     * @param   x   the abscissa coordinate
     * @return  the <i>theta</i> component of the point
     *          (<i>r</i>,&nbsp;<i>theta</i>)
     *          in polar coordinates that corresponds to the point
     *          (<i>x</i>,&nbsp;<i>y</i>) in Cartesian coordinates.
     */
    public static double atan2(double y, double x) {
        return StrictMath.atan2(y, x); // default impl. delegates to StrictMath
    }

    /**
     * Returns the value of the first argument raised to the power of the
     * second argument. Special cases:
     *
     * <ul><li>If the second argument is positive or negative zero, then the
     * result is 1.0.
     * <li>If the second argument is 1.0, then the result is the same as the
     * first argument.
     * <li>If the second argument is NaN, then the result is NaN.
     * <li>If the first argument is NaN and the second argument is nonzero,
     * then the result is NaN.
     *
     * <li>If
     * <ul>
     * <li>the absolute value of the first argument is greater than 1
     * and the second argument is positive infinity, or
     * <li>the absolute value of the first argument is less than 1 and
     * the second argument is negative infinity,
     * </ul>
     * then the result is positive infinity.
     *
     * <li>If
     * <ul>
     * <li>the absolute value of the first argument is greater than 1 and
     * the second argument is negative infinity, or
     * <li>the absolute value of the
     * first argument is less than 1 and the second argument is positive
     * infinity,
     * </ul>
     * then the result is positive zero.
     *
     * <li>If the absolute value of the first argument equals 1 and the
     * second argument is infinite, then the result is NaN.
     *
     * <li>If
     * <ul>
     * <li>the first argument is positive zero and the second argument
     * is greater than zero, or
     * <li>the first argument is positive infinity and the second
     * argument is less than zero,
     * </ul>
     * then the result is positive zero.
     *
     * <li>If
     * <ul>
     * <li>the first argument is positive zero and the second argument
     * is less than zero, or
     * <li>the first argument is positive infinity and the second
     * argument is greater than zero,
     * </ul>
     * then the result is positive infinity.
     *
     * <li>If
     * <ul>
     * <li>the first argument is negative zero and the second argument
     * is greater than zero but not a finite odd integer, or
     * <li>the first argument is negative infinity and the second
     * argument is less than zero but not a finite odd integer,
     * </ul>
     * then the result is positive zero.
     *
     * <li>If
     * <ul>
     * <li>the first argument is negative zero and the second argument
     * is a positive finite odd integer, or
     * <li>the first argument is negative infinity and the second
     * argument is a negative finite odd integer,
     * </ul>
     * then the result is negative zero.
     *
     * <li>If
     * <ul>
     * <li>the first argument is negative zero and the second argument
     * is less than zero but not a finite odd integer, or
     * <li>the first argument is negative infinity and the second
     * argument is greater than zero but not a finite odd integer,
     * </ul>
     * then the result is positive infinity.
     *
     * <li>If
     * <ul>
     * <li>the first argument is negative zero and the second argument
     * is a negative finite odd integer, or
     * <li>the first argument is negative infinity and the second
     * argument is a positive finite odd integer,
     * </ul>
     * then the result is negative infinity.
     *
     * <li>If the first argument is finite and less than zero
     * <ul>
     * <li> if the second argument is a finite even integer, the
     * result is equal to the result of raising the absolute value of
     * the first argument to the power of the second argument
     *
     * <li>if the second argument is a finite odd integer, the result
     * is equal to the negative of the result of raising the absolute
     * value of the first argument to the power of the second
     * argument
     *
     * <li>if the second argument is finite and not an integer, then
     * the result is NaN.
     * </ul>
     *
     * <li>If both arguments are integers, then the result is exactly equal
     * to the mathematical result of raising the first argument to the power
     * of the second argument if that result can in fact be represented
     * exactly as a {@code double} value.</ul>
     *
     * <p>(In the foregoing descriptions, a floating-point value is
     * considered to be an integer if and only if it is finite and a
     * fixed point of the method {@link #ceil ceil} or,
     * equivalently, a fixed point of the method {@link #floor
     * floor}. A value is a fixed point of a one-argument
     * method if and only if the result of applying the method to the
     * value is equal to the value.)
     *
     * <p>The computed result must be within 1 ulp of the exact result.
     * Results must be semi-monotonic.
     *
     * @param   a   the base.
     * @param   b   the exponent.
     * @return  the value {@code a}<sup>{@code b}</sup>.
     */
    public static double pow(double a, double b) {
        return StrictMath.pow(a, b); // default impl. delegates to StrictMath
    }

    /**
     * Returns the closest {@code int} to the argument, with ties
     * rounding to positive infinity.
     *
     * <p>
     * Special cases:
     * <ul><li>If the argument is NaN, the result is 0.
     * <li>If the argument is negative infinity or any value less than or
     * equal to the value of {@code Integer.MIN_VALUE}, the result is
     * equal to the value of {@code Integer.MIN_VALUE}.
     * <li>If the argument is positive infinity or any value greater than or
     * equal to the value of {@code Integer.MAX_VALUE}, the result is
     * equal to the value of {@code Integer.MAX_VALUE}.</ul>
     *
     * @param   a   a floating-point value to be rounded to an integer.
     * @return  the value of the argument rounded to the nearest
     *          {@code int} value.
     * @see     java.lang.Integer#MAX_VALUE
     * @see     java.lang.Integer#MIN_VALUE
     */
    public static int round(float a) {
        int intBits = Float.floatToRawIntBits(a);
        int biasedExp = (intBits & FloatConsts.EXP_BIT_MASK)
                >> (FloatConsts.SIGNIFICAND_WIDTH - 1);
        int shift = (FloatConsts.SIGNIFICAND_WIDTH - 2
                + FloatConsts.EXP_BIAS) - biasedExp;
        if ((shift & -32) == 0) { // shift >= 0 && shift < 32
            // a is a finite number such that pow(2,-32) <= ulp(a) < 1
            int r = ((intBits & FloatConsts.SIGNIF_BIT_MASK)
                    | (FloatConsts.SIGNIF_BIT_MASK + 1));
            if (intBits < 0) {
                r = -r;
            }
            // In the comments below each Java expression evaluates to the value
            // the corresponding mathematical expression:
            // (r) evaluates to a / ulp(a)
            // (r >> shift) evaluates to floor(a * 2)
            // ((r >> shift) + 1) evaluates to floor((a + 1/2) * 2)
            // (((r >> shift) + 1) >> 1) evaluates to floor(a + 1/2)
            return ((r >> shift) + 1) >> 1;
        } else {
            // a is either
            // - a finite number with abs(a) < exp(2,FloatConsts.SIGNIFICAND_WIDTH-32) < 1/2
            // - a finite number with ulp(a) >= 1 and hence a is a mathematical integer
            // - an infinity or NaN
            return (int) a;
        }
    }

    /**
     * Returns the closest {@code long} to the argument, with ties
     * rounding to positive infinity.
     *
     * <p>Special cases:
     * <ul><li>If the argument is NaN, the result is 0.
     * <li>If the argument is negative infinity or any value less than or
     * equal to the value of {@code Long.MIN_VALUE}, the result is
     * equal to the value of {@code Long.MIN_VALUE}.
     * <li>If the argument is positive infinity or any value greater than or
     * equal to the value of {@code Long.MAX_VALUE}, the result is
     * equal to the value of {@code Long.MAX_VALUE}.</ul>
     *
     * @param   a   a floating-point value to be rounded to a
     *          {@code long}.
     * @return  the value of the argument rounded to the nearest
     *          {@code long} value.
     * @see     java.lang.Long#MAX_VALUE
     * @see     java.lang.Long#MIN_VALUE
     */
    public static long round(double a) {
        long longBits = Double.doubleToRawLongBits(a);
        long biasedExp = (longBits & DoubleConsts.EXP_BIT_MASK)
                >> (DoubleConsts.SIGNIFICAND_WIDTH - 1);
        long shift = (DoubleConsts.SIGNIFICAND_WIDTH - 2
                + DoubleConsts.EXP_BIAS) - biasedExp;
        if ((shift & -64) == 0) { // shift >= 0 && shift < 64
            // a is a finite number such that pow(2,-64) <= ulp(a) < 1
            long r = ((longBits & DoubleConsts.SIGNIF_BIT_MASK)
                    | (DoubleConsts.SIGNIF_BIT_MASK + 1));
            if (longBits < 0) {
                r = -r;
            }
            // In the comments below each Java expression evaluates to the value
            // the corresponding mathematical expression:
            // (r) evaluates to a / ulp(a)
            // (r >> shift) evaluates to floor(a * 2)
            // ((r >> shift) + 1) evaluates to floor((a + 1/2) * 2)
            // (((r >> shift) + 1) >> 1) evaluates to floor(a + 1/2)
            return ((r >> shift) + 1) >> 1;
        } else {
            // a is either
            // - a finite number with abs(a) < exp(2,DoubleConsts.SIGNIFICAND_WIDTH-64) < 1/2
            // - a finite number with ulp(a) >= 1 and hence a is a mathematical integer
            // - an infinity or NaN
            return (long) a;
        }
    }

    private static final class RandomNumberGeneratorHolder {
        static final Random randomNumberGenerator = new Random();
    }

    /**
     * Returns a {@code double} value with a positive sign, greater
     * than or equal to {@code 0.0} and less than {@code 1.0}.
     * Returned values are chosen pseudorandomly with (approximately)
     * uniform distribution from that range.
     *
     * <p>When this method is first called, it creates a single new
     * pseudorandom-number generator, exactly as if by the expression
     *
     * <blockquote>{@code new java.util.Random()}</blockquote>
     *
     * This new pseudorandom-number generator is used thereafter for
     * all calls to this method and is used nowhere else.
     *
     * <p>This method is properly synchronized to allow correct use by
     * more than one thread. However, if many threads need to generate
     * pseudorandom numbers at a great rate, it may reduce contention
     * for each thread to have its own pseudorandom-number generator.
     *
     * @apiNote
     * As the largest {@code double} value less than {@code 1.0}
     * is {@code Math.nextDown(1.0)}, a value {@code x} in the closed range
     * {@code [x1,x2]} where {@code x1<=x2} may be defined by the statements
     *
     * <blockquote><pre>{@code
     * double f = Math.random()/Math.nextDown(1.0);
     * double x = x1*(1.0 - f) + x2*f;
     * }</pre></blockquote>
     *
     * @return  a pseudorandom {@code double} greater than or equal
     * to {@code 0.0} and less than {@code 1.0}.
     * @see #nextDown(double)
     * @see Random#nextDouble()
     */
    public static double random() {
        return RandomNumberGeneratorHolder.randomNumberGenerator.nextDouble();
    }

    /**
     * Returns the sum of its arguments,
     * throwing an exception if the result overflows an {@code int}.
     *
     * @param x the first value
     * @param y the second value
     * @return the result
     * @throws ArithmeticException if the result overflows an int
     * @since 1.8
     */
    public static int addExact(int x, int y) {
        int r = x + y;
        // HD 2-12 Overflow iff both arguments have the opposite sign of the result
        if (((x ^ r) & (y ^ r)) < 0) {
            throw new ArithmeticException("integer overflow");
        }
        return r;
    }

    /**
     * Returns the sum of its arguments,
     * throwing an exception if the result overflows a {@code long}.
     *
     * @param x the first value
     * @param y the second value
     * @return the result
     * @throws ArithmeticException if the result overflows a long
     * @since 1.8
     */
    public static long addExact(long x, long y) {
        long r = x + y;
        // HD 2-12 Overflow iff both arguments have the opposite sign of the result
        if (((x ^ r) & (y ^ r)) < 0) {
            throw new ArithmeticException("long overflow");
        }
        return r;
    }

    /**
     * Returns the difference of the arguments,
     * throwing an exception if the result overflows an {@code int}.
     *
     * @param x the first value
     * @param y the second value to subtract from the first
     * @return the result
     * @throws ArithmeticException if the result overflows an int
     * @since 1.8
     */
    public static int subtractExact(int x, int y) {
        int r = x - y;
        // HD 2-12 Overflow iff the arguments have different signs and
        // the sign of the result is different than the sign of x
        if (((x ^ y) & (x ^ r)) < 0) {
            throw new ArithmeticException("integer overflow");
        }
        return r;
    }

    /**
     * Returns the difference of the arguments,
     * throwing an exception if the result overflows a {@code long}.
     *
     * @param x the first value
     * @param y the second value to subtract from the first
     * @return the result
     * @throws ArithmeticException if the result overflows a long
     * @since 1.8
     */
    public static long subtractExact(long x, long y) {
        long r = x - y;
        // HD 2-12 Overflow iff the arguments have different signs and
        // the sign of the result is different than the sign of x
        if (((x ^ y) & (x ^ r)) < 0) {
            throw new ArithmeticException("long overflow");
        }
        return r;
    }

    /**
     * Returns the product of the arguments,
     * throwing an exception if the result overflows an {@code int}.
     *
     * @param x the first value
     * @param y the second value
     * @return the result
     * @throws ArithmeticException if the result overflows an int
     * @since 1.8
     */
    public static int multiplyExact(int x, int y) {
        long r = (long)x * (long)y;
        if ((int)r != r) {
            throw new ArithmeticException("integer overflow");
        }
        return (int)r;
    }

    /**
     * Returns the product of the arguments,
     * throwing an exception if the result overflows a {@code long}.
     *
     * @param x the first value
     * @param y the second value
     * @return the result
     * @throws ArithmeticException if the result overflows a long
     * @since 1.8
     */
    public static long multiplyExact(long x, long y) {
        long r = x * y;
        long ax = Math.abs(x);
        long ay = Math.abs(y);
        if (((ax | ay) >>> 31 != 0)) {
            // Some bits greater than 2^31 that might cause overflow
            // Check the result using the divide operator
            // and check for the special case of Long.MIN_VALUE * -1
           if (((y != 0) && (r / y != x)) ||
               (x == Long.MIN_VALUE && y == -1)) {
                throw new ArithmeticException("long overflow");
            }
        }
        return r;
    }

    /**
     * Returns the argument incremented by one, throwing an exception if the
     * result overflows an {@code int}.
     *
     * @param a the value to increment
     * @return the result
     * @throws ArithmeticException if the result overflows an int
     * @since 1.8
     */
    public static int incrementExact(int a) {
        if (a == Integer.MAX_VALUE) {
            throw new ArithmeticException("integer overflow");
        }

        return a + 1;
    }

    /**
     * Returns the argument incremented by one, throwing an exception if the
     * result overflows a {@code long}.
     *
     * @param a the value to increment
     * @return the result
     * @throws ArithmeticException if the result overflows a long
     * @since 1.8
     */
    public static long incrementExact(long a) {
        if (a == Long.MAX_VALUE) {
            throw new ArithmeticException("long overflow");
        }

        return a + 1L;
    }

    /**
     * Returns the argument decremented by one, throwing an exception if the
     * result overflows an {@code int}.
     *
     * @param a the value to decrement
     * @return the result
     * @throws ArithmeticException if the result overflows an int
     * @since 1.8
     */
    public static int decrementExact(int a) {
        if (a == Integer.MIN_VALUE) {
            throw new ArithmeticException("integer overflow");
        }

        return a - 1;
    }

    /**
     * Returns the argument decremented by one, throwing an exception if the
     * result overflows a {@code long}.
     *
     * @param a the value to decrement
     * @return the result
     * @throws ArithmeticException if the result overflows a long
     * @since 1.8
     */
    public static long decrementExact(long a) {
        if (a == Long.MIN_VALUE) {
            throw new ArithmeticException("long overflow");
        }

        return a - 1L;
    }

    /**
     * Returns the negation of the argument, throwing an exception if the
     * result overflows an {@code int}.
     *
     * @param a the value to negate
     * @return the result
     * @throws ArithmeticException if the result overflows an int
     * @since 1.8
     */
    public static int negateExact(int a) {
        if (a == Integer.MIN_VALUE) {
            throw new ArithmeticException("integer overflow");
        }

        return -a;
    }

    /**
     * Returns the negation of the argument, throwing an exception if the
     * result overflows a {@code long}.
     *
     * @param a the value to negate
     * @return the result
     * @throws ArithmeticException if the result overflows a long
     * @since 1.8
     */
    public static long negateExact(long a) {
        if (a == Long.MIN_VALUE) {
            throw new ArithmeticException("long overflow");
        }

        return -a;
    }

    /**
     * Returns the value of the {@code long} argument;
     * throwing an exception if the value overflows an {@code int}.
     *
     * @param value the long value
     * @return the argument as an int
     * @throws ArithmeticException if the {@code argument} overflows an int
     * @since 1.8
     */
    public static int toIntExact(long value) {
        if ((int)value != value) {
            throw new ArithmeticException("integer overflow");
        }
        return (int)value;
    }

    /**
     * Returns the largest (closest to positive infinity)
     * {@code int} value that is less than or equal to the algebraic quotient.
     * There is one special case, if the dividend is the
     * {@linkplain Integer#MIN_VALUE Integer.MIN_VALUE} and the divisor is {@code -1},
     * then integer overflow occurs and
     * the result is equal to the {@code Integer.MIN_VALUE}.
     * <p>
     * Normal integer division operates under the round to zero rounding mode
     * (truncation).  This operation instead acts under the round toward
     * negative infinity (floor) rounding mode.
     * The floor rounding mode gives different results than truncation
     * when the exact result is negative.
     * <ul>
     *   <li>If the signs of the arguments are the same, the results of
     *       {@code floorDiv} and the {@code /} operator are the same.  <br>
     *       For example, {@code floorDiv(4, 3) == 1} and {@code (4 / 3) == 1}.</li>
     *   <li>If the signs of the arguments are different,  the quotient is negative and
     *       {@code floorDiv} returns the integer less than or equal to the quotient
     *       and the {@code /} operator returns the integer closest to zero.<br>
     *       For example, {@code floorDiv(-4, 3) == -2},
     *       whereas {@code (-4 / 3) == -1}.
     *   </li>
     * </ul>
     * <p>
     *
     * @param x the dividend
     * @param y the divisor
     * @return the largest (closest to positive infinity)
     * {@code int} value that is less than or equal to the algebraic quotient.
     * @throws ArithmeticException if the divisor {@code y} is zero
     * @see #floorMod(int, int)
     * @see #floor(double)
     * @since 1.8
     */
    public static int floorDiv(int x, int y) {
        int r = x / y;
        // if the signs are different and modulo not zero, round down
        if ((x ^ y) < 0 && (r * y != x)) {
            r--;
        }
        return r;
    }

    /**
     * Returns the largest (closest to positive infinity)
     * {@code long} value that is less than or equal to the algebraic quotient.
     * There is one special case, if the dividend is the
     * {@linkplain Long#MIN_VALUE Long.MIN_VALUE} and the divisor is {@code -1},
     * then integer overflow occurs and
     * the result is equal to the {@code Long.MIN_VALUE}.
     * <p>
     * Normal integer division operates under the round to zero rounding mode
     * (truncation).  This operation instead acts under the round toward
     * negative infinity (floor) rounding mode.
     * The floor rounding mode gives different results than truncation
     * when the exact result is negative.
     * <p>
     * For examples, see {@link #floorDiv(int, int)}.
     *
     * @param x the dividend
     * @param y the divisor
     * @return the largest (closest to positive infinity)
     * {@code long} value that is less than or equal to the algebraic quotient.
     * @throws ArithmeticException if the divisor {@code y} is zero
     * @see #floorMod(long, long)
     * @see #floor(double)
     * @since 1.8
     */
    public static long floorDiv(long x, long y) {
        long r = x / y;
        // if the signs are different and modulo not zero, round down
        if ((x ^ y) < 0 && (r * y != x)) {
            r--;
        }
        return r;
    }

    /**
     * Returns the floor modulus of the {@code int} arguments.
     * <p>
     * The floor modulus is {@code x - (floorDiv(x, y) * y)},
     * has the same sign as the divisor {@code y}, and
     * is in the range of {@code -abs(y) < r < +abs(y)}.
     *
     * <p>
     * The relationship between {@code floorDiv} and {@code floorMod} is such that:
     * <ul>
     *   <li>{@code floorDiv(x, y) * y + floorMod(x, y) == x}
     * </ul>
     * <p>
     * The difference in values between {@code floorMod} and
     * the {@code %} operator is due to the difference between
     * {@code floorDiv} that returns the integer less than or equal to the quotient
     * and the {@code /} operator that returns the integer closest to zero.
     * <p>
     * Examples:
     * <ul>
     *   <li>If the signs of the arguments are the same, the results
     *       of {@code floorMod} and the {@code %} operator are the same.  <br>
     *       <ul>
     *       <li>{@code floorMod(4, 3) == 1}; &nbsp; and {@code (4 % 3) == 1}</li>
     *       </ul>
     *   <li>If the signs of the arguments are different, the results differ from the {@code %} operator.<br>
     *      <ul>
     *      <li>{@code floorMod(+4, -3) == -2}; &nbsp; and {@code (+4 % -3) == +1} </li>
     *      <li>{@code floorMod(-4, +3) == +2}; &nbsp; and {@code (-4 % +3) == -1} </li>
     *      <li>{@code floorMod(-4, -3) == -1}; &nbsp; and {@code (-4 % -3) == -1 } </li>
     *      </ul>
     *   </li>
     * </ul>
     * <p>
     * If the signs of arguments are unknown and a positive modulus
     * is needed it can be computed as {@code (floorMod(x, y) + abs(y)) % abs(y)}.
     *
     * @param x the dividend
     * @param y the divisor
     * @return the floor modulus {@code x - (floorDiv(x, y) * y)}
     * @throws ArithmeticException if the divisor {@code y} is zero
     * @see #floorDiv(int, int)
     * @since 1.8
     */
    public static int floorMod(int x, int y) {
        int r = x - floorDiv(x, y) * y;
        return r;
    }

    /**
     * Returns the floor modulus of the {@code long} arguments.
     * <p>
     * The floor modulus is {@code x - (floorDiv(x, y) * y)},
     * has the same sign as the divisor {@code y}, and
     * is in the range of {@code -abs(y) < r < +abs(y)}.
     *
     * <p>
     * The relationship between {@code floorDiv} and {@code floorMod} is such that:
     * <ul>
     *   <li>{@code floorDiv(x, y) * y + floorMod(x, y) == x}
     * </ul>
     * <p>
     * For examples, see {@link #floorMod(int, int)}.
     *
     * @param x the dividend
     * @param y the divisor
     * @return the floor modulus {@code x - (floorDiv(x, y) * y)}
     * @throws ArithmeticException if the divisor {@code y} is zero
     * @see #floorDiv(long, long)
     * @since 1.8
     */
    public static long floorMod(long x, long y) {
        return x - floorDiv(x, y) * y;
    }

    /**
     * Returns the absolute value of an {@code int} value.
     * If the argument is not negative, the argument is returned.
     * If the argument is negative, the negation of the argument is returned.
     *
     * <p>Note that if the argument is equal to the value of
     * {@link Integer#MIN_VALUE}, the most negative representable
     * {@code int} value, the result is that same value, which is
     * negative.
     *
     * @param   a   the argument whose absolute value is to be determined
     * @return  the absolute value of the argument.
     */
    public static int abs(int a) {
        return (a < 0) ? -a : a;
    }

    /**
     * Returns the absolute value of a {@code long} value.
     * If the argument is not negative, the argument is returned.
     * If the argument is negative, the negation of the argument is returned.
     *
     * <p>Note that if the argument is equal to the value of
     * {@link Long#MIN_VALUE}, the most negative representable
     * {@code long} value, the result is that same value, which
     * is negative.
     *
     * @param   a   the argument whose absolute value is to be determined
     * @return  the absolute value of the argument.
     */
    public static long abs(long a) {
        return (a < 0) ? -a : a;
    }

    /**
     * Returns the absolute value of a {@code float} value.
     * If the argument is not negative, the argument is returned.
     * If the argument is negative, the negation of the argument is returned.
     * Special cases:
     * <ul><li>If the argument is positive zero or negative zero, the
     * result is positive zero.
     * <li>If the argument is infinite, the result is positive infinity.
     * <li>If the argument is NaN, the result is NaN.</ul>
     * In other words, the result is the same as the value of the expression:
     * <p>{@code Float.intBitsToFloat(0x7fffffff & Float.floatToIntBits(a))}
     *
     * @param   a   the argument whose absolute value is to be determined
     * @return  the absolute value of the argument.
     */
    public static float abs(float a) {
        return (a <= 0.0F) ? 0.0F - a : a;
    }

    /**
     * Returns the absolute value of a {@code double} value.
     * If the argument is not negative, the argument is returned.
     * If the argument is negative, the negation of the argument is returned.
     * Special cases:
     * <ul><li>If the argument is positive zero or negative zero, the result
     * is positive zero.
     * <li>If the argument is infinite, the result is positive infinity.
     * <li>If the argument is NaN, the result is NaN.</ul>
     * In other words, the result is the same as the value of the expression:
     * <p>{@code Double.longBitsToDouble((Double.doubleToLongBits(a)<<1)>>>1)}
     *
     * @param   a   the argument whose absolute value is to be determined
     * @return  the absolute value of the argument.
     */
    public static double abs(double a) {
        return (a <= 0.0D) ? 0.0D - a : a;
    }

    /**
     * Returns the greater of two {@code int} values. That is, the
     * result is the argument closer to the value of
     * {@link Integer#MAX_VALUE}. If the arguments have the same value,
     * the result is that same value.
     *
     * @param   a   an argument.
     * @param   b   another argument.
     * @return  the larger of {@code a} and {@code b}.
     */
    public static int max(int a, int b) {
        return (a >= b) ? a : b;
    }

    /**
     * Returns the greater of two {@code long} values. That is, the
     * result is the argument closer to the value of
     * {@link Long#MAX_VALUE}. If the arguments have the same value,
     * the result is that same value.
     *
     * @param   a   an argument.
     * @param   b   another argument.
     * @return  the larger of {@code a} and {@code b}.
     */
    public static long max(long a, long b) {
        return (a >= b) ? a : b;
    }

    // Use raw bit-wise conversions on guaranteed non-NaN arguments.
    private static long negativeZeroFloatBits  = Float.floatToRawIntBits(-0.0f);
    private static long negativeZeroDoubleBits = Double.doubleToRawLongBits(-0.0d);

    /**
     * Returns the greater of two {@code float} values.  That is,
     * the result is the argument closer to positive infinity. If the
     * arguments have the same value, the result is that same
     * value. If either value is NaN, then the result is NaN.  Unlike
     * the numerical comparison operators, this method considers
     * negative zero to be strictly smaller than positive zero. If one
     * argument is positive zero and the other negative zero, the
     * result is positive zero.
     *
     * @param   a   an argument.
     * @param   b   another argument.
     * @return  the larger of {@code a} and {@code b}.
     */
    public static float max(float a, float b) {
        if (a != a)
            return a;   // a is NaN
        if ((a == 0.0f) &&
            (b == 0.0f) &&
            (Float.floatToRawIntBits(a) == negativeZeroFloatBits)) {
            // Raw conversion ok since NaN can't map to -0.0.
            return b;
        }
        return (a >= b) ? a : b;
    }

    /**
     * Returns the greater of two {@code double} values.  That
     * is, the result is the argument closer to positive infinity. If
     * the arguments have the same value, the result is that same
     * value. If either value is NaN, then the result is NaN.  Unlike
     * the numerical comparison operators, this method considers
     * negative zero to be strictly smaller than positive zero. If one
     * argument is positive zero and the other negative zero, the
     * result is positive zero.
     *
     * @param   a   an argument.
     * @param   b   another argument.
     * @return  the larger of {@code a} and {@code b}.
     */
    public static double max(double a, double b) {
        if (a != a)
            return a;   // a is NaN
        if ((a == 0.0d) &&
            (b == 0.0d) &&
            (Double.doubleToRawLongBits(a) == negativeZeroDoubleBits)) {
            // Raw conversion ok since NaN can't map to -0.0.
            return b;
        }
        return (a >= b) ? a : b;
    }

    /**
     * Returns the smaller of two {@code int} values. That is,
     * the result the argument closer to the value of
     * {@link Integer#MIN_VALUE}.  If the arguments have the same
     * value, the result is that same value.
     *
     * @param   a   an argument.
     * @param   b   another argument.
     * @return  the smaller of {@code a} and {@code b}.
     */
    public static int min(int a, int b) {
        return (a <= b) ? a : b;
    }

    /**
     * Returns the smaller of two {@code long} values. That is,
     * the result is the argument closer to the value of
     * {@link Long#MIN_VALUE}. If the arguments have the same
     * value, the result is that same value.
     *
     * @param   a   an argument.
     * @param   b   another argument.
     * @return  the smaller of {@code a} and {@code b}.
     */
    public static long min(long a, long b) {
        return (a <= b) ? a : b;
    }

    /**
     * Returns the smaller of two {@code float} values.  That is,
     * the result is the value closer to negative infinity. If the
     * arguments have the same value, the result is that same
     * value. If either value is NaN, then the result is NaN.  Unlike
     * the numerical comparison operators, this method considers
     * negative zero to be strictly smaller than positive zero.  If
     * one argument is positive zero and the other is negative zero,
     * the result is negative zero.
     *
     * @param   a   an argument.
     * @param   b   another argument.
     * @return  the smaller of {@code a} and {@code b}.
     */
    public static float min(float a, float b) {
        if (a != a)
            return a;   // a is NaN
        if ((a == 0.0f) &&
            (b == 0.0f) &&
            (Float.floatToRawIntBits(b) == negativeZeroFloatBits)) {
            // Raw conversion ok since NaN can't map to -0.0.
            return b;
        }
        return (a <= b) ? a : b;
    }

    /**
     * Returns the smaller of two {@code double} values.  That
     * is, the result is the value closer to negative infinity. If the
     * arguments have the same value, the result is that same
     * value. If either value is NaN, then the result is NaN.  Unlike
     * the numerical comparison operators, this method considers
     * negative zero to be strictly smaller than positive zero. If one
     * argument is positive zero and the other is negative zero, the
     * result is negative zero.
     *
     * @param   a   an argument.
     * @param   b   another argument.
     * @return  the smaller of {@code a} and {@code b}.
     */
    public static double min(double a, double b) {
        if (a != a)
            return a;   // a is NaN
        if ((a == 0.0d) &&
            (b == 0.0d) &&
            (Double.doubleToRawLongBits(b) == negativeZeroDoubleBits)) {
            // Raw conversion ok since NaN can't map to -0.0.
            return b;
        }
        return (a <= b) ? a : b;
    }

    /**
     * Returns the size of an ulp of the argument.  An ulp, unit in
     * the last place, of a {@code double} value is the positive
     * distance between this floating-point value and the {@code
     * double} value next larger in magnitude.  Note that for non-NaN
     * <i>x</i>, <code>ulp(-<i>x</i>) == ulp(<i>x</i>)</code>.
     *
     * <p>Special Cases:
     * <ul>
     * <li> If the argument is NaN, then the result is NaN.
     * <li> If the argument is positive or negative infinity, then the
     * result is positive infinity.
     * <li> If the argument is positive or negative zero, then the result is
     * {@code Double.MIN_VALUE}.
     * <li> If the argument is &plusmn;{@code Double.MAX_VALUE}, then
     * the result is equal to 2<sup>971</sup>.
     * </ul>
     *
     * @param d the floating-point value whose ulp is to be returned
     * @return the size of an ulp of the argument
     * @author Joseph D. Darcy
     * @since 1.5
     */
    public static double ulp(double d) {
        int exp = getExponent(d);

        switch(exp) {
        case DoubleConsts.MAX_EXPONENT+1:       // NaN or infinity
            return Math.abs(d);

        case DoubleConsts.MIN_EXPONENT-1:       // zero or subnormal
            return Double.MIN_VALUE;

        default:
            assert exp <= DoubleConsts.MAX_EXPONENT && exp >= DoubleConsts.MIN_EXPONENT;

            // ulp(x) is usually 2^(SIGNIFICAND_WIDTH-1)*(2^ilogb(x))
            exp = exp - (DoubleConsts.SIGNIFICAND_WIDTH-1);
            if (exp >= DoubleConsts.MIN_EXPONENT) {
                return powerOfTwoD(exp);
            }
            else {
                // return a subnormal result; left shift integer
                // representation of Double.MIN_VALUE appropriate
                // number of positions
                return Double.longBitsToDouble(1L <<
                (exp - (DoubleConsts.MIN_EXPONENT - (DoubleConsts.SIGNIFICAND_WIDTH-1)) ));
            }
        }
    }

    /**
     * Returns the size of an ulp of the argument.  An ulp, unit in
     * the last place, of a {@code float} value is the positive
     * distance between this floating-point value and the {@code
     * float} value next larger in magnitude.  Note that for non-NaN
     * <i>x</i>, <code>ulp(-<i>x</i>) == ulp(<i>x</i>)</code>.
     *
     * <p>Special Cases:
     * <ul>
     * <li> If the argument is NaN, then the result is NaN.
     * <li> If the argument is positive or negative infinity, then the
     * result is positive infinity.
     * <li> If the argument is positive or negative zero, then the result is
     * {@code Float.MIN_VALUE}.
     * <li> If the argument is &plusmn;{@code Float.MAX_VALUE}, then
     * the result is equal to 2<sup>104</sup>.
     * </ul>
     *
     * @param f the floating-point value whose ulp is to be returned
     * @return the size of an ulp of the argument
     * @author Joseph D. Darcy
     * @since 1.5
     */
    public static float ulp(float f) {
        int exp = getExponent(f);

        switch(exp) {
        case FloatConsts.MAX_EXPONENT+1:        // NaN or infinity
            return Math.abs(f);

        case FloatConsts.MIN_EXPONENT-1:        // zero or subnormal
            return FloatConsts.MIN_VALUE;

        default:
            assert exp <= FloatConsts.MAX_EXPONENT && exp >= FloatConsts.MIN_EXPONENT;

            // ulp(x) is usually 2^(SIGNIFICAND_WIDTH-1)*(2^ilogb(x))
            exp = exp - (FloatConsts.SIGNIFICAND_WIDTH-1);
            if (exp >= FloatConsts.MIN_EXPONENT) {
                return powerOfTwoF(exp);
            }
            else {
                // return a subnormal result; left shift integer
                // representation of FloatConsts.MIN_VALUE appropriate
                // number of positions
                return Float.intBitsToFloat(1 <<
                (exp - (FloatConsts.MIN_EXPONENT - (FloatConsts.SIGNIFICAND_WIDTH-1)) ));
            }
        }
    }

    /**
     * Returns the signum function of the argument; zero if the argument
     * is zero, 1.0 if the argument is greater than zero, -1.0 if the
     * argument is less than zero.
     *
     * <p>Special Cases:
     * <ul>
     * <li> If the argument is NaN, then the result is NaN.
     * <li> If the argument is positive zero or negative zero, then the
     *      result is the same as the argument.
     * </ul>
     *
     * @param d the floating-point value whose signum is to be returned
     * @return the signum function of the argument
     * @author Joseph D. Darcy
     * @since 1.5
     */
    public static double signum(double d) {
        return (d == 0.0 || Double.isNaN(d))?d:copySign(1.0, d);
    }

    /**
     * Returns the signum function of the argument; zero if the argument
     * is zero, 1.0f if the argument is greater than zero, -1.0f if the
     * argument is less than zero.
     *
     * <p>Special Cases:
     * <ul>
     * <li> If the argument is NaN, then the result is NaN.
     * <li> If the argument is positive zero or negative zero, then the
     *      result is the same as the argument.
     * </ul>
     *
     * @param f the floating-point value whose signum is to be returned
     * @return the signum function of the argument
     * @author Joseph D. Darcy
     * @since 1.5
     */
    public static float signum(float f) {
        return (f == 0.0f || Float.isNaN(f))?f:copySign(1.0f, f);
    }

    /**
     * Returns the hyperbolic sine of a {@code double} value.
     * The hyperbolic sine of <i>x</i> is defined to be
     * (<i>e<sup>x</sup>&nbsp;-&nbsp;e<sup>-x</sup></i>)/2
     * where <i>e</i> is {@linkplain Math#E Euler's number}.
     *
     * <p>Special cases:
     * <ul>
     *
     * <li>If the argument is NaN, then the result is NaN.
     *
     * <li>If the argument is infinite, then the result is an infinity
     * with the same sign as the argument.
     *
     * <li>If the argument is zero, then the result is a zero with the
     * same sign as the argument.
     *
     * </ul>
     *
     * <p>The computed result must be within 2.5 ulps of the exact result.
     *
     * @param   x The number whose hyperbolic sine is to be returned.
     * @return  The hyperbolic sine of {@code x}.
     * @since 1.5
     */
    public static double sinh(double x) {
        return StrictMath.sinh(x);
    }

    /**
     * Returns the hyperbolic cosine of a {@code double} value.
     * The hyperbolic cosine of <i>x</i> is defined to be
     * (<i>e<sup>x</sup>&nbsp;+&nbsp;e<sup>-x</sup></i>)/2
     * where <i>e</i> is {@linkplain Math#E Euler's number}.
     *
     * <p>Special cases:
     * <ul>
     *
     * <li>If the argument is NaN, then the result is NaN.
     *
     * <li>If the argument is infinite, then the result is positive
     * infinity.
     *
     * <li>If the argument is zero, then the result is {@code 1.0}.
     *
     * </ul>
     *
     * <p>The computed result must be within 2.5 ulps of the exact result.
     *
     * @param   x The number whose hyperbolic cosine is to be returned.
     * @return  The hyperbolic cosine of {@code x}.
     * @since 1.5
     */
    public static double cosh(double x) {
        return StrictMath.cosh(x);
    }

    /**
     * Returns the hyperbolic tangent of a {@code double} value.
     * The hyperbolic tangent of <i>x</i> is defined to be
     * (<i>e<sup>x</sup>&nbsp;-&nbsp;e<sup>-x</sup></i>)/(<i>e<sup>x</sup>&nbsp;+&nbsp;e<sup>-x</sup></i>),
     * in other words, {@linkplain Math#sinh
     * sinh(<i>x</i>)}/{@linkplain Math#cosh cosh(<i>x</i>)}.  Note
     * that the absolute value of the exact tanh is always less than
     * 1.
     *
     * <p>Special cases:
     * <ul>
     *
     * <li>If the argument is NaN, then the result is NaN.
     *
     * <li>If the argument is zero, then the result is a zero with the
     * same sign as the argument.
     *
     * <li>If the argument is positive infinity, then the result is
     * {@code +1.0}.
     *
     * <li>If the argument is negative infinity, then the result is
     * {@code -1.0}.
     *
     * </ul>
     *
     * <p>The computed result must be within 2.5 ulps of the exact result.
     * The result of {@code tanh} for any finite input must have
     * an absolute value less than or equal to 1.  Note that once the
     * exact result of tanh is within 1/2 of an ulp of the limit value
     * of &plusmn;1, correctly signed &plusmn;{@code 1.0} should
     * be returned.
     *
     * @param   x The number whose hyperbolic tangent is to be returned.
     * @return  The hyperbolic tangent of {@code x}.
     * @since 1.5
     */
    public static double tanh(double x) {
        return StrictMath.tanh(x);
    }

    /**
     * Returns sqrt(<i>x</i><sup>2</sup>&nbsp;+<i>y</i><sup>2</sup>)
     * without intermediate overflow or underflow.
     *
     * <p>Special cases:
     * <ul>
     *
     * <li> If either argument is infinite, then the result
     * is positive infinity.
     *
     * <li> If either argument is NaN and neither argument is infinite,
     * then the result is NaN.
     *
     * </ul>
     *
     * <p>The computed result must be within 1 ulp of the exact
     * result.  If one parameter is held constant, the results must be
     * semi-monotonic in the other parameter.
     *
     * @param x a value
     * @param y a value
     * @return sqrt(<i>x</i><sup>2</sup>&nbsp;+<i>y</i><sup>2</sup>)
     * without intermediate overflow or underflow
     * @since 1.5
     */
    public static double hypot(double x, double y) {
        return StrictMath.hypot(x, y);
    }

    /**
     * Returns <i>e</i><sup>x</sup>&nbsp;-1.  Note that for values of
     * <i>x</i> near 0, the exact sum of
     * {@code expm1(x)}&nbsp;+&nbsp;1 is much closer to the true
     * result of <i>e</i><sup>x</sup> than {@code exp(x)}.
     *
     * <p>Special cases:
     * <ul>
     * <li>If the argument is NaN, the result is NaN.
     *
     * <li>If the argument is positive infinity, then the result is
     * positive infinity.
     *
     * <li>If the argument is negative infinity, then the result is
     * -1.0.
     *
     * <li>If the argument is zero, then the result is a zero with the
     * same sign as the argument.
     *
     * </ul>
     *
     * <p>The computed result must be within 1 ulp of the exact result.
     * Results must be semi-monotonic.  The result of
     * {@code expm1} for any finite input must be greater than or
     * equal to {@code -1.0}.  Note that once the exact result of
     * <i>e</i><sup>{@code x}</sup>&nbsp;-&nbsp;1 is within 1/2
     * ulp of the limit value -1, {@code -1.0} should be
     * returned.
     *
     * @param   x   the exponent to raise <i>e</i> to in the computation of
     *              <i>e</i><sup>{@code x}</sup>&nbsp;-1.
     * @return  the value <i>e</i><sup>{@code x}</sup>&nbsp;-&nbsp;1.
     * @since 1.5
     */
    public static double expm1(double x) {
        return StrictMath.expm1(x);
    }

    /**
     * Returns the natural logarithm of the sum of the argument and 1.
     * Note that for small values {@code x}, the result of
     * {@code log1p(x)} is much closer to the true result of ln(1
     * + {@code x}) than the floating-point evaluation of
     * {@code log(1.0+x)}.
     *
     * <p>Special cases:
     *
     * <ul>
     *
     * <li>If the argument is NaN or less than -1, then the result is
     * NaN.
     *
     * <li>If the argument is positive infinity, then the result is
     * positive infinity.
     *
     * <li>If the argument is negative one, then the result is
     * negative infinity.
     *
     * <li>If the argument is zero, then the result is a zero with the
     * same sign as the argument.
     *
     * </ul>
     *
     * <p>The computed result must be within 1 ulp of the exact result.
     * Results must be semi-monotonic.
     *
     * @param   x   a value
     * @return the value ln({@code x}&nbsp;+&nbsp;1), the natural
     * log of {@code x}&nbsp;+&nbsp;1
     * @since 1.5
     */
    public static double log1p(double x) {
        return StrictMath.log1p(x);
    }

    /**
     * Returns the first floating-point argument with the sign of the
     * second floating-point argument.  Note that unlike the {@link
     * StrictMath#copySign(double, double) StrictMath.copySign}
     * method, this method does not require NaN {@code sign}
     * arguments to be treated as positive values; implementations are
     * permitted to treat some NaN arguments as positive and other NaN
     * arguments as negative to allow greater performance.
     *
     * @param magnitude  the parameter providing the magnitude of the result
     * @param sign   the parameter providing the sign of the result
     * @return a value with the magnitude of {@code magnitude}
     * and the sign of {@code sign}.
     * @since 1.6
     */
    public static double copySign(double magnitude, double sign) {
        return Double.longBitsToDouble((Double.doubleToRawLongBits(sign) &
                                        (DoubleConsts.SIGN_BIT_MASK)) |
                                       (Double.doubleToRawLongBits(magnitude) &
                                        (DoubleConsts.EXP_BIT_MASK |
                                         DoubleConsts.SIGNIF_BIT_MASK)));
    }

    /**
     * Returns the first floating-point argument with the sign of the
     * second floating-point argument.  Note that unlike the {@link
     * StrictMath#copySign(float, float) StrictMath.copySign}
     * method, this method does not require NaN {@code sign}
     * arguments to be treated as positive values; implementations are
     * permitted to treat some NaN arguments as positive and other NaN
     * arguments as negative to allow greater performance.
     *
     * @param magnitude  the parameter providing the magnitude of the result
     * @param sign   the parameter providing the sign of the result
     * @return a value with the magnitude of {@code magnitude}
     * and the sign of {@code sign}.
     * @since 1.6
     */
    public static float copySign(float magnitude, float sign) {
        return Float.intBitsToFloat((Float.floatToRawIntBits(sign) &
                                     (FloatConsts.SIGN_BIT_MASK)) |
                                    (Float.floatToRawIntBits(magnitude) &
                                     (FloatConsts.EXP_BIT_MASK |
                                      FloatConsts.SIGNIF_BIT_MASK)));
    }

    /**
     * Returns the unbiased exponent used in the representation of a
     * {@code float}.  Special cases:
     *
     * <ul>
     * <li>If the argument is NaN or infinite, then the result is
     * {@link Float#MAX_EXPONENT} + 1.
     * <li>If the argument is zero or subnormal, then the result is
     * {@link Float#MIN_EXPONENT} -1.
     * </ul>
     * @param f a {@code float} value
     * @return the unbiased exponent of the argument
     * @since 1.6
     */
    public static int getExponent(float f) {
        /*
         * Bitwise convert f to integer, mask out exponent bits, shift
         * to the right and then subtract out float's bias adjust to
         * get true exponent value
         */
        return ((Float.floatToRawIntBits(f) & FloatConsts.EXP_BIT_MASK) >>
                (FloatConsts.SIGNIFICAND_WIDTH - 1)) - FloatConsts.EXP_BIAS;
    }

    /**
     * Returns the unbiased exponent used in the representation of a
     * {@code double}.  Special cases:
     *
     * <ul>
     * <li>If the argument is NaN or infinite, then the result is
     * {@link Double#MAX_EXPONENT} + 1.
     * <li>If the argument is zero or subnormal, then the result is
     * {@link Double#MIN_EXPONENT} -1.
     * </ul>
     * @param d a {@code double} value
     * @return the unbiased exponent of the argument
     * @since 1.6
     */
    public static int getExponent(double d) {
        /*
         * Bitwise convert d to long, mask out exponent bits, shift
         * to the right and then subtract out double's bias adjust to
         * get true exponent value.
         */
        return (int)(((Double.doubleToRawLongBits(d) & DoubleConsts.EXP_BIT_MASK) >>
                      (DoubleConsts.SIGNIFICAND_WIDTH - 1)) - DoubleConsts.EXP_BIAS);
    }

    /**
     * Returns the floating-point number adjacent to the first
     * argument in the direction of the second argument.  If both
     * arguments compare as equal the second argument is returned.
     *
     * <p>
     * Special cases:
     * <ul>
     * <li> If either argument is a NaN, then NaN is returned.
     *
     * <li> If both arguments are signed zeros, {@code direction}
     * is returned unchanged (as implied by the requirement of
     * returning the second argument if the arguments compare as
     * equal).
     *
     * <li> If {@code start} is
     * &plusmn;{@link Double#MIN_VALUE} and {@code direction}
     * has a value such that the result should have a smaller
     * magnitude, then a zero with the same sign as {@code start}
     * is returned.
     *
     * <li> If {@code start} is infinite and
     * {@code direction} has a value such that the result should
     * have a smaller magnitude, {@link Double#MAX_VALUE} with the
     * same sign as {@code start} is returned.
     *
     * <li> If {@code start} is equal to &plusmn;
     * {@link Double#MAX_VALUE} and {@code direction} has a
     * value such that the result should have a larger magnitude, an
     * infinity with same sign as {@code start} is returned.
     * </ul>
     *
     * @param start  starting floating-point value
     * @param direction value indicating which of
     * {@code start}'s neighbors or {@code start} should
     * be returned
     * @return The floating-point number adjacent to {@code start} in the
     * direction of {@code direction}.
     * @since 1.6
     */
    public static double nextAfter(double start, double direction) {
        /*
         * The cases:
         *
         * nextAfter(+infinity, 0)  == MAX_VALUE
         * nextAfter(+infinity, +infinity)  == +infinity
         * nextAfter(-infinity, 0)  == -MAX_VALUE
         * nextAfter(-infinity, -infinity)  == -infinity
         *
         * are naturally handled without any additional testing
         */

        // First check for NaN values
        if (Double.isNaN(start) || Double.isNaN(direction)) {
            // return a NaN derived from the input NaN(s)
            return start + direction;
        } else if (start == direction) {
            return direction;
        } else {        // start > direction or start < direction
            // Add +0.0 to get rid of a -0.0 (+0.0 + -0.0 => +0.0)
            // then bitwise convert start to integer.
            long transducer = Double.doubleToRawLongBits(start + 0.0d);

            /*
             * IEEE 754 floating-point numbers are lexicographically
             * ordered if treated as signed- magnitude integers .
             * Since Java's integers are two's complement,
             * incrementing" the two's complement representation of a
             * logically negative floating-point value *decrements*
             * the signed-magnitude representation. Therefore, when
             * the integer representation of a floating-point values
             * is less than zero, the adjustment to the representation
             * is in the opposite direction than would be expected at
             * first .
             */
            if (direction > start) { // Calculate next greater value
                transducer = transducer + (transducer >= 0L ? 1L:-1L);
            } else  { // Calculate next lesser value
                assert direction < start;
                if (transducer > 0L)
                    --transducer;
                else
                    if (transducer < 0L )
                        ++transducer;
                    /*
                     * transducer==0, the result is -MIN_VALUE
                     *
                     * The transition from zero (implicitly
                     * positive) to the smallest negative
                     * signed magnitude value must be done
                     * explicitly.
                     */
                    else
                        transducer = DoubleConsts.SIGN_BIT_MASK | 1L;
            }

            return Double.longBitsToDouble(transducer);
        }
    }

    /**
     * Returns the floating-point number adjacent to the first
     * argument in the direction of the second argument.  If both
     * arguments compare as equal a value equivalent to the second argument
     * is returned.
     *
     * <p>
     * Special cases:
     * <ul>
     * <li> If either argument is a NaN, then NaN is returned.
     *
     * <li> If both arguments are signed zeros, a value equivalent
     * to {@code direction} is returned.
     *
     * <li> If {@code start} is
     * &plusmn;{@link Float#MIN_VALUE} and {@code direction}
     * has a value such that the result should have a smaller
     * magnitude, then a zero with the same sign as {@code start}
     * is returned.
     *
     * <li> If {@code start} is infinite and
     * {@code direction} has a value such that the result should
     * have a smaller magnitude, {@link Float#MAX_VALUE} with the
     * same sign as {@code start} is returned.
     *
     * <li> If {@code start} is equal to &plusmn;
     * {@link Float#MAX_VALUE} and {@code direction} has a
     * value such that the result should have a larger magnitude, an
     * infinity with same sign as {@code start} is returned.
     * </ul>
     *
     * @param start  starting floating-point value
     * @param direction value indicating which of
     * {@code start}'s neighbors or {@code start} should
     * be returned
     * @return The floating-point number adjacent to {@code start} in the
     * direction of {@code direction}.
     * @since 1.6
     */
    public static float nextAfter(float start, double direction) {
        /*
         * The cases:
         *
         * nextAfter(+infinity, 0)  == MAX_VALUE
         * nextAfter(+infinity, +infinity)  == +infinity
         * nextAfter(-infinity, 0)  == -MAX_VALUE
         * nextAfter(-infinity, -infinity)  == -infinity
         *
         * are naturally handled without any additional testing
         */

        // First check for NaN values
        if (Float.isNaN(start) || Double.isNaN(direction)) {
            // return a NaN derived from the input NaN(s)
            return start + (float)direction;
        } else if (start == direction) {
            return (float)direction;
        } else {        // start > direction or start < direction
            // Add +0.0 to get rid of a -0.0 (+0.0 + -0.0 => +0.0)
            // then bitwise convert start to integer.
            int transducer = Float.floatToRawIntBits(start + 0.0f);

            /*
             * IEEE 754 floating-point numbers are lexicographically
             * ordered if treated as signed- magnitude integers .
             * Since Java's integers are two's complement,
             * incrementing" the two's complement representation of a
             * logically negative floating-point value *decrements*
             * the signed-magnitude representation. Therefore, when
             * the integer representation of a floating-point values
             * is less than zero, the adjustment to the representation
             * is in the opposite direction than would be expected at
             * first.
             */
            if (direction > start) {// Calculate next greater value
                transducer = transducer + (transducer >= 0 ? 1:-1);
            } else  { // Calculate next lesser value
                assert direction < start;
                if (transducer > 0)
                    --transducer;
                else
                    if (transducer < 0 )
                        ++transducer;
                    /*
                     * transducer==0, the result is -MIN_VALUE
                     *
                     * The transition from zero (implicitly
                     * positive) to the smallest negative
                     * signed magnitude value must be done
                     * explicitly.
                     */
                    else
                        transducer = FloatConsts.SIGN_BIT_MASK | 1;
            }

            return Float.intBitsToFloat(transducer);
        }
    }

    /**
     * Returns the floating-point value adjacent to {@code d} in
     * the direction of positive infinity.  This method is
     * semantically equivalent to {@code nextAfter(d,
     * Double.POSITIVE_INFINITY)}; however, a {@code nextUp}
     * implementation may run faster than its equivalent
     * {@code nextAfter} call.
     *
     * <p>Special Cases:
     * <ul>
     * <li> If the argument is NaN, the result is NaN.
     *
     * <li> If the argument is positive infinity, the result is
     * positive infinity.
     *
     * <li> If the argument is zero, the result is
     * {@link Double#MIN_VALUE}
     *
     * </ul>
     *
     * @param d starting floating-point value
     * @return The adjacent floating-point value closer to positive
     * infinity.
     * @since 1.6
     */
    public static double nextUp(double d) {
        if( Double.isNaN(d) || d == Double.POSITIVE_INFINITY)
            return d;
        else {
            d += 0.0d;
            return Double.longBitsToDouble(Double.doubleToRawLongBits(d) +
                                           ((d >= 0.0d)?+1L:-1L));
        }
    }

    /**
     * Returns the floating-point value adjacent to {@code f} in
     * the direction of positive infinity.  This method is
     * semantically equivalent to {@code nextAfter(f,
     * Float.POSITIVE_INFINITY)}; however, a {@code nextUp}
     * implementation may run faster than its equivalent
     * {@code nextAfter} call.
     *
     * <p>Special Cases:
     * <ul>
     * <li> If the argument is NaN, the result is NaN.
     *
     * <li> If the argument is positive infinity, the result is
     * positive infinity.
     *
     * <li> If the argument is zero, the result is
     * {@link Float#MIN_VALUE}
     *
     * </ul>
     *
     * @param f starting floating-point value
     * @return The adjacent floating-point value closer to positive
     * infinity.
     * @since 1.6
     */
    public static float nextUp(float f) {
        if( Float.isNaN(f) || f == FloatConsts.POSITIVE_INFINITY)
            return f;
        else {
            f += 0.0f;
            return Float.intBitsToFloat(Float.floatToRawIntBits(f) +
                                        ((f >= 0.0f)?+1:-1));
        }
    }

    /**
     * Returns the floating-point value adjacent to {@code d} in
     * the direction of negative infinity.  This method is
     * semantically equivalent to {@code nextAfter(d,
     * Double.NEGATIVE_INFINITY)}; however, a
     * {@code nextDown} implementation may run faster than its
     * equivalent {@code nextAfter} call.
     *
     * <p>Special Cases:
     * <ul>
     * <li> If the argument is NaN, the result is NaN.
     *
     * <li> If the argument is negative infinity, the result is
     * negative infinity.
     *
     * <li> If the argument is zero, the result is
     * {@code -Double.MIN_VALUE}
     *
     * </ul>
     *
     * @param d  starting floating-point value
     * @return The adjacent floating-point value closer to negative
     * infinity.
     * @since 1.8
     */
    public static double nextDown(double d) {
        if (Double.isNaN(d) || d == Double.NEGATIVE_INFINITY)
            return d;
        else {
            if (d == 0.0)
                return -Double.MIN_VALUE;
            else
                return Double.longBitsToDouble(Double.doubleToRawLongBits(d) +
                                               ((d > 0.0d)?-1L:+1L));
        }
    }

    /**
     * Returns the floating-point value adjacent to {@code f} in
     * the direction of negative infinity.  This method is
     * semantically equivalent to {@code nextAfter(f,
     * Float.NEGATIVE_INFINITY)}; however, a
     * {@code nextDown} implementation may run faster than its
     * equivalent {@code nextAfter} call.
     *
     * <p>Special Cases:
     * <ul>
     * <li> If the argument is NaN, the result is NaN.
     *
     * <li> If the argument is negative infinity, the result is
     * negative infinity.
     *
     * <li> If the argument is zero, the result is
     * {@code -Float.MIN_VALUE}
     *
     * </ul>
     *
     * @param f  starting floating-point value
     * @return The adjacent floating-point value closer to negative
     * infinity.
     * @since 1.8
     */
    public static float nextDown(float f) {
        if (Float.isNaN(f) || f == Float.NEGATIVE_INFINITY)
            return f;
        else {
            if (f == 0.0f)
                return -Float.MIN_VALUE;
            else
                return Float.intBitsToFloat(Float.floatToRawIntBits(f) +
                                            ((f > 0.0f)?-1:+1));
        }
    }

    /**
     * Returns {@code d} &times;
     * 2<sup>{@code scaleFactor}</sup> rounded as if performed
     * by a single correctly rounded floating-point multiply to a
     * member of the double value set.  See the Java
     * Language Specification for a discussion of floating-point
     * value sets.  If the exponent of the result is between {@link
     * Double#MIN_EXPONENT} and {@link Double#MAX_EXPONENT}, the
     * answer is calculated exactly.  If the exponent of the result
     * would be larger than {@code Double.MAX_EXPONENT}, an
     * infinity is returned.  Note that if the result is subnormal,
     * precision may be lost; that is, when {@code scalb(x, n)}
     * is subnormal, {@code scalb(scalb(x, n), -n)} may not equal
     * <i>x</i>.  When the result is non-NaN, the result has the same
     * sign as {@code d}.
     *
     * <p>Special cases:
     * <ul>
     * <li> If the first argument is NaN, NaN is returned.
     * <li> If the first argument is infinite, then an infinity of the
     * same sign is returned.
     * <li> If the first argument is zero, then a zero of the same
     * sign is returned.
     * </ul>
     *
     * @param d number to be scaled by a power of two.
     * @param scaleFactor power of 2 used to scale {@code d}
     * @return {@code d} &times; 2<sup>{@code scaleFactor}</sup>
     * @since 1.6
     */
    public static double scalb(double d, int scaleFactor) {
        /*
         * This method does not need to be declared strictfp to
         * compute the same correct result on all platforms.  When
         * scaling up, it does not matter what order the
         * multiply-store operations are done; the result will be
         * finite or overflow regardless of the operation ordering.
         * However, to get the correct result when scaling down, a
         * particular ordering must be used.
         *
         * When scaling down, the multiply-store operations are
         * sequenced so that it is not possible for two consecutive
         * multiply-stores to return subnormal results.  If one
         * multiply-store result is subnormal, the next multiply will
         * round it away to zero.  This is done by first multiplying
         * by 2 ^ (scaleFactor % n) and then multiplying several
         * times by by 2^n as needed where n is the exponent of number
         * that is a covenient power of two.  In this way, at most one
         * real rounding error occurs.  If the double value set is
         * being used exclusively, the rounding will occur on a
         * multiply.  If the double-extended-exponent value set is
         * being used, the products will (perhaps) be exact but the
         * stores to d are guaranteed to round to the double value
         * set.
         *
         * It is _not_ a valid implementation to first multiply d by
         * 2^MIN_EXPONENT and then by 2 ^ (scaleFactor %
         * MIN_EXPONENT) since even in a strictfp program double
         * rounding on underflow could occur; e.g. if the scaleFactor
         * argument was (MIN_EXPONENT - n) and the exponent of d was a
         * little less than -(MIN_EXPONENT - n), meaning the final
         * result would be subnormal.
         *
         * Since exact reproducibility of this method can be achieved
         * without any undue performance burden, there is no
         * compelling reason to allow double rounding on underflow in
         * scalb.
         */

        // magnitude of a power of two so large that scaling a finite
        // nonzero value by it would be guaranteed to over or
        // underflow; due to rounding, scaling down takes takes an
        // additional power of two which is reflected here
        final int MAX_SCALE = DoubleConsts.MAX_EXPONENT + -DoubleConsts.MIN_EXPONENT +
                              DoubleConsts.SIGNIFICAND_WIDTH + 1;
        int exp_adjust = 0;
        int scale_increment = 0;
        double exp_delta = Double.NaN;

        // Make sure scaling factor is in a reasonable range

        if(scaleFactor < 0) {
            scaleFactor = Math.max(scaleFactor, -MAX_SCALE);
            scale_increment = -512;
            exp_delta = twoToTheDoubleScaleDown;
        }
        else {
            scaleFactor = Math.min(scaleFactor, MAX_SCALE);
            scale_increment = 512;
            exp_delta = twoToTheDoubleScaleUp;
        }

        // Calculate (scaleFactor % +/-512), 512 = 2^9, using
        // technique from "Hacker's Delight" section 10-2.
        int t = (scaleFactor >> 9-1) >>> 32 - 9;
        exp_adjust = ((scaleFactor + t) & (512 -1)) - t;

        d *= powerOfTwoD(exp_adjust);
        scaleFactor -= exp_adjust;

        while(scaleFactor != 0) {
            d *= exp_delta;
            scaleFactor -= scale_increment;
        }
        return d;
    }

    /**
     * Returns {@code f} &times;
     * 2<sup>{@code scaleFactor}</sup> rounded as if performed
     * by a single correctly rounded floating-point multiply to a
     * member of the float value set.  See the Java
     * Language Specification for a discussion of floating-point
     * value sets.  If the exponent of the result is between {@link
     * Float#MIN_EXPONENT} and {@link Float#MAX_EXPONENT}, the
     * answer is calculated exactly.  If the exponent of the result
     * would be larger than {@code Float.MAX_EXPONENT}, an
     * infinity is returned.  Note that if the result is subnormal,
     * precision may be lost; that is, when {@code scalb(x, n)}
     * is subnormal, {@code scalb(scalb(x, n), -n)} may not equal
     * <i>x</i>.  When the result is non-NaN, the result has the same
     * sign as {@code f}.
     *
     * <p>Special cases:
     * <ul>
     * <li> If the first argument is NaN, NaN is returned.
     * <li> If the first argument is infinite, then an infinity of the
     * same sign is returned.
     * <li> If the first argument is zero, then a zero of the same
     * sign is returned.
     * </ul>
     *
     * @param f number to be scaled by a power of two.
     * @param scaleFactor power of 2 used to scale {@code f}
     * @return {@code f} &times; 2<sup>{@code scaleFactor}</sup>
     * @since 1.6
     */
    public static float scalb(float f, int scaleFactor) {
        // magnitude of a power of two so large that scaling a finite
        // nonzero value by it would be guaranteed to over or
        // underflow; due to rounding, scaling down takes takes an
        // additional power of two which is reflected here
        final int MAX_SCALE = FloatConsts.MAX_EXPONENT + -FloatConsts.MIN_EXPONENT +
                              FloatConsts.SIGNIFICAND_WIDTH + 1;

        // Make sure scaling factor is in a reasonable range
        scaleFactor = Math.max(Math.min(scaleFactor, MAX_SCALE), -MAX_SCALE);

        /*
         * Since + MAX_SCALE for float fits well within the double
         * exponent range and + float -> double conversion is exact
         * the multiplication below will be exact. Therefore, the
         * rounding that occurs when the double product is cast to
         * float will be the correctly rounded float result.  Since
         * all operations other than the final multiply will be exact,
         * it is not necessary to declare this method strictfp.
         */
        return (float)((double)f*powerOfTwoD(scaleFactor));
    }

    // Constants used in scalb
    static double twoToTheDoubleScaleUp = powerOfTwoD(512);
    static double twoToTheDoubleScaleDown = powerOfTwoD(-512);

    /**
     * Returns a floating-point power of two in the normal range.
     */
    static double powerOfTwoD(int n) {
        assert(n >= DoubleConsts.MIN_EXPONENT && n <= DoubleConsts.MAX_EXPONENT);
        return Double.longBitsToDouble((((long)n + (long)DoubleConsts.EXP_BIAS) <<
                                        (DoubleConsts.SIGNIFICAND_WIDTH-1))
                                       & DoubleConsts.EXP_BIT_MASK);
    }

    /**
     * Returns a floating-point power of two in the normal range.
     */
    static float powerOfTwoF(int n) {
        assert(n >= FloatConsts.MIN_EXPONENT && n <= FloatConsts.MAX_EXPONENT);
        return Float.intBitsToFloat(((n + FloatConsts.EXP_BIAS) <<
                                     (FloatConsts.SIGNIFICAND_WIDTH-1))
                                    & FloatConsts.EXP_BIT_MASK);
    }
}
