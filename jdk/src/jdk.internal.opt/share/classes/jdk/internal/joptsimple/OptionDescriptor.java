/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * The MIT License
 *
 * Copyright (c) 2004-2014 Paul R. Holser, Jr.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package jdk.internal.joptsimple;

import java.util.Collection;
import java.util.List;

/**
 * Describes options that an option parser recognizes, in ways that might be useful to {@linkplain HelpFormatter
 * help screens}.
 *
 * @author <a href="mailto:pholser@alumni.rice.edu">Paul Holser</a>
 */
public interface OptionDescriptor {
    /**
     * A set of options that are mutually synonymous.
     *
     * @return synonymous options
     */
    Collection<String> options();

    /**
     * Description of this option's purpose.
     *
     * @return a description for the option
     */
    String description();

    /**
     * What values will the option take if none are specified on the command line?
     *
     * @return any default values for the option
     */
    List<?> defaultValues();

    /**
     * Is this option {@linkplain ArgumentAcceptingOptionSpec#required() required} on a command line?
     *
     * @return whether the option is required
     */
    boolean isRequired();

    /**
     * Does this option {@linkplain ArgumentAcceptingOptionSpec accept arguments}?
     *
     * @return whether the option accepts arguments
     */
    boolean acceptsArguments();

    /**
     * Does this option {@linkplain OptionSpecBuilder#withRequiredArg() require an argument}?
     *
     * @return whether the option requires an argument
     */
    boolean requiresArgument();

    /**
     * Gives a short {@linkplain ArgumentAcceptingOptionSpec#describedAs(String) description} of the option's argument.
     *
     * @return a description for the option's argument
     */
    String argumentDescription();

    /**
     * Gives an indication of the {@linkplain ArgumentAcceptingOptionSpec#ofType(Class) expected type} of the option's
     * argument.
     *
     * @return a description for the option's argument type
     */
    String argumentTypeIndicator();

    /**
     * Tells whether this object represents the non-option arguments of a command line.
     *
     * @return {@code true} if this represents non-option arguments
     */
    boolean representsNonOptions();
}
