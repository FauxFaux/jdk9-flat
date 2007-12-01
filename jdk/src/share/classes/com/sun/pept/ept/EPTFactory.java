/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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

/** Java interface "EPTFactory.java" generated from Poseidon for UML.
 *  Poseidon for UML is developed by <A HREF="http://www.gentleware.com">Gentleware</A>.
 *  Generated with <A HREF="http://jakarta.apache.org/velocity/">velocity</A> template engine.
 */
package com.sun.pept.ept;

import com.sun.pept.encoding.Decoder;
import com.sun.pept.encoding.Encoder;
import com.sun.pept.presentation.TargetFinder;
import com.sun.pept.protocol.Interceptors;
import com.sun.pept.protocol.MessageDispatcher;
import java.util.*;

/**
 * <p>
 *
 * @author Dr. Harold Carr
 * </p>
 */
public interface EPTFactory {

  ///////////////////////////////////////
  // operations

/**
 * <p>
 * Does ...
 * </p><p>
 *
 * @return a MessageDispatcher with ...
 * </p><p>
 * @param messageInfo ...
 * </p>
 */
    public MessageDispatcher getMessageDispatcher(MessageInfo messageInfo);
/**
 * <p>
 * Does ...
 * </p><p>
 *
 * @return a Encoder with ...
 * </p><p>
 * @param messageInfo ...
 * </p>
 */
    public Encoder getEncoder(MessageInfo messageInfo);
/**
 * <p>
 * Does ...
 * </p><p>
 *
 * @return a Decoder with ...
 * </p><p>
 * @param messageInfo ...
 * </p>
 */
    public Decoder getDecoder(MessageInfo messageInfo);
/**
 * <p>
 * Does ...
 * </p><p>
 *
 * @return a Interceptors with ...
 * </p><p>
 * @param x ...
 * </p>
 */
    public Interceptors getInterceptors(MessageInfo x);
/**
 * <p>
 * Does ...
 * </p><p>
 *
 * @return a TargetFinder with ...
 * </p><p>
 * @param x ...
 * </p>
 */
    public TargetFinder getTargetFinder(MessageInfo x);

} // end EPTFactory
