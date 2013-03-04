/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.codegen;

import java.util.ArrayList;
import java.util.List;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.runtime.AccessorProperty;
import jdk.nashorn.internal.runtime.Property;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.arrays.ArrayIndex;

/**
 * Class that creates PropertyMap sent to script object constructors.
 */
public class MapCreator {
    /** Object structure for objects associated with this map */
    private final Class<?> structure;

    /** key set for object map */
    private final String[] keys;

    /** corresponding symbol set for object map */
    private final Symbol[] symbols;

    /**
     * Constructor
     *
     * @param structure structure to generate map for (a JO subclass)
     * @param keys      list of keys for map
     * @param symbols   list of symbols for map
     */
    MapCreator(final Class<?> structure, final List<String> keys, final List<Symbol> symbols) {
        final int size   = keys.size();

        this.structure = structure;
        this.keys      = keys.toArray(new String[size]);
        this.symbols   = symbols.toArray(new Symbol[size]);
    }

    /**
     * Constructs a property map based on a set of fields.
     *
     * @param hasArguments does the created object have an "arguments" property
     *
     * @return New map populated with accessor properties.
     */
    PropertyMap makeMap(final boolean hasArguments) {
        final List<Property> properties = new ArrayList<>();

        assert keys != null;

        for (int i = 0; i < keys.length; i++) {
            final String key    = keys[i];
            final Symbol symbol = symbols[i];

            if (symbol != null && !ArrayIndex.isIndexKey(key)) {
                properties.add(new AccessorProperty(key, getPropertyFlags(symbol, hasArguments), structure, symbol.getFieldIndex()));
            }
        }

        return PropertyMap.newMap(structure, properties);
    }

    /**
     * Compute property flags given local state of a field. May be overridden and extended,
     *
     * @param symbol       symbol to check
     * @param hasArguments does the created object have an "arguments" property
     *
     * @return flags to use for fields
     */
    protected int getPropertyFlags(final Symbol symbol, final boolean hasArguments) {
        int flags = 0;

        if (symbol.isParam()) {
            flags |= Property.IS_ALWAYS_OBJECT | Property.IS_PARAMETER;
        }

        if (hasArguments) {
            flags |= Property.IS_ALWAYS_OBJECT | Property.HAS_ARGUMENTS;
        }

        if (symbol.isScope()) {
            flags |= Property.NOT_CONFIGURABLE;
        }

        if (symbol.canBePrimitive()) {
            flags |= Property.CAN_BE_PRIMITIVE;
        }

        if (symbol.canBeUndefined()) {
            flags |= Property.CAN_BE_UNDEFINED;
        }

        return flags;
    }

}
