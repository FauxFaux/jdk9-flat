/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * This script file is executed by script engine at the construction
 * of the engine. The functions here assume global variables "context"
 * of type javax.script.ScriptContext and "engine" of the type
 * jdk.nashorn.api.scripting.NashornScriptEngine.
 *
 **/

Object.defineProperty(this, "__noSuchProperty__", {
    configurable: true,
    enumerable: false,
    writable: true,
    value: function (name) {
        'use strict';
        return engine.__noSuchProperty__(this, context, name);
    }
});

Object.defineProperty(this, "__noSuchMethod__", {
    configurable: true,
    enumerable: false,
    writable: true,
    value: function (name, args) {
        'use strict';
        return engine.__noSuchMethod__(this, context, name, args);
    }
});

function print(str) {
    var writer = context.getWriter();
    if (! (writer instanceof java.io.PrintWriter)) {
        writer = new java.io.PrintWriter(writer);
    }
    writer.println(String(str));
}
