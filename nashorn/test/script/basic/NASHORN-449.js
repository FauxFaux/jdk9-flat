/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * NASHORN-449 :  defineProperty on arguments object does not work element in question was deleted earlier
 * 
 * @test
 * @run
 */


function func() {
    delete arguments[0];
    return arguments;
}

var a = func(334, 55, 33);
var getterCalled = false;
Object.defineProperty(a, "0", {
  get: function() { getterCalled = true; return 22; }
});

if (a[0] === undefined) {
    fail("a[0] is undefined");
}

if (! getterCalled) {
    fail("getter not called");
}

if (a[0] !== 22) {
    fail("a[0] !== 22");
}
