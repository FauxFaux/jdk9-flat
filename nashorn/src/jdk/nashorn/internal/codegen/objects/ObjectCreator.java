/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.codegen.objects;

import java.util.List;
import jdk.nashorn.internal.codegen.CodeGenerator;
import jdk.nashorn.internal.codegen.CompileUnit;
import jdk.nashorn.internal.codegen.Compiler;
import jdk.nashorn.internal.codegen.MethodEmitter;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.PropertyMap;

/**
 * Base class for object creation code generation.
 */
public abstract class ObjectCreator {

    /** Compile unit for this ObjectCreator, see CompileUnit */
    protected final CompileUnit   compileUnit;

    /** List of keys to initiate in this ObjectCreator */
    protected final List<String>  keys;

    /** List of symbols to initiate in this ObjectCreator */
    protected final List<Symbol>  symbols;

    /** Code generator */
    protected final CodeGenerator codegen;

    private   final boolean       isScope;
    private   final boolean       isVarArg;
    private         int           fieldCount;
    private         int           paramCount;
    private         String        fieldObjectClassName;
    private         Class<?>      fieldObjectClass;
    private         PropertyMap   propertyMap;

    /**
     * Constructor
     *
     * @param codegen  the code generator
     * @param keys     the keys
     * @param symbols  the symbols corresponding to keys, same index
     * @param isScope  is this object scope
     * @param isVarArg is this object var arg
     */
    protected ObjectCreator(final CodeGenerator codegen, final List<String> keys, final List<Symbol> symbols, final boolean isScope, final boolean isVarArg) {
        this.codegen       = codegen;
        this.compileUnit   = codegen.getCurrentCompileUnit();
        this.keys          = keys;
        this.symbols       = symbols;
        this.isScope       = isScope;
        this.isVarArg      = isVarArg;

        countFields();
        findClass();
    }

    /**
     * Tally the number of fields and parameters.
     */
    private void countFields() {
        for (final Symbol symbol : this.symbols) {
            if (symbol != null) {
                if (isVarArg() && symbol.isParam()) {
                    symbol.setFieldIndex(paramCount++);
                } else {
                    symbol.setFieldIndex(fieldCount++);
                }
            }
        }
    }

    /**
     * Locate (or indirectly create) the object container class.
     */
    private void findClass() {
        fieldObjectClassName = isScope() ?
            ObjectClassGenerator.getClassName(fieldCount, paramCount) :
            ObjectClassGenerator.getClassName(fieldCount);

        try {
            this.fieldObjectClass = Context.forStructureClass(Compiler.binaryName(fieldObjectClassName));
        } catch (final ClassNotFoundException e) {
            throw new AssertionError("Nashorn has encountered an internal error.  Structure can not be created.");
        }
    }

    /**
     * Generate code for making the object.
     * @param method Script method.
     */
    public abstract void makeObject(final MethodEmitter method);

    /**
     * Create a new MapCreator
     * @param clazz type of MapCreator
     * @return map creator instantiated by type
     */
    protected MapCreator newMapCreator(final Class<?> clazz) {
        return new MapCreator(clazz, keys, symbols);
    }

    /**
     * Construct the property map appropriate for the object.
     */
    protected void makeMap() {
        if (keys.isEmpty()) { //empty map
            propertyMap = PropertyMap.newMap(fieldObjectClass);
            return;
        }

        propertyMap = newMapCreator(fieldObjectClass).makeMap(isVarArg());
    }

    /**
     * Emit the correct map for the object.
     * @param method method emitter
     * @return the method emitter
     */
    protected MethodEmitter loadMap(final MethodEmitter method) {
        codegen.loadConstant(propertyMap);
        return method;
    }

    /**
     * Get the class name for the object class,
     * e.g. {@code com.nashorn.oracle.scripts.JO$2P0}
     *
     * @return script class name
     */
    public String getClassName() {
        return fieldObjectClassName;
    }

    /**
     * Is this a scope object
     * @return true if scope
     */
    protected boolean isScope() {
        return isScope;
    }

    /**
     * Is this a vararg object
     * @return true if vararg
     */
    protected boolean isVarArg() {
        return isVarArg;
    }
}
