/*
 * Copyright 1999-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.javac.parser;

import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Options;

/**
 * A factory for creating parsers.
 */
public class ParserFactory {

    /** The context key for the parser factory. */
    protected static final Context.Key<ParserFactory> parserFactoryKey = new Context.Key<ParserFactory>();

    public static ParserFactory instance(Context context) {
        ParserFactory instance = context.get(parserFactoryKey);
        if (instance == null) {
            instance = new ParserFactory(context);
        }
        return instance;
    }

    final TreeMaker F;
    final Log log;
    final Keywords keywords;
    final Source source;
    final Names names;
    final Options options;
    final Scanner.Factory scannerFactory;

    protected ParserFactory(Context context) {
        super();
        context.put(parserFactoryKey, this);
        this.F = TreeMaker.instance(context);
        this.log = Log.instance(context);
        this.names = Names.instance(context);
        this.keywords = Keywords.instance(context);
        this.source = Source.instance(context);
        this.options = Options.instance(context);
        this.scannerFactory = Scanner.Factory.instance(context);
    }

    public Parser newParser(CharSequence input, boolean keepDocComments, boolean keepEndPos, boolean keepLineMap) {
        Lexer lexer = scannerFactory.newScanner(input);
        if (keepEndPos) {
            return new EndPosParser(this, lexer, keepDocComments, keepLineMap);
        } else {
            return new JavacParser(this, lexer, keepDocComments, keepLineMap);
        }
    }
}
