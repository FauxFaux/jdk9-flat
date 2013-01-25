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

package jdk.nashorn.internal.runtime;

import static jdk.nashorn.internal.codegen.CompilerConstants.staticCall;
import static jdk.nashorn.internal.codegen.CompilerConstants.virtualCall;
import static jdk.nashorn.internal.codegen.CompilerConstants.virtualCallNoLookup;
import static jdk.nashorn.internal.runtime.ECMAErrors.referenceError;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.PropertyDescriptor.CONFIGURABLE;
import static jdk.nashorn.internal.runtime.PropertyDescriptor.ENUMERABLE;
import static jdk.nashorn.internal.runtime.PropertyDescriptor.GET;
import static jdk.nashorn.internal.runtime.PropertyDescriptor.SET;
import static jdk.nashorn.internal.runtime.PropertyDescriptor.VALUE;
import static jdk.nashorn.internal.runtime.PropertyDescriptor.WRITABLE;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;
import static jdk.nashorn.internal.runtime.arrays.ArrayIndex.getArrayIndexNoThrow;
import static jdk.nashorn.internal.runtime.arrays.ArrayIndex.isValidArrayIndex;
import static jdk.nashorn.internal.runtime.linker.Lookup.MH;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jdk.nashorn.internal.codegen.CompilerConstants.Call;
import jdk.nashorn.internal.codegen.objects.ObjectClassGenerator;
import jdk.nashorn.internal.objects.AccessorPropertyDescriptor;
import jdk.nashorn.internal.objects.DataPropertyDescriptor;
import jdk.nashorn.internal.runtime.arrays.ArrayData;
import jdk.nashorn.internal.runtime.linker.Bootstrap;
import jdk.nashorn.internal.runtime.linker.Lookup;
import jdk.nashorn.internal.runtime.linker.MethodHandleFactory;
import jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor;
import jdk.nashorn.internal.runtime.linker.NashornGuardedInvocation;
import jdk.nashorn.internal.runtime.linker.NashornGuards;
import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.LinkRequest;
import org.dynalang.dynalink.support.CallSiteDescriptorFactory;

/**
 * Base class for generic JavaScript objects.
 * <p>
 * Notes:
 * <ul>
 * <li>The map is used to identify properties in the object.</li>
 * <li>If the map is modified then it must be cloned and replaced.  This notifies
 *     any code that made assumptions about the object that things have changed.
 *     Ex. CallSites that have been validated must check to see if the map has
 *     changed (or a map from a different object type) and hence relink the method
 *     to call.</li>
 * <li>Modifications of the map include adding/deleting attributes or changing a
 *     function field value.</li>
 * </ul>
 */


public abstract class ScriptObject extends PropertyListenerManager implements PropertyAccess {

    /** Search fall back routine name for "no such method" */
    static final String NO_SUCH_METHOD_NAME   = "__noSuchMethod__";

    /** Search fall back routine name for "no such property" */
    static final String NO_SUCH_PROPERTY_NAME = "__noSuchProperty__";

    /** Per ScriptObject flag - is this a scope object? */
    public static final int IS_SCOPE       = 0b0000_0001;

    /** Per ScriptObject flag - is this an array object? */
    public static final int IS_ARRAY       = 0b0000_0010;

    /** Per ScriptObject flag - is this an arguments object? */
    public static final int IS_ARGUMENTS   = 0b0000_0100;

    /** Spill growth rate - by how many elements does {@link ScriptObject#spill} when full */
    public static final int SPILL_RATE = 8;

    /** Map to property information and accessor functions. Ordered by insertion. */
    private PropertyMap map;

    /** Object flags. */
    private int flags;

    /** Area for properties added to object after instantiation, see {@link SpillProperty} */
    public Object[] spill;

    /** Local embed area position 0 - used for {@link SpillProperty} before {@link ScriptObject#spill} */
    public Object embed0;

    /** Local embed area position 1 - used for {@link SpillProperty} before {@link ScriptObject#spill} */
    public Object embed1;

    /** Local embed area position 2 - used for {@link SpillProperty} before {@link ScriptObject#spill} */
    public Object embed2;

    /** Local embed area position 3 - used for {@link SpillProperty} before {@link ScriptObject#spill} */
    public Object embed3;

    /** Indexed array data. */
    private ArrayData arrayData;

    static final MethodHandle SETEMBED           = findOwnMH("setEmbed",         void.class, CallSiteDescriptor.class, PropertyMap.class, PropertyMap.class, MethodHandle.class, int.class, Object.class, Object.class);
    static final MethodHandle SETSPILL           = findOwnMH("setSpill",         void.class, CallSiteDescriptor.class, PropertyMap.class, PropertyMap.class, int.class, Object.class, Object.class);
    static final MethodHandle SETSPILLWITHNEW    = findOwnMH("setSpillWithNew",  void.class, CallSiteDescriptor.class, PropertyMap.class, PropertyMap.class, int.class, Object.class, Object.class);
    static final MethodHandle SETSPILLWITHGROW   = findOwnMH("setSpillWithGrow", void.class, CallSiteDescriptor.class, PropertyMap.class, PropertyMap.class, int.class, int.class, Object.class, Object.class);

    private static final MethodHandle TRUNCATINGFILTER   = findOwnMH("truncatingFilter", Object[].class, int.class, Object[].class);
    private static final MethodHandle KNOWNFUNCPROPGUARD = findOwnMH("knownFunctionPropertyGuard", boolean.class, Object.class, PropertyMap.class, MethodHandle.class, Object.class, ScriptFunction.class);

    /** Method handle for getting a function argument at a given index. Used from MapCreator */
    public static final Call GET_ARGUMENT       = virtualCall(ScriptObject.class, "getArgument", Object.class, int.class);

    /** Method handle for setting a function argument at a given index. Used from MapCreator */
    public static final Call SET_ARGUMENT       = virtualCall(ScriptObject.class, "setArgument", void.class, int.class, Object.class);

    /** Method handle for getting the proto of a ScriptObject - used by {@link jdk.nashorn.internal.codegen.CodeGenerator} */
    public static final Call GET_PROTO          = virtualCallNoLookup(ScriptObject.class, "getProto", ScriptObject.class);

    /** Method handle for setting the proto of a ScriptObject - used by {@link jdk.nashorn.internal.codegen.CodeGenerator} */
    public static final Call SET_PROTO          = virtualCallNoLookup(ScriptObject.class, "setProto", void.class, ScriptObject.class);

    /** Method handle for setting the user accessors of a ScriptObject - used by {@link jdk.nashorn.internal.codegen.CodeGenerator} */
    public static final Call SET_USER_ACCESSORS = virtualCall(ScriptObject.class, "setUserAccessors", void.class, String.class, ScriptFunction.class, ScriptFunction.class);

    /** Method handle for getter for {@link UserAccessorProperty}, given a slot */
    static final Call USER_ACCESSOR_GETTER = staticCall(MethodHandles.lookup(), ScriptObject.class, "userAccessorGetter", Object.class, ScriptObject.class, int.class, Object.class);

    /** Method handle for setter for {@link UserAccessorProperty}, given a slot */
    static final Call USER_ACCESSOR_SETTER = staticCall(MethodHandles.lookup(), ScriptObject.class, "userAccessorSetter", void.class, ScriptObject.class, int.class, String.class, Object.class, Object.class);

    private static final MethodHandle INVOKE_UA_GETTER = Bootstrap.createDynamicInvoker("dyn:call", Object.class,
            Object.class, Object.class);
    private static final MethodHandle INVOKE_UA_SETTER = Bootstrap.createDynamicInvoker("dyn:call", void.class,
            Object.class, Object.class, Object.class);

    /**
     * Constructor
     */
    public ScriptObject() {
        this(null);
    }

    /**
    * Constructor
    *
    * @param map {@link PropertyMap} used to create the initial object
    */
    public ScriptObject(final PropertyMap map) {
        if (Context.DEBUG) {
            ScriptObject.count++;
        }

        this.arrayData = ArrayData.EMPTY_ARRAY;

        if (map == null) {
            this.setMap(PropertyMap.newMap(getClass()));
            return;
        }

        this.setMap(map);
    }

    /**
     * Copy all properties from the source object with their receiver bound to the source.
     * This function was known as mergeMap
     *
     * @param source The source object to copy from.
     */
    public void addBoundProperties(final ScriptObject source) {
        PropertyMap newMap = this.getMap();

        for (final Property property : source.getMap().getProperties()) {
            final String key = property.getKey();

            if (newMap.findProperty(key) == null) {
                if (property instanceof UserAccessorProperty) {
                    final UserAccessorProperty prop = this.newUserAccessors(key, property.getFlags(), property.getGetterFunction(source), property.getSetterFunction(source));
                    newMap = newMap.addProperty(prop);
                } else {
                    newMap = newMap.newPropertyBind((AccessorProperty)property, source);
                }
            }
        }

        this.setMap(newMap);
    }

    /**
     * Bind the method handle to the specified receiver, while preserving its original type (it will just ignore the
     * first argument in lieu of the bound argument).
     * @param methodHandle Method handle to bind to.
     * @param receiver     Object to bind.
     * @return Bound method handle.
     */
    static MethodHandle bindTo(final MethodHandle methodHandle, final Object receiver) {
        return MH.dropArguments(MH.bindTo(methodHandle, receiver), 0, methodHandle.type().parameterType(0));
    }

    /**
     * Return a property iterator.
     * @return Property iterator.
     */
    public Iterator<String> propertyIterator() {
        return new KeyIterator(this);
    }

    /**
     * Return a property value iterator.
     * @return Property value iterator.
     */
    public Iterator<Object> valueIterator() {
        return new ValueIterator(this);
    }

    /**
     * ECMA 8.10.1 IsAccessorDescriptor ( Desc )
     * @return true if this has a {@link AccessorPropertyDescriptor} with a getter or a setter
     */
    public final boolean isAccessorDescriptor() {
        return has(GET) || has(SET);
    }

    /**
     * ECMA 8.10.2 IsDataDescriptor ( Desc )
     * @return true if this has a {@link DataPropertyDescriptor}, i.e. the object has a property value and is writable
     */
    public final boolean isDataDescriptor() {
        return has(VALUE) || has(WRITABLE);
    }

    /**
     * ECMA 8.10.3 IsGenericDescriptor ( Desc )
     * @return true if this has a descriptor describing an {@link AccessorPropertyDescriptor} or {@link DataPropertyDescriptor}
     */
    public final boolean isGenericDescriptor() {
        return isAccessorDescriptor() || isDataDescriptor();
    }

    /**
      * ECMA 8.10.5 ToPropertyDescriptor ( Obj )
      *
      * @return property descriptor
      */
    public final PropertyDescriptor toPropertyDescriptor() {
        final GlobalObject global = (GlobalObject) Context.getGlobalTrusted();

        final PropertyDescriptor desc;
        if (isDataDescriptor()) {
            if (has(SET) || has(GET)) {
                typeError((ScriptObject)global, "inconsistent.property.descriptor");
            }

            desc = global.newDataDescriptor(UNDEFINED, false, false, false);
        } else if (isAccessorDescriptor()) {
            if (has(VALUE) || has(WRITABLE)) {
                typeError((ScriptObject)global, "inconsistent.property.descriptor");
            }

            desc = global.newAccessorDescriptor(UNDEFINED, UNDEFINED, false, false);
        } else {
            desc = global.newGenericDescriptor(false, false);
        }

        return desc.fillFrom(this);
    }

    /**
     * ECMA 8.10.5 ToPropertyDescriptor ( Obj )
     *
     * @param global  global scope object
     * @param obj object to create property descriptor from
     *
     * @return property descriptor
     */
    public static PropertyDescriptor toPropertyDescriptor(final ScriptObject global, final Object obj) {
        if (obj instanceof ScriptObject) {
            return ((ScriptObject)obj).toPropertyDescriptor();
        }

        typeError(global, "not.an.object", ScriptRuntime.safeToString(obj));
        return null;
    }

    /**
     * ECMA 8.12.1 [[GetOwnProperty]] (P)
     *
     * @param key property key
     *
     * @return Returns the Property Descriptor of the named own property of this
     * object, or undefined if absent.
     */
    public Object getOwnPropertyDescriptor(final String key) {
        final Property property = getMap().findProperty(key);

        final GlobalObject global = (GlobalObject)Context.getGlobalTrusted();

        if (property != null) {
            final ScriptFunction get   = property.getGetterFunction(this);
            final ScriptFunction set   = property.getSetterFunction(this);

            final boolean configurable = property.isConfigurable();
            final boolean enumerable   = property.isEnumerable();
            final boolean writable     = property.isWritable();

            if (property instanceof UserAccessorProperty) {
                return global.newAccessorDescriptor(
                    (get != null) ?
                        get :
                        UNDEFINED,
                    (set != null) ?
                        set :
                        UNDEFINED,
                    configurable,
                    enumerable);
            }

            return global.newDataDescriptor(getWithProperty(property), configurable, enumerable, writable);
        }

        final int index = getArrayIndexNoThrow(key);
        final ArrayData array = getArray();

        if (array.has(index)) {
            return array.getDescriptor(global, index);
        }

        return UNDEFINED;
    }

    /**
     * ECMA 8.12.2 [[GetProperty]] (P)
     *
     * @param key property key
     *
     * @return Returns the fully populated Property Descriptor of the named property
     * of this object, or undefined if absent.
     */
    public Object getPropertyDescriptor(final String key) {
        final Object res = getOwnPropertyDescriptor(key);

        if (res != UNDEFINED) {
            return res;
        } else if (getProto() != null) {
            return getProto().getOwnPropertyDescriptor(key);
        } else {
            return UNDEFINED;
        }
    }

    /**
     * ECMA 8.12.9 [[DefineOwnProperty]] (P, Desc, Throw)
     *
     * @param key the property key
     * @param propertyDesc the property descriptor
     * @param reject is the property extensible - true means new definitions are rejected
     *
     * @return true if property was successfully defined
     */
    public boolean defineOwnProperty(final String key, final Object propertyDesc, final boolean reject) {
        final ScriptObject       global  = Context.getGlobalTrusted();
        final PropertyDescriptor desc    = toPropertyDescriptor(global, propertyDesc);
        final Object             current = getOwnPropertyDescriptor(key);
        final String             name    = JSType.toString(key);

        if (current == UNDEFINED) {
            if (isExtensible()) {
                // add a new own property
                addOwnProperty(key, desc);
                return true;
            }
            // new property added to non-extensible object
            if (reject) {
                typeError(global, "object.non.extensible", name, ScriptRuntime.safeToString(this));
            }
            return false;
        }
        // modifying an existing property
        final PropertyDescriptor currentDesc = (PropertyDescriptor) current;
        final PropertyDescriptor newDesc     = desc;

        if (newDesc.type() == PropertyDescriptor.GENERIC &&
            ! newDesc.has(CONFIGURABLE) && ! newDesc.has(ENUMERABLE)) {
            // every descriptor field is absent
            return true;
        }

        if (currentDesc.equals(newDesc)) {
            // every descriptor field of the new is same as the current
            return true;
        }

        if (! currentDesc.isConfigurable()) {
            if (newDesc.has(CONFIGURABLE) && newDesc.isConfigurable()) {
                // not configurable can not be made configurable
                if (reject) {
                    typeError(global, "cant.redefine.property", name, ScriptRuntime.safeToString(this));
                }
                return false;
            }

            if (newDesc.has(ENUMERABLE) &&
                currentDesc.isEnumerable() != newDesc.isEnumerable()) {
                // cannot make non-enumerable as enumerable or vice-versa
                if (reject) {
                    typeError(global, "cant.redefine.property", name, ScriptRuntime.safeToString(this));
                }
                return false;
            }
        }

        int propFlags = Property.mergeFlags(currentDesc, newDesc);
        Property property = getMap().findProperty(key);

        if (currentDesc.type() == PropertyDescriptor.DATA &&
            (newDesc.type() == PropertyDescriptor.DATA || newDesc.type() == PropertyDescriptor.GENERIC)) {
            if (! currentDesc.isConfigurable() && ! currentDesc.isWritable()) {
                if (newDesc.has(WRITABLE) && newDesc.isWritable() ||
                    newDesc.has(VALUE) && ! ScriptRuntime.sameValue(currentDesc.getValue(), newDesc.getValue())) {
                    if (reject) {
                        typeError(global, "cant.redefine.property", name, ScriptRuntime.safeToString(this));
                    }
                    return false;
                }
            }

            final boolean newValue = newDesc.has(VALUE);
            final Object value     = newValue? newDesc.getValue() : currentDesc.getValue();
            if (newValue && property != null) {
                // Temporarily clear flags.
                property = modifyOwnProperty(property, 0);
                set(key, value, getContext()._strict);
            }

            if (property == null) {
                // promoting an arrayData value to actual property
                addOwnProperty(key, propFlags, value);
                removeArraySlot(key);
            } else {
                // Now set the new flags
                modifyOwnProperty(property, propFlags);
            }
        } else if (currentDesc.type() == PropertyDescriptor.ACCESSOR &&
                   (newDesc.type() == PropertyDescriptor.ACCESSOR ||
                    newDesc.type() == PropertyDescriptor.GENERIC)) {
            if (! currentDesc.isConfigurable()) {
                if (newDesc.has(PropertyDescriptor.GET) && ! ScriptRuntime.sameValue(currentDesc.getGetter(), newDesc.getGetter()) ||
                    newDesc.has(PropertyDescriptor.SET) && ! ScriptRuntime.sameValue(currentDesc.getSetter(), newDesc.getSetter())) {
                    if (reject) {
                        typeError(global, "cant.redefine.property", name, ScriptRuntime.safeToString(this));
                    }
                    return false;
                }
            }

            // New set the new features.
            modifyOwnProperty(property, propFlags,
                                      newDesc.has(GET) ? newDesc.getGetter() : currentDesc.getGetter(),
                                      newDesc.has(SET) ? newDesc.getSetter() : currentDesc.getSetter());
        } else {
            // changing descriptor type
            if (! currentDesc.isConfigurable()) {
                // not configurable can not be made configurable
                if (reject) {
                    typeError(global, "cant.redefine.property", name, ScriptRuntime.safeToString(this));
                }
                return false;
            }

            propFlags = 0;

            // Preserve only configurable and enumerable from current desc
            // if those are not overridden in the new property descriptor.
            boolean value = newDesc.has(CONFIGURABLE)? newDesc.isConfigurable() : currentDesc.isConfigurable();
            if (!value) {
                propFlags |= Property.NOT_CONFIGURABLE;
            }
            value = newDesc.has(ENUMERABLE)? newDesc.isEnumerable() : currentDesc.isEnumerable();
            if (!value) {
                propFlags |= Property.NOT_ENUMERABLE;
            }

            final int type = newDesc.type();
            if (type == PropertyDescriptor.DATA) {
                // get writable from the new descriptor
                value = newDesc.has(WRITABLE) && newDesc.isWritable();
                if (! value) {
                    propFlags |= Property.NOT_WRITABLE;
                }

                // delete the old property
                deleteOwnProperty(property);
                // add new data property
                addOwnProperty(key, propFlags, newDesc.getValue());
            } else if (type == PropertyDescriptor.ACCESSOR) {
                if (property == null) {
                    addOwnProperty(key, propFlags,
                                     newDesc.has(GET) ? newDesc.getGetter() : null,
                                     newDesc.has(SET) ? newDesc.getSetter() : null);
                } else {
                    // Modify old property with the new features.
                    modifyOwnProperty(property, propFlags,
                                        newDesc.has(GET) ? newDesc.getGetter() : null,
                                        newDesc.has(SET) ? newDesc.getSetter() : null);
                }
            }
        }

        checkIntegerKey(key);

        return true;
    }

    /**
     * Spec. mentions use of [[DefineOwnProperty]] for indexed properties in
     * certain places (eg. Array.prototype.map, filter). We can not use ScriptObject.set
     * method in such cases. This is because set method uses inherited setters (if any)
     * from any object in proto chain such as Array.prototype, Object.prototype.
     * This method directly sets a particular element value in the current object.
     *
     * @param index index key for property
     * @param value value to define
     */
    protected final void defineOwnProperty(final int index, final Object value) {
        if (index >= getArray().length()) {
            // make array big enough to hold..
            setArray(getArray().ensure(index));
        }
        setArray(getArray().set(index, value, false));
    }

    private void checkIntegerKey(final String key) {
        final int index = getArrayIndexNoThrow(key);

        if (isValidArrayIndex(index)) {
            final ArrayData data = getArray();

            if (data.has(index)) {
                setArray(data.delete(index));
            }
        }
    }

    private void removeArraySlot(final String key) {
        final int index = getArrayIndexNoThrow(key);
        final ArrayData array = getArray();

        if (array.has(index)) {
            setArray(array.delete(index));
        }
    }

    /**
      * Add a new property to the object.
      *
      * @param key          property key
      * @param propertyDesc property descriptor for property
      */
    public final void addOwnProperty(final String key, final PropertyDescriptor propertyDesc) {
        // Already checked that there is no own property with that key.
        PropertyDescriptor pdesc = propertyDesc;

        final int propFlags = Property.toFlags(pdesc);

        if (pdesc.type() == PropertyDescriptor.GENERIC) {
            final GlobalObject global = (GlobalObject) Context.getGlobalTrusted();
            final PropertyDescriptor dDesc = global.newDataDescriptor(UNDEFINED, false, false, false);

            dDesc.fillFrom((ScriptObject)pdesc);
            pdesc = dDesc;
        }

        final int type = pdesc.type();
        if (type == PropertyDescriptor.DATA) {
            addOwnProperty(key, propFlags, pdesc.getValue());
        } else if (type == PropertyDescriptor.ACCESSOR) {
            addOwnProperty(key, propFlags,
                    pdesc.has(GET) ? pdesc.getGetter() : null,
                    pdesc.has(SET) ? pdesc.getSetter() : null);
        }

        checkIntegerKey(key);
    }

    /**
     * Low level property API (not using property descriptors)
     * <p>
     * Find a property in the prototype hierarchy. Note: this is final and not
     * a good idea to override. If you have to, use
     * {jdk.nashorn.internal.objects.NativeArray{@link #getProperty(String)} or
     * {jdk.nashorn.internal.objects.NativeArray{@link #getPropertyDescriptor(String)} as the
     * overriding way to find array properties
     *
     * @see jdk.nashorn.internal.objects.NativeArray
     *
     * @param key  Property key.
     * @param deep Whether the search should look up proto chain.
     *
     * @return FindPropertyData or null if not found.
     */
    public final FindProperty findProperty(final String key, final boolean deep) {
        return findProperty(key, deep, false);
    }

    /**
     * Low level property API (not using property descriptors)
     * <p>
     * Find a property in the prototype hierarchy. Note: this is final and not
     * a good idea to override. If you have to, use
     * {jdk.nashorn.internal.objects.NativeArray{@link #getProperty(String)} or
     * {jdk.nashorn.internal.objects.NativeArray{@link #getPropertyDescriptor(String)} as the
     * overriding way to find array properties
     *
     * @see jdk.nashorn.internal.objects.NativeArray
     *
     * @param key  Property key.
     * @param deep Whether the search should look up proto chain.
     * @param stopOnNonScope should a deep search stop on the first non-scope object?
     *
     * @return FindPropertyData or null if not found.
     */
    public final FindProperty findProperty(final String key, final boolean deep, final boolean stopOnNonScope) {
        int depth = 0;

        for (ScriptObject self = this; self != null; self = self.getProto()) {
            // if doing deep search, stop search on the first non-scope object if asked to do so
            if (stopOnNonScope && depth != 0 && !self.isScope()) {
                break;
            }
            final PropertyMap selfMap  = self.getMap();
            final Property    property = selfMap.findProperty(key);

            if (property != null) {
                return new FindProperty(this, self, selfMap, property, depth);
            } else if (!deep) {
                return null;
            }

            depth++;
        }

        return null;
    }

    /**
     * Add a new property to the object.
     * <p>
     * This a more "low level" way that doesn't involve {@link PropertyDescriptor}s
     *
     * @param key             Property key.
     * @param propertyFlags   Property flags.
     * @param getter          Property getter, or null if not defined
     * @param setter          Property setter, or null if not defined
     *
     * @return New property.
     */
    public final Property addOwnProperty(final String key, final int propertyFlags, final ScriptFunction getter, final ScriptFunction setter) {
        return addOwnProperty(newUserAccessors(key, propertyFlags, getter, setter));
    }

    /**
     * Add a new property to the object.
     * <p>
     * This a more "low level" way that doesn't involve {@link PropertyDescriptor}s
     *
     * @param key             Property key.
     * @param propertyFlags   Property flags.
     * @param value           Value of property
     *
     * @return New property.
     */
    public final Property addOwnProperty(final String key, final int propertyFlags, final Object value) {
        final MethodHandle setter = addSpill(key, propertyFlags);

        try {
            setter.invokeExact((Object)this, value);
        } catch (final Error|RuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }

        return getMap().findProperty(key);
    }

    /**
     * Add a new property to the object.
     * <p>
     * This a more "low level" way that doesn't involve {@link PropertyDescriptor}s
     *
     * @param newProperty property to add
     *
     * @return New property.
     */
    public final Property addOwnProperty(final Property newProperty) {
        PropertyMap oldMap = getMap();

        while (true) {
            final PropertyMap newMap = oldMap.addProperty(newProperty);

            if (!compareAndSetMap(oldMap, newMap)) {
                oldMap = getMap();
                final Property oldProperty = oldMap.findProperty(newProperty.getKey());

                if (oldProperty != null) {
                    return oldProperty;
                }
            } else {
                return newProperty;
            }
        }
    }

    private void erasePropertyValue(final Property property) {
        // Erase the property field value with undefined. If the property is defined
        // by user-defined accessors, we don't want to call the setter!!
        if (!(property instanceof UserAccessorProperty)) {
            try {
                // make the property value to be undefined
                //TODO specproperties
                property.getSetter(Object.class, getMap()).invokeExact((Object)this, (Object)UNDEFINED);
            } catch (final RuntimeException | Error e) {
                throw e;
            } catch (final Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }

    /**
     * Delete a property from the object.
     *
     * @param property Property to delete.
     *
     * @return true if deleted.
     */
    public final boolean deleteOwnProperty(final Property property) {
        erasePropertyValue(property);
        PropertyMap oldMap = getMap();

        while (true) {
            final PropertyMap newMap = oldMap.deleteProperty(property);

            if (newMap == null) {
                return false;
            }

            if (!compareAndSetMap(oldMap, newMap)) {
                oldMap = getMap();
            } else {
                // delete getter and setter function references so that we don't leak
                if (property instanceof UserAccessorProperty) {
                    final UserAccessorProperty uc = (UserAccessorProperty) property;
                    setEmbedOrSpill(uc.getGetterSlot(), null);
                    setEmbedOrSpill(uc.getSetterSlot(), null);
                }
                return true;
            }
        }
    }

    /**
     * Modify a property in the object
     *
     * @param oldProperty    property to modify
     * @param propertyFlags  new property flags
     * @param getter         getter for {@link UserAccessorProperty}, null if not present or N/A
     * @param setter         setter for {@link UserAccessorProperty}, null if not present or N/A
     *
     * @return new property
     */
    public final Property modifyOwnProperty(final Property oldProperty, final int propertyFlags, final ScriptFunction getter, final ScriptFunction setter) {
        Property newProperty;
        if (oldProperty instanceof UserAccessorProperty) {
            // re-use the slots of the old user accessor property.
            final UserAccessorProperty uc = (UserAccessorProperty) oldProperty;

            int getterSlot = uc.getGetterSlot();
            // clear the old getter and set the new getter
            setEmbedOrSpill(getterSlot, getter);
            // if getter function is null, flag the slot to be negative (less by 1)
            if (getter == null) {
                getterSlot = -getterSlot - 1;
            }

            int setterSlot = uc.getSetterSlot();
            // clear the old setter and set the new setter
            setEmbedOrSpill(setterSlot, setter);
            // if setter function is null, flag the slot to be negative (less by 1)
            if (setter == null) {
                setterSlot = -setterSlot - 1;
            }

            newProperty = new UserAccessorProperty(oldProperty.getKey(), propertyFlags, getterSlot, setterSlot);
            // if just flipping getter and setter with new functions, no need to change property or map
            if (oldProperty.equals(newProperty)) {
                return oldProperty;
            }
        } else {
            // erase old property value and create new user accessor property
            erasePropertyValue(oldProperty);
            newProperty = newUserAccessors(oldProperty.getKey(), propertyFlags, getter, setter);
        }

        notifyPropertyModified(this, oldProperty, newProperty);

        return modifyOwnProperty(oldProperty, newProperty);
    }

    /**
      * Modify a property in the object
      *
      * @param oldProperty    property to modify
      * @param propertyFlags  new property flags
      *
      * @return new property
      */
    public final Property modifyOwnProperty(final Property oldProperty, final int propertyFlags) {
        return modifyOwnProperty(oldProperty, oldProperty.setFlags(propertyFlags));
    }

    /**
     * Modify a property in the object, replacing a property with a new one
     *
     * @param oldProperty   property to replace
     * @param newProperty   property to replace it with
     *
     * @return new property
     */
    private Property modifyOwnProperty(final Property oldProperty, final Property newProperty) {
        assert newProperty.getKey().equals(oldProperty.getKey()) : "replacing property with different key";

        PropertyMap oldMap = getMap();

        while (true) {
            final PropertyMap newMap = oldMap.replaceProperty(oldProperty, newProperty);

            if (!compareAndSetMap(oldMap, newMap)) {
                oldMap = getMap();
                final Property oldPropertyLookup = oldMap.findProperty(oldProperty.getKey());

                if (oldPropertyLookup != null && oldPropertyLookup.equals(newProperty)) {
                    return oldPropertyLookup;
                }
            } else {
                return newProperty;
            }
        }
    }

    /**
     * Update getter and setter in an object literal.
     *
     * @param key    Property key.
     * @param getter {@link UserAccessorProperty} defined getter, or null if none
     * @param setter {@link UserAccessorProperty} defined setter, or null if none
     */
    public final void setUserAccessors(final String key, final ScriptFunction getter, final ScriptFunction setter) {
        final Property oldProperty = getMap().findProperty(key);
        if (oldProperty != null) {
            final UserAccessorProperty newProperty = newUserAccessors(oldProperty.getKey(), oldProperty.getFlags(), getter, setter);
            modifyOwnProperty(oldProperty, newProperty);
        } else {
            final UserAccessorProperty newProperty = newUserAccessors(key, 0, getter, setter);
            addOwnProperty(newProperty);
        }
    }

    private static int getIntValue(final FindProperty find) {
        final MethodHandle getter = find.getGetter(int.class);
        if (getter != null) {
            try {
                return (int)getter.invokeExact((Object)find.getOwner());
            } catch (final Error|RuntimeException e) {
                throw e;
            } catch (final Throwable e) {
                throw new RuntimeException(e);
            }
        }

        return ObjectClassGenerator.UNDEFINED_INT;
    }

    private static long getLongValue(final FindProperty find) {
        final MethodHandle getter = find.getGetter(long.class);
        if (getter != null) {
            try {
                return (long)getter.invokeExact((Object)find.getOwner());
            } catch (final Error|RuntimeException e) {
                throw e;
            } catch (final Throwable e) {
                throw new RuntimeException(e);
            }
        }

        return ObjectClassGenerator.UNDEFINED_LONG;
    }

    private static double getDoubleValue(final FindProperty find) {
        final MethodHandle getter = find.getGetter(double.class);
        if (getter != null) {
            try {
                return (double)getter.invokeExact((Object)find.getOwner());
            } catch (final Error|RuntimeException e) {
                throw e;
            } catch (final Throwable e) {
                throw new RuntimeException(e);
            }
        }

        return ObjectClassGenerator.UNDEFINED_DOUBLE;
    }

    /**
      * Get the object value of a property
      *
      * @param find {@link FindProperty} lookup result
      *
      * @return the value of the property
      */
    protected static Object getObjectValue(final FindProperty find) {
        final MethodHandle getter = find.getGetter(Object.class);
        if (getter != null) {
            try {
                return getter.invokeExact((Object)find.getOwner());
            } catch (final Error|RuntimeException e) {
                throw e;
            } catch (final Throwable e) {
                throw new RuntimeException(e);
            }
        }

        return UNDEFINED;
    }

    /**
     * Return methodHandle of value function for call.
     *
     * @param find      data from find property.
     * @param type      method type of function.
     * @param bindName  null or name to bind to second argument (property not found method.)
     *
     * @return value of property as a MethodHandle or null.
     *
     */
    @SuppressWarnings("static-method")
    protected MethodHandle getCallMethodHandle(final FindProperty find, final MethodType type, final String bindName) {
        return getCallMethodHandle(getObjectValue(find), type, bindName);
    }

    /**
     * Return methodHandle of value function for call.
     *
     * @param value     value of receiver, it not a {@link ScriptFunction} this will return null.
     * @param type      method type of function.
     * @param bindName  null or name to bind to second argument (property not found method.)
     *
     * @return value of property as a MethodHandle or null.
     */
    protected static MethodHandle getCallMethodHandle(final Object value, final MethodType type, final String bindName) {
        return value instanceof ScriptFunction ? ((ScriptFunction)value).getCallMethodHandle(type, bindName) : null;
    }

    /**
     * Get value using found property.
     *
     * @param property Found property.
     *
     * @return Value of property.
     */
    public final Object getWithProperty(final Property property) {
        return getObjectValue(new FindProperty(this, this, getMap(), property, 0));
    }

    /**
     * Get a property given a key
     *
     * @param key property key
     *
     * @return property for key
     */
    public final Property getProperty(final String key) {
        return getMap().findProperty(key);
    }

    static String convertKey(final Object key) {
        return (key instanceof String) ? (String)key : JSType.toString(key);
    }

    /**
     * Overridden by {@link jdk.nashorn.internal.objects.NativeArguments} class (internal use.)
     * Used for argument access in a vararg function using parameter name.
     * Returns the argument at a given key (index)
     *
     * @param key argument index
     *
     * @return the argument at the given position, or undefined if not present
     */
    public Object getArgument(final int key) {
        return get(key);
    }

    /**
     * Overridden by {@link jdk.nashorn.internal.objects.NativeArguments} class (internal use.)
     * Used for argument access in a vararg function using parameter name.
     * Returns the argument at a given key (index)
     *
     * @param key   argument index
     * @param value the value to write at the given index
     */
    public void setArgument(final int key, final Object value) {
        set(key, value, getContext()._strict);
    }

    public final boolean isStrictContext() {
        return getContext()._strict;
    }

    /**
     * Return the current context from the object's map.
     * @return Current context.
     */
    protected final Context getContext() {
        return getMap().getContext();
    }

    /**
     * Return the map of an object.
     * @return PropertyMap object.
     */
    public final PropertyMap getMap() {
        return map;
    }

    /**
     * Set the initial map.
     * @param map Initial map.
     */
    public final void setMap(final PropertyMap map) {
        this.map = map;
    }

    /**
     * Conditionally set the new map if the old map is the same.
     * @param oldMap Map prior to manipulation.
     * @param newMap Replacement map.
     * @return true if the operation succeeded.
     */
    protected synchronized final boolean compareAndSetMap(final PropertyMap oldMap, final PropertyMap newMap) {
        final boolean update = oldMap == this.map;

        if (update) {
            this.map = newMap;
        }

        return update;
     }

    /**
     * Return the __proto__ of an object.
     * @return __proto__ object.
     */
    public final ScriptObject getProto() {
        return getMap().getProto();
    }

    /**
     * Check if this is a prototype
     * @return true if {@link PropertyMap#isPrototype()} is true for this ScriptObject
     */
    public final boolean isPrototype() {
        return getMap().isPrototype();
    }

    /**
     * Set the __proto__ of an object.
     * @param newProto new __proto__ to set.
     */
    public final void setProto(final ScriptObject newProto) {
        PropertyMap  oldMap   = getMap();
        ScriptObject oldProto = getProto();

        while (oldProto != newProto) {
            final PropertyMap newMap = oldMap.setProto(newProto);

            if (!compareAndSetMap(oldMap, newMap)) {
                oldMap = getMap();
                oldProto = getProto();
            } else {
                if (isPrototype()) {

                    if (oldProto != null) {
                        oldProto.removePropertyListener(this);
                    }

                    if (newProto != null) {
                        newProto.addPropertyListener(this);
                    }
                }

                return;
            }
        }
    }

    /**
     * Set the __proto__ of an object with checks.
     * @param newProto Prototype to set.
     */
    public final void setProtoCheck(final Object newProto) {
        if (newProto == null || newProto instanceof ScriptObject) {
            setProto((ScriptObject)newProto);
        } else {
            final ScriptObject global = Context.getGlobalTrusted();
            final Object  newProtoObject = JSType.toScriptObject(global, newProto);

            if (newProtoObject instanceof ScriptObject) {
                setProto((ScriptObject)newProtoObject);
            } else {
                typeError(global, "cant.set.proto.to.non.object", ScriptRuntime.safeToString(this), ScriptRuntime.safeToString(newProto));
            }
        }
    }

    /**
     * return a List of own keys associated with the object.
     * @param all True if to include non-enumerable keys.
     * @return Array of keys.
     */
    public String[] getOwnKeys(final boolean all) {
        final List<Object> keys    = new ArrayList<>();
        final PropertyMap  selfMap = this.getMap();

        final ArrayData array  = getArray();
        final long length      = array.length();

        for (long i = 0; i < length; i = array.nextIndex(i)) {
            if (array.has((int)i)) {
                keys.add(JSType.toString(i));
            }
        }

        for (final Property property : selfMap.getProperties()) {
            if (all || property.isEnumerable()) {
                keys.add(property.getKey());
            }
        }

        return keys.toArray(new String[keys.size()]);
    }

    /**
     * Check if this ScriptObject has array entries. This means that someone has
     * set values with numeric keys in the object.
     *
     * Note: this can be O(n) up to the array length
     *
     * @return true if array entries exists.
     */
    public boolean hasArrayEntries() {
        final ArrayData array = getArray();
        final long length = array.length();

        for (long i = 0; i < length; i++) {
            if (array.has((int)i)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Return the valid JavaScript type name descriptor
     *
     * @return "Object"
     */
    public String getClassName() {
        return "Object";
    }

    /**
     * {@code length} is a well known property. This is its getter.
     * Note that this *may* be optimized by other classes
     *
     * @return length property value for this ScriptObject
     */
    public Object getLength() {
        return get("length");
    }

    /**
     * Stateless toString for ScriptObjects.
     *
     * @return string description of this object, e.g. {@code [object Object]}
     */
    public String safeToString() {
        return "[object " + getClassName() + "]";
    }

    /**
     * Return the default value of the object with a given preferred type hint.
     * The preferred type hints are String.class for type String, Number.class
     * for type Number. <p>
     *
     * A <code>hint</code> of null means "no hint".
     *
     * ECMA 8.12.8 [[DefaultValue]](hint)
     *
     * @param typeHint the preferred type hint
     * @return the default value
     */
    public Object getDefaultValue(final Class<?> typeHint) {
        // We delegate to GlobalObject, as the implementation uses dynamic call sites to invoke object's "toString" and
        // "valueOf" methods, and in order to avoid those call sites from becoming megamorphic when multiple contexts
        // are being executed in a long-running program, we move the code and their associated dynamic call sites
        // (Global.TO_STRING and Global.VALUE_OF) into per-context code.
        return ((GlobalObject)Context.getGlobalTrusted()).getDefaultValue(this, typeHint);
    }

    /**
     * Checking whether a script object is an instance of another. Used
     * in {@link ScriptFunction} for hasInstance implementation, walks
     * the proto chain
     *
     * @param instance instace to check
     * @return true if instance of instance
     */
    public boolean isInstance(final ScriptObject instance) {
        return false;
    }

    /**
     * Flag this ScriptObject as non extensible
     *
     * @return the object after being made non extensible
     */
    public ScriptObject preventExtensions() {
        PropertyMap oldMap = getMap();

        while (true) {
            final PropertyMap newMap = getMap().preventExtensions();

            if (!compareAndSetMap(oldMap, newMap)) {
                oldMap = getMap();
            } else {
                return this;
            }
        }
    }

    /**
     * Check whether if an Object (not just a ScriptObject) represents JavaScript array
     *
     * @param obj object to check
     *
     * @return true if array
     */
    public static boolean isArray(final Object obj) {
        return (obj instanceof ScriptObject) && ((ScriptObject)obj).isArray();
    }

    /**
     * Check if this ScriptObject is an array
     * @return true if array
     */
    public final boolean isArray() {
        return (flags & IS_ARRAY) != 0;
    }

    /**
     * Flag this ScriptObject as being an array
     */
    public final void setIsArray() {
        flags |= IS_ARRAY;
    }

    /**
     * Check if this ScriptObject is an {@code arguments} vector
     * @return true if arguments vector
     */
    public final boolean isArguments() {
        return (flags & IS_ARGUMENTS) != 0;
    }

    /**
     * Flag this ScriptObject as being an {@code arguments} vector
     */
    public final void setIsArguments() {
        flags |= IS_ARGUMENTS;
    }

    /**
     * Get the {@link ArrayData} for this ScriptObject if it is an array
     * @return array data
     */
    public final ArrayData getArray() {
        return arrayData;
    }

    /**
     * Set the {@link ArrayData} for this ScriptObject if it is to be an array
     * @param arrayData the array data
     */
    public final void setArray(final ArrayData arrayData) {
        this.arrayData = arrayData;
    }

    /**
     * Check if this ScriptObject is extensible
     * @return true if extensible
     */
    public boolean isExtensible() {
        return getMap().isExtensible();
    }

    /**
     * ECMAScript 15.2.3.8 - seal implementation
     * @return the sealed ScriptObject
     */
    public ScriptObject seal() {
        PropertyMap oldMap = getMap();

        while (true) {
            final PropertyMap newMap = getMap().seal();

            if (!compareAndSetMap(oldMap, newMap)) {
                oldMap = getMap();
            } else {
                setArray(ArrayData.seal(getArray()));
                return this;
            }
        }
    }

    /**
     * Check whether this ScriptObject is sealed
     * @return true if sealed
     */
    public boolean isSealed() {
        return getMap().isSealed();
    }

    /**
     * ECMA 15.2.39 - freeze implementation. Freeze this ScriptObject
     * @return the frozen ScriptObject
     */
    public ScriptObject freeze() {
        PropertyMap oldMap = getMap();

        while (true) {
            final PropertyMap newMap = getMap().freeze();

            if (!compareAndSetMap(oldMap, newMap)) {
                oldMap = getMap();
            } else {
                setArray(ArrayData.freeze(getArray()));
                return this;
            }
        }
    }

    /**
     * Check whether this ScriptObject is frozen
     * @return true if frozed
     */
    public boolean isFrozen() {
        return getMap().isFrozen();
    }


    /**
     * Flag this ScriptObject as scope
     */
    public final void setIsScope() {
        if (Context.DEBUG) {
            scopeCount++;
        }
        flags |= IS_SCOPE;
    }

    /**
     * Check whether this ScriptObject is scope
     * @return true if scope
     */
    public final boolean isScope() {
        return (flags & IS_SCOPE) != 0;
    }

    // java.util.Map-like methods to help ScriptObjectMirror implementation
    public void clear() {
        final boolean strict = getContext()._strict;
        final Iterator<String> iter = propertyIterator();
        while (iter.hasNext()) {
            delete(iter.next(), strict);
        }
    }

    public boolean containsKey(final Object key) {
        return has(key);
    }

    public boolean containsValue(final Object value) {
        final Iterator<Object> iter = valueIterator();
        while (iter.hasNext()) {
            if (iter.next().equals(value)) {
                return true;
            }
        }
        return false;
    }

    public Set<Map.Entry<Object, Object>> entrySet() {
        final Iterator<String> iter = propertyIterator();
        final Set<Map.Entry<Object, Object>> entries = new HashSet<>();
        while (iter.hasNext()) {
            final Object key = iter.next();
            entries.add(new AbstractMap.SimpleImmutableEntry<>(key, get(key)));
        }
        return Collections.unmodifiableSet(entries);
    }

    public boolean isEmpty() {
        return !propertyIterator().hasNext();
    }

    public Set<Object> keySet() {
        final Iterator<String> iter = propertyIterator();
        final Set<Object> keySet = new HashSet<>();
        while (iter.hasNext()) {
            keySet.add(iter.next());
        }
        return Collections.unmodifiableSet(keySet);
    }

    public Object put(final Object key, final Object value) {
        final Object oldValue = get(key);
        set(key, value, getContext()._strict);
        return oldValue;
    }

    public void putAll(final Map<?, ?> otherMap) {
        final boolean strict = getContext()._strict;
        for (final Map.Entry<?, ?> entry : otherMap.entrySet()) {
            set(entry.getKey(), entry.getValue(), strict);
        }
    }

    public Object remove(final Object key) {
        final Object oldValue = get(key);
        delete(key, getContext()._strict);
        return oldValue;
    }

    public int size() {
        int n = 0;
        for (final Iterator<String> iter = propertyIterator(); iter.hasNext(); iter.next()) {
            n++;
        }
        return n;
    }

    public Collection<Object> values() {
        final List<Object>     values = new ArrayList<>(size());
        final Iterator<Object> iter   = valueIterator();
        while (iter.hasNext()) {
            values.add(iter.next());
        }
        return Collections.unmodifiableList(values);
    }

    /**
     * Lookup method that, given a CallSiteDescriptor, looks up the target
     * MethodHandle and creates a GuardedInvocation
     * with the appropriate guard(s).
     *
     * @param desc call site descriptor
     * @param request the link request
     *
     * @return GuardedInvocation for the callsite
     */
    public GuardedInvocation lookup(final CallSiteDescriptor desc, final LinkRequest request) {
        final int c = desc.getNameTokenCount();
        // JavaScript is "immune" to all currently defined Dynalink composite operation - getProp is the same as getElem
        // is the same as getMethod as JavaScript objects have a single namespace for all three. Therefore, we don't
        // care about them, and just link to whatever is the first operation.
        final String operator = CallSiteDescriptorFactory.tokenizeOperators(desc).get(0);
        // NOTE: we support getElem and setItem as JavaScript doesn't distinguish items from properties. Nashorn itself
        // emits "dyn:getProp:identifier" for "<expr>.<identifier>" and "dyn:getElem" for "<expr>[<expr>]", but we are
        // more flexible here and dispatch not on operation name (getProp vs. getElem), but rather on whether the
        // operation has an associated name or not.
        switch (operator) {
        case "getProp":
        case "getElem":
        case "getMethod":
            return c > 2 ? findGetMethod(desc, request, operator) : findGetIndexMethod(desc, request);
        case "setProp":
        case "setElem":
            return c > 2 ? findSetMethod(desc, request) : findSetIndexMethod(desc);
        case "call":
            return findCallMethod(desc, request);
        case "new":
            return findNewMethod(desc);
        case "callMethod":
            return findCallMethodMethod(desc, request);
        default:
            return null;
        }
    }

    /**
     * Find the appropriate New method for an invoke dynamic call.
     *
     * @param desc The invoke dynamic call site descriptor.
     *
     * @return GuardedInvocation to be invoked at call site.
     */
    protected GuardedInvocation findNewMethod(final CallSiteDescriptor desc) {
        return notAFunction();
    }

    /**
     * Find the appropriate CALL method for an invoke dynamic call.
     * This generates "not a function" always
     *
     * @param desc    the call site descriptor.
     * @param request the link request
     *
     * @return GuardedInvocation to be invoed at call site.
     */
    protected GuardedInvocation findCallMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        return notAFunction();
    }

    private GuardedInvocation notAFunction() {
        typeError("not.a.function", ScriptRuntime.safeToString(this));
        return null;
    }

    /**
     * Find an implementation for a "dyn:callMethod" operation. Note that Nashorn internally never uses
     * "dyn:callMethod", but instead always emits two call sites in bytecode, one for "dyn:getMethod", and then another
     * one for "dyn:call". Explicit support for "dyn:callMethod" is provided for the benefit of potential external
     * callers. The implementation itself actually folds a "dyn:getMethod" method handle into a "dyn:call" method handle.
     *
     * @param desc    the call site descriptor.
     * @param request the link request
     *
     * @return GuardedInvocation to be invoked at call site.
     */
    protected GuardedInvocation findCallMethodMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        // R(P0, P1, ...)
        final MethodType callType = desc.getMethodType();
        // use type Object(P0) for the getter
        final CallSiteDescriptor getterType = desc.changeMethodType(MethodType.methodType(Object.class, callType.parameterType(0)));
        final GuardedInvocation getter = findGetMethod(getterType, request, "getMethod");

        // Object(P0) => Object(P0, P1, ...)
        final MethodHandle argDroppingGetter = MH.dropArguments(getter.getInvocation(), 1, callType.parameterList().subList(1, callType.parameterCount()));
        // R(Object, P0, P1, ...)
        final MethodHandle invoker = Bootstrap.createDynamicInvoker("dyn:call", callType.insertParameterTypes(0, argDroppingGetter.type().returnType()));
        // Fold Object(P0, P1, ...) into R(Object, P0, P1, ...) => R(P0, P1, ...)
        return getter.replaceMethods(MH.foldArguments(invoker, argDroppingGetter), getter.getGuard());
    }

    /**
     * Find the appropriate GET method for an invoke dynamic call.
     *
     * @param desc     the call site descriptor
     * @param request  the link request
     * @param operator operator for get: getProp, getMethod, getElem etc
     *
     * @return GuardedInvocation to be invoked at call site.
     */
    protected GuardedInvocation findGetMethod(final CallSiteDescriptor desc, final LinkRequest request, final String operator) {
        final String name = desc.getNameToken(2);

        if (request.isCallSiteUnstable()) {
            return findMegaMorphicGetMethod(desc, name);
        }

        final FindProperty find = findProperty(name, true);

        MethodHandle methodHandle;

        if (find == null) {
            if ("getProp".equals(operator)) {
                return noSuchProperty(desc, request);
            } else if ("getMethod".equals(operator)) {
                return noSuchMethod(desc, request);
            } else if ("getElem".equals(operator)) {
                return createEmptyGetter(desc, name);
            }
            throw new AssertionError(); // never invoked with any other operation
        }

        final Class<?> returnType = desc.getMethodType().returnType();
        final Property property = find.getProperty();
        methodHandle = find.getGetter(returnType);

        // getMap() is fine as we have the prototype switchpoint depending on where the property was found
        final MethodHandle guard = NashornGuards.getMapGuard(getMap());

        if (methodHandle != null) {
            assert methodHandle.type().returnType().equals(returnType);
            final ScriptFunction getter = find.getGetterFunction();
            final boolean nonStrict = getter != null && getter.isNonStrictFunction();
            if (find.isSelf()) {
                return new NashornGuardedInvocation(methodHandle, null, ObjectClassGenerator.OBJECT_FIELDS_ONLY &&
                        NashornCallSiteDescriptor.isFastScope(desc) && !property.canChangeType() ? null : guard,
                            nonStrict);
            }

            final ScriptObject prototype = find.getOwner();

            if (!property.hasGetterFunction()) {
                methodHandle = bindTo(methodHandle, prototype);
            }
            return new NashornGuardedInvocation(methodHandle, getMap().getProtoGetSwitchPoint(name), guard, nonStrict);
        }

        assert !NashornCallSiteDescriptor.isFastScope(desc);
        return new GuardedInvocation(Lookup.emptyGetter(returnType), getMap().getProtoGetSwitchPoint(name), guard);
    }

    private static GuardedInvocation findMegaMorphicGetMethod(final CallSiteDescriptor desc, final String name) {
        final GuardedInvocation inv = findGetIndexMethod(desc.getMethodType().insertParameterTypes(1, Object.class));
        return inv.replaceMethods(MH.insertArguments(inv.getInvocation(), 1, name), inv.getGuard());
    }

    /**
     * Find the appropriate GETINDEX method for an invoke dynamic call.
     *
     * @param desc    the call site descriptor
     * @param request the link request
     *
     * @return GuardedInvocation to be invoked at call site.
     */
    protected GuardedInvocation findGetIndexMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        return findGetIndexMethod(desc.getMethodType());
    }

    /**
     * Find the appropriate GETINDEX method for an invoke dynamic call.
     *
     * @param callType the call site method type
     * @return GuardedInvocation to be invoked at call site.
     */
    private static GuardedInvocation findGetIndexMethod(final MethodType callType) {
        final Class<?> returnClass = callType.returnType();
        final Class<?> keyClass    = callType.parameterType(1);

        String name = "get";
        if (returnClass.isPrimitive()) {
            //turn e.g. get with a double into getDouble
            final String returnTypeName = returnClass.getName();
            name += Character.toUpperCase(returnTypeName.charAt(0)) + returnTypeName.substring(1, returnTypeName.length());
        }

        return new GuardedInvocation(findOwnMH(name, returnClass, keyClass), getScriptObjectGuard(callType));
    }

    private static MethodHandle getScriptObjectGuard(final MethodType type) {
        return ScriptObject.class.isAssignableFrom(type.parameterType(0)) ? null : NashornGuards.getScriptObjectGuard();
    }

    /**
     * Find the appropriate SET method for an invoke dynamic call.
     *
     * @param desc    the call site descriptor
     * @param request the link request
     *
     * @return GuardedInvocation to be invoked at call site.
     */
    protected GuardedInvocation findSetMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        final String name = desc.getNameToken(CallSiteDescriptor.NAME_OPERAND);
        if(request.isCallSiteUnstable()) {
            return findMegaMorphicSetMethod(desc, name);
        }

        final boolean scope = isScope();
        /*
         * If doing property set on a scope object, we should stop proto search on the first
         * non-scope object. Without this, for exmaple, when assigning "toString" on global scope,
         * we'll end up assigning it on it's proto - which is Object.prototype.toString !!
         *
         * toString = function() { print("global toString"); } // don't affect Object.prototype.toString
         */
        FindProperty find = findProperty(name, true, scope);
        // If it's not a scope search, then we don't want any inherited properties except those with user defined accessors.
        if (!scope && find != null && find.isInherited() && !(find.getProperty() instanceof UserAccessorProperty)) {
            // We should still check if inherited data property is not writable
            if (isExtensible() && !find.isWritable()) {
                return createEmptySetMethod(desc, "property.not.writable", false);
            }
            // Otherwise, forget the found property
            find = null;
        }

        if (find != null) {
            if(!find.isWritable()) {
                // Existing, non-writable property
                return createEmptySetMethod(desc, "property.not.writable", true);
            }
        } else if (!isExtensible()) {
            // Non-existing property on a non-extensible object
            return createEmptySetMethod(desc, "object.non.extensible", false);
        }

        return new SetMethodCreator(this, find, desc).createGuardedInvocation();
    }

    private GuardedInvocation createEmptySetMethod(final CallSiteDescriptor desc, String strictErrorMessage, boolean canBeFastScope) {
        final String name = desc.getNameToken(CallSiteDescriptor.NAME_OPERAND);
        if (NashornCallSiteDescriptor.isStrict(desc)) {
               typeError(strictErrorMessage, name, ScriptRuntime.safeToString((this)));
           }
           assert canBeFastScope || !NashornCallSiteDescriptor.isFastScope(desc);
           final PropertyMap myMap = getMap();
           return new GuardedInvocation(Lookup.EMPTY_SETTER, myMap.getProtoGetSwitchPoint(name), NashornGuards.getMapGuard(myMap));
    }

    @SuppressWarnings("unused")
    private static void setEmbed(final CallSiteDescriptor desc, final PropertyMap oldMap, final PropertyMap newMap, final MethodHandle setter, final int i, final Object self, final Object value) throws Throwable {
        final ScriptObject obj = (ScriptObject)self;
        if (obj.trySetEmbedOrSpill(desc, oldMap, newMap, value)) {
            obj.useEmbed(i);
            setter.invokeExact(self, value);
        }
    }

    @SuppressWarnings("unused")
    private static void setSpill(final CallSiteDescriptor desc, final PropertyMap oldMap, final PropertyMap newMap, final int index, final Object self, final Object value) {
        final ScriptObject obj = (ScriptObject)self;
        if (obj.trySetEmbedOrSpill(desc, oldMap, newMap, value)) {
            obj.spill[index] = value;
        }
    }

    private boolean trySetEmbedOrSpill(final CallSiteDescriptor desc, final PropertyMap oldMap, final PropertyMap newMap, final Object value) {
        final boolean isStrict = NashornCallSiteDescriptor.isStrict(desc);
        if (!isExtensible() && isStrict) {
            typeError("object.non.extensible", desc.getNameToken(2), ScriptRuntime.safeToString(this));
            throw new AssertionError(); // never reached
        } else if (compareAndSetMap(oldMap, newMap)) {
            return true;
        } else {
            set(desc.getNameToken(CallSiteDescriptor.NAME_OPERAND), value, isStrict);
            return false;
        }
    }

    @SuppressWarnings("unused")
    private static void setSpillWithNew(final CallSiteDescriptor desc, final PropertyMap oldMap, final PropertyMap newMap, final int index, final Object self, final Object value) {
        final ScriptObject obj      = (ScriptObject)self;
        final boolean      isStrict = NashornCallSiteDescriptor.isStrict(desc);

        if (!obj.isExtensible()) {
            if (isStrict) {
                typeError("object.non.extensible", desc.getNameToken(2), ScriptRuntime.safeToString(obj));
            }
        } else if (obj.compareAndSetMap(oldMap, newMap)) {
            obj.spill = new Object[SPILL_RATE];
            obj.spill[index] = value;
        } else {
            obj.set(desc.getNameToken(2), value, isStrict);
        }
    }

    @SuppressWarnings("unused")
    private static void setSpillWithGrow(final CallSiteDescriptor desc, final PropertyMap oldMap, final PropertyMap newMap, final int index, final int newLength, final Object self, final Object value) {
        final ScriptObject obj      = (ScriptObject)self;
        final boolean      isStrict = NashornCallSiteDescriptor.isStrict(desc);

        if (!obj.isExtensible()) {
            if (isStrict) {
                typeError("object.non.extensible", desc.getNameToken(2), ScriptRuntime.safeToString(obj));
            }
        } else if (obj.compareAndSetMap(oldMap, newMap)) {
            final int oldLength = obj.spill.length;
            final Object[] newSpill = new Object[newLength];
            System.arraycopy(obj.spill, 0, newSpill, 0, oldLength);
            obj.spill = newSpill;
            obj.spill[index] = value;
        } else {
            obj.set(desc.getNameToken(2), value, isStrict);
        }
    }

    private static GuardedInvocation findMegaMorphicSetMethod(final CallSiteDescriptor desc, final String name) {
        final GuardedInvocation inv = findSetIndexMethod(desc.getMethodType().insertParameterTypes(1, Object.class),
                NashornCallSiteDescriptor.isStrict(desc));
        return inv.replaceMethods(MH.insertArguments(inv.getInvocation(), 1, name), inv.getGuard());
    }

    private static GuardedInvocation findSetIndexMethod(final CallSiteDescriptor desc) { // array, index, value
        return findSetIndexMethod(desc.getMethodType(), NashornCallSiteDescriptor.isStrict(desc));
    }

    /**
     * Find the appropriate SETINDEX method for an invoke dynamic call.
     *
     * @param callType the method type at the call site
     * @param isStrict are we in strict mode?
     *
     * @return GuardedInvocation to be invoked at call site.
     */
    private static GuardedInvocation findSetIndexMethod(final MethodType callType, final boolean isStrict) {
        assert callType.parameterCount() == 3;

        final Class<?>   keyClass   = callType.parameterType(1);
        final Class<?>   valueClass = callType.parameterType(2);

        MethodHandle methodHandle = findOwnMH("set", void.class, keyClass, valueClass, boolean.class);
        methodHandle = MH.insertArguments(methodHandle, 3, isStrict);

        return new GuardedInvocation(methodHandle, getScriptObjectGuard(callType));
    }

    /**
     * Fall back if a function property is not found.
     * @param desc The call site descriptor
     * @param request the link request
     * @return GuardedInvocation to be invoked at call site.
     */
    public GuardedInvocation noSuchMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        final String       name      = desc.getNameToken(2);
        final FindProperty find      = findProperty(NO_SUCH_METHOD_NAME, true);
        final boolean      scopeCall = isScope() && NashornCallSiteDescriptor.isScope(desc);

        if (find == null) {
            if (scopeCall) {
                ECMAErrors.referenceError("not.defined", name);
                throw new AssertionError(); // never reached
            }
            return createEmptyGetter(desc, name);
        }

        final ScriptFunction func = (ScriptFunction)getObjectValue(find);
        final Object thiz = scopeCall && func.isStrict() ? ScriptRuntime.UNDEFINED : this;
        // TODO: It'd be awesome if we could bind "name" without binding "this".
        return new GuardedInvocation(MH.dropArguments(MH.constant(ScriptFunction.class,
                func.makeBoundFunction(thiz, new Object[] { name })), 0, Object.class),
                null, NashornGuards.getMapGuard(getMap()));
    }

    /**
     * Fall back if a property is not found.
     * @param desc the call site descriptor.
     * @param request the link request
     * @return GuardedInvocation to be invoked at call site.
     */
    public GuardedInvocation noSuchProperty(final CallSiteDescriptor desc, final LinkRequest request) {
        final String name = desc.getNameToken(2);
        final FindProperty find = findProperty(NO_SUCH_PROPERTY_NAME, true);
        final boolean scopeAccess = isScope() && NashornCallSiteDescriptor.isScope(desc);

        if (find != null) {
            final ScriptFunction func = (ScriptFunction)getObjectValue(find);
            MethodHandle methodHandle = getCallMethodHandle(func, desc.getMethodType(), name);

            if (methodHandle != null) {
                if (scopeAccess && func.isStrict()) {
                    methodHandle = bindTo(methodHandle, UNDEFINED);
                }
                return new GuardedInvocation(methodHandle,
                        find.isInherited()? getMap().getProtoGetSwitchPoint(NO_SUCH_PROPERTY_NAME) : null,
                        getKnownFunctionPropertyGuard(getMap(), find.getGetter(Object.class), find.getOwner(), func));
            }
        }

        if (scopeAccess) {
            referenceError("not.defined", name);
        }

        return createEmptyGetter(desc, name);
    }

    private GuardedInvocation createEmptyGetter(final CallSiteDescriptor desc, final String name) {
        return new GuardedInvocation(Lookup.emptyGetter(desc.getMethodType().returnType()), getMap().getProtoGetSwitchPoint(name), NashornGuards.getMapGuard(getMap()));
    }

    private abstract static class ScriptObjectIterator <T extends Object> implements Iterator<T> {
        protected T[] values;
        protected final ScriptObject object;
        private int index;

        ScriptObjectIterator(final ScriptObject object) {
            this.object = object;
        }

        protected abstract void init();

        @Override
        public boolean hasNext() {
            if (values == null) {
                init();
            }
            return index < values.length;
        }

        @Override
        public T next() {
            if (values == null) {
                init();
            }
            return values[index++];
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static class KeyIterator extends ScriptObjectIterator<String> {
        KeyIterator(final ScriptObject object) {
            super(object);
        }

        @Override
        protected void init() {
            final Set<String> keys = new LinkedHashSet<>();
            for (ScriptObject self = object; self != null; self = self.getProto()) {
                keys.addAll(Arrays.asList(self.getOwnKeys(false)));
            }
            this.values = keys.toArray(new String[keys.size()]);
        }
    }

    private static class ValueIterator extends ScriptObjectIterator<Object> {
        ValueIterator(final ScriptObject object) {
            super(object);
        }

        @Override
        protected void init() {
            final ArrayList<Object> valueList = new ArrayList<>();
            for (ScriptObject self = object; self != null; self = self.getProto()) {
                for (final String key : self.getOwnKeys(false)) {
                    valueList.add(self.get(key));
                }
            }
            this.values = valueList.toArray(new Object[valueList.size()]);
        }
    }

    /**
     * Add a spill property for the given key.
     * @param key           Property key.
     * @param propertyFlags Property flags.
     * @return Added property.
     */
    private Property addSpillProperty(final String key, final int propertyFlags) {
        int i = findEmbed();
        Property spillProperty;

        if (i >= EMBED_SIZE) {
            i = getMap().getSpillLength();
            MethodHandle getter = MH.arrayElementGetter(Object[].class);
            MethodHandle setter = MH.arrayElementSetter(Object[].class);
            getter = MH.asType(MH.insertArguments(getter, 1, i), Lookup.GET_OBJECT_TYPE);
            setter = MH.asType(MH.insertArguments(setter, 1, i), Lookup.SET_OBJECT_TYPE);
            spillProperty = new SpillProperty(key, propertyFlags | Property.IS_SPILL, i, getter, setter);
            notifyPropertyAdded(this, spillProperty);
            spillProperty = addOwnProperty(spillProperty);
            i = spillProperty.getSlot();

            final int newLength = (i + SPILL_RATE) / SPILL_RATE * SPILL_RATE;
            final Object[] newSpill = new Object[newLength];

            if (spill != null) {
                System.arraycopy(spill, 0, newSpill, 0, spill.length);
            }

            spill = newSpill;
         } else {
            useEmbed(i);
            spillProperty = new SpillProperty(key, propertyFlags, i, GET_EMBED[i], SET_EMBED[i]);
            notifyPropertyAdded(this, spillProperty);
            spillProperty = addOwnProperty(spillProperty);
        }

        return spillProperty;
    }


    /**
     * Add a spill entry for the given key.
     * @param key           Property key.
     * @param propertyFlags Property flags.
     * @return Setter method handle.
     */
    private MethodHandle addSpill(final String key, final int propertyFlags) {
        final Property spillProperty = addSpillProperty(key, propertyFlags);
        final Class<?> type = Object.class;
        return spillProperty.getSetter(type, getMap()); //TODO specfields
    }

    MethodHandle addSpill(final String key) {
        return addSpill(key, 0);
    }

    /**
     * Make sure arguments are paired correctly, with respect to more parameters than declared,
     * fewer parameters than declared and other things that JavaScript allows. This might involve
     * creating collectors.
     *
     * @param methodHandle method handle for invoke
     * @param callType     type of the call
     *
     * @return method handle with adjusted arguments
     */
    protected static MethodHandle pairArguments(final MethodHandle methodHandle, final MethodType callType) {
        return pairArguments(methodHandle, callType, null);
    }

    /**
     * Make sure arguments are paired correctly, with respect to more parameters than declared,
     * fewer parameters than declared and other things that JavaScript allows. This might involve
     * creating collectors.
     *
     * Make sure arguments are paired correctly.
     * @param methodHandle MethodHandle to adjust.
     * @param callType     MethodType of caller.
     * @param callerVarArg true if the caller is vararg, false otherwise, null if it should be inferred.
     *
     * @return method handle with adjusted arguments
     */
    public static MethodHandle pairArguments(final MethodHandle methodHandle, final MethodType callType, final Boolean callerVarArg) {

        final MethodType methodType = methodHandle.type();
        if (methodType.equals(callType)) {
            return methodHandle;
        }

        final int parameterCount = methodType.parameterCount();
        final int callCount      = callType.parameterCount();

        final boolean isCalleeVarArg = parameterCount > 0 && methodType.parameterType(parameterCount - 1).isArray();
        final boolean isCallerVarArg = callerVarArg != null ? callerVarArg.booleanValue() : (callCount > 1 &&
                callType.parameterType(callCount - 1).isArray());

        if (callCount < parameterCount) {
            final int      missingArgs = parameterCount - callCount;
            final Object[] fillers     = new Object[missingArgs];

            Arrays.fill(fillers, UNDEFINED);

            if (isCalleeVarArg) {
                fillers[missingArgs - 1] = new Object[0];
            }

            return MH.insertArguments(
                methodHandle,
                parameterCount - missingArgs,
                fillers);
        }

        if (isCalleeVarArg) {
            return isCallerVarArg ?
                methodHandle :
                MH.asCollector(methodHandle, Object[].class, callCount - parameterCount + 1);
        }

        if (isCallerVarArg) {
            final int spreadArgs = parameterCount - callCount + 1;
            return MH.filterArguments(
                MH.asSpreader(
                    methodHandle,
                    Object[].class,
                    spreadArgs),
                callCount - 1,
                MH.insertArguments(
                    TRUNCATINGFILTER,
                    0,
                    spreadArgs)
                );
        }

        if (callCount > parameterCount) {
            final int discardedArgs = callCount - parameterCount;

            final Class<?>[] discards = new Class<?>[discardedArgs];
            Arrays.fill(discards, Object.class);

            return MH.dropArguments(methodHandle, callCount - discardedArgs, discards);
        }

        return methodHandle;
    }

    @SuppressWarnings("unused")
    private static Object[] truncatingFilter(final int n, final Object[] array) {
        final int length = array == null ? 0 : array.length;
        if (n == length) {
            return array == null ? new Object[0] : array;
        }

        final Object[] newArray = new Object[n];

        if (array != null) {
            for (int i = 0; i < n && i < length; i++) {
                newArray[i] = array[i];
            }
        }

        if (length < n) {
            final Object fill = UNDEFINED;

            for (int i = length; i < n; i++) {
                newArray[i] = fill;
            }
        }

        return newArray;
    }

    /**
      * Numeric length setter for length property
      *
      * @param newLength new length to set
      */
    public final void setLength(final long newLength) {
       final long arrayLength = getArray().length();
       if (newLength == arrayLength) {
           return;
       }

       final boolean isStrict = getContext()._strict;

       if (newLength > arrayLength) {
           setArray(getArray().ensure(newLength - 1));
            if (getArray().canDelete(arrayLength, (newLength - 1), isStrict)) {
               setArray(getArray().delete(arrayLength, (newLength - 1)));
           }
           return;
       }

       if (newLength < arrayLength) {
           setArray(getArray().shrink(newLength));
           getArray().setLength(newLength);
       }
   }

    @Override
    public int getInt(final Object key) {
        final int index = getArrayIndexNoThrow(key);

        if (getArray().has(index)) {
            return getArray().getInt(index);
        }

        final FindProperty find = findProperty(convertKey(key), false);

        if (find != null) {
            return getIntValue(find);
        }

        final ScriptObject proto = this.getProto();

        return proto != null ? proto.getInt(key) : 0;
    }

    @Override
    public int getInt(final double key) {
        final int index = getArrayIndexNoThrow(key);

        if (getArray().has(index)) {
            return getArray().getInt(index);
        }

        final FindProperty find = findProperty(convertKey(key), false);

        if (find != null) {
            return getIntValue(find);
        }

        final ScriptObject proto = this.getProto();

        return proto != null ? proto.getInt(key) : 0;
    }

    @Override
    public int getInt(final long key) {
        final int index = getArrayIndexNoThrow(key);

        if (getArray().has(index)) {
            return getArray().getInt(index);
        }

        final FindProperty find = findProperty(convertKey(key), false);

        if (find != null) {
            return getIntValue(find);
        }

        final ScriptObject proto = this.getProto();

        return proto != null ? proto.getInt(key) : 0;
    }

    @Override
    public int getInt(final int key) {
        final int index = getArrayIndexNoThrow(key);

        if (getArray().has(index)) {
            return getArray().getInt(index);
        }

        final FindProperty find = findProperty(convertKey(key), false);

        if (find != null) {
            return getIntValue(find);
        }

        final ScriptObject proto = this.getProto();

        return proto != null ? proto.getInt(key) : 0;
    }

    @Override
    public long getLong(final Object key) {
        final int index = getArrayIndexNoThrow(key);

        if (getArray().has(index)) {
            return getArray().getLong(index);
        }

        final FindProperty find = findProperty(convertKey(key), false);

        if (find != null) {
            return getLongValue(find);
        }

        final ScriptObject proto = this.getProto();

        return proto != null ? proto.getLong(key) : 0L;
    }

    @Override
    public long getLong(final double key) {
        final int index = getArrayIndexNoThrow(key);

        if (getArray().has(index)) {
            return getArray().getLong(index);
        }

        final FindProperty find = findProperty(convertKey(key), false);

        if (find != null) {
            return getLongValue(find);
        }

        final ScriptObject proto = this.getProto();

        return proto != null ? proto.getLong(key) : 0L;
    }

    @Override
    public long getLong(final long key) {
        final int index = getArrayIndexNoThrow(key);

        if (getArray().has(index)) {
            return getArray().getLong(index);
        }

        final FindProperty find = findProperty(convertKey(key), false);

        if (find != null) {
            return getLongValue(find);
        }

        final ScriptObject proto = this.getProto();

        return proto != null ? proto.getLong(key) : 0L;
    }

    @Override
    public long getLong(final int key) {
        final int index = getArrayIndexNoThrow(key);

        if (getArray().has(index)) {
            return getArray().getLong(index);
        }

        final FindProperty find = findProperty(convertKey(key), false);

        if (find != null) {
            return getLongValue(find);
        }

        final ScriptObject proto = this.getProto();

        return proto != null ? proto.getLong(key) : 0L;
    }

    @Override
    public double getDouble(final Object key) {
        final int index = getArrayIndexNoThrow(key);

        if (getArray().has(index)) {
            return getArray().getDouble(index);
        }

        final FindProperty find = findProperty(convertKey(key), false);

        if (find != null) {
            return getDoubleValue(find);
        }

        final ScriptObject proto = this.getProto();

        return proto != null ? proto.getDouble(key) : Double.NaN;
    }

    @Override
    public double getDouble(final double key) {
        final int index = getArrayIndexNoThrow(key);

        if (getArray().has(index)) {
            return getArray().getDouble(index);
        }

        final FindProperty find = findProperty(convertKey(key), false);

        if (find != null) {
            return getDoubleValue(find);
        }

        final ScriptObject proto = this.getProto();

        return proto != null ? proto.getDouble(key) : Double.NaN;
    }

    @Override
    public double getDouble(final long key) {
        final int index = getArrayIndexNoThrow(key);

        if (getArray().has(index)) {
            return getArray().getDouble(index);
        }

        final FindProperty find = findProperty(convertKey(key), false);

        if (find != null) {
            return getDoubleValue(find);
        }

        final ScriptObject proto = this.getProto();

        return proto != null ? proto.getDouble(key) : Double.NaN;
    }

    @Override
    public double getDouble(final int key) {
        final int index = getArrayIndexNoThrow(key);

        if (getArray().has(index)) {
            return getArray().getDouble(index);
        }

        final FindProperty find = findProperty(convertKey(key), false);

        if (find != null) {
            return getDoubleValue(find);
        }

        final ScriptObject proto = this.getProto();

        return proto != null ? proto.getDouble(key) : Double.NaN;
    }

    @Override
    public Object get(final Object key) {
        final int index = getArrayIndexNoThrow(key);

        if (getArray().has(index)) {
            return getArray().getObject(index);
        }

        final FindProperty find = findProperty(convertKey(key), false);

        if (find != null) {
            return getObjectValue(find);
        }

        final ScriptObject proto = this.getProto();

        return proto != null ? proto.get(key) : UNDEFINED;
    }

    @Override
    public Object get(final double key) {
        final int index = getArrayIndexNoThrow(key);

        if (getArray().has(index)) {
            return getArray().getObject(index);
        }

        final FindProperty find = findProperty(convertKey(key), false);

        if (find != null) {
            return getObjectValue(find);
        }

        final ScriptObject proto = this.getProto();

        return proto != null ? proto.get(key) : UNDEFINED;
    }

    @Override
    public Object get(final long key) {
        final int index = getArrayIndexNoThrow(key);

        if (getArray().has(index)) {
            return getArray().getObject(index);
        }

        final FindProperty find = findProperty(convertKey(key), false);

        if (find != null) {
            return getObjectValue(find);
        }

        final ScriptObject proto = this.getProto();

        return proto != null ? proto.get(key) : UNDEFINED;
    }

    @Override
    public Object get(final int key) {
        final int index = getArrayIndexNoThrow(key);

        if (getArray().has(index)) {
            return getArray().getObject(index);
        }

        final FindProperty find = findProperty(convertKey(key), false);

        if (find != null) {
            return getObjectValue(find);
        }

        final ScriptObject proto = this.getProto();

        return proto != null ? proto.get(key) : UNDEFINED;
    }

    /**
     * Handle when an array doesn't have a slot - possibly grow and/or convert array.
     *
     * @param index  key as index
     * @param value  element value
     * @param strict are we in strict mode
     */
    private void doesNotHave(final int index, final Object value, final boolean strict) {
        final long oldLength = getArray().length();
        final long longIndex = index & 0xffff_ffffL;

        if (!getArray().has(index)) {
            final String key = convertKey(longIndex);
            final FindProperty find = findProperty(key, true);

            if (find != null) {
                setObject(find, strict, key, value);
                return;
            }
        }

        if (longIndex >= oldLength) {
            if (!isExtensible()) {
                if (strict) {
                    typeError("object.non.extensible", JSType.toString(index), ScriptRuntime.safeToString(this));
                }
                return;
            }
            setArray(getArray().ensure(longIndex));
        }

        if (value instanceof Integer) {
            setArray(getArray().set(index, (int)value, strict));
        } else if (value instanceof Long) {
            setArray(getArray().set(index, (long)value, strict));
        } else if (value instanceof Double) {
            setArray(getArray().set(index, (double)value, strict));
        } else {
            setArray(getArray().set(index, value, strict));
        }

        if (longIndex > oldLength) {
            ArrayData array = getArray();

            if (array.canDelete(oldLength, (longIndex - 1), strict)) {
                array = array.delete(oldLength, (longIndex - 1));
            }

            setArray(array);
        }
    }

    /**
     * This is the most generic of all Object setters. Most of the others use this in some form.
     * TODO: should be further specialized
     *
     * @param find    found property
     * @param strict  are we in strict mode
     * @param key     property key
     * @param value   property value
     */
    public final void setObject(final FindProperty find, final boolean strict, final String key, final Object value) {
        FindProperty f = find;

        if (f != null && f.isInherited() && !(f.getProperty() instanceof UserAccessorProperty)) {
            f = null;
        }

        MethodHandle setter;

        if (f != null) {
            if (!f.isWritable()) {
                if (strict) {
                    typeError("property.not.writable", key, ScriptRuntime.safeToString(this));
                }

                return;
            }

            setter = f.getSetter(Object.class, strict); //TODO specfields
            try {
                setter.invokeExact((Object)f.getOwner(), value);
            } catch (final Error|RuntimeException e) {
                throw e;
            } catch (final Throwable e) {
                throw new RuntimeException(e);
            }
        } else if (!isExtensible()) {
            if (strict) {
                typeError("object.non.extensible", key, ScriptRuntime.safeToString(this));
            }
        } else {
            spill(key, value);
        }
    }

    private void spill(final String key, final Object value) {
        try {
            addSpill(key).invokeExact((Object)this, value);
        } catch (final Error|RuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public void set(final Object key, final int value, final boolean strict) {
        final int index = getArrayIndexNoThrow(key);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        set(key, JSType.toObject(value), strict);
    }

    @Override
    public void set(final Object key, final long value, final boolean strict) {
        final int index = getArrayIndexNoThrow(key);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        set(key, JSType.toObject(value), strict);
    }

    @Override
    public void set(final Object key, final double value, final boolean strict) {
        final int index = getArrayIndexNoThrow(key);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        set(key, JSType.toObject(value), strict);
    }

    @Override
    public void set(final Object key, final Object value, final boolean strict) {
        final int index = getArrayIndexNoThrow(key);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        final String       propName = convertKey(key);
        final FindProperty find     = findProperty(propName, true);

        setObject(find, strict, propName, value);
    }

    @Override
    public void set(final double key, final int value, final boolean strict) {
        final int index = getArrayIndexNoThrow(key);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        set(JSType.toObject(key), JSType.toObject(value), strict);
    }

    @Override
    public void set(final double key, final long value, final boolean strict) {
        final int index = getArrayIndexNoThrow(key);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        set(JSType.toObject(key), JSType.toObject(value), strict);
    }

    @Override
    public void set(final double key, final double value, final boolean strict) {
        final int index = getArrayIndexNoThrow(key);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        set(JSType.toObject(key), JSType.toObject(value), strict);
    }

    @Override
    public void set(final double key, final Object value, final boolean strict) {
        final int index = getArrayIndexNoThrow(key);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        set(JSType.toObject(key), value, strict);
    }

    @Override
    public void set(final long key, final int value, final boolean strict) {
        final int index = getArrayIndexNoThrow(key);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        set(JSType.toObject(key), JSType.toObject(value), strict);
    }

    @Override
    public void set(final long key, final long value, final boolean strict) {
        final int index = getArrayIndexNoThrow(key);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        set(JSType.toObject(key), JSType.toObject(value), strict);
    }

    @Override
    public void set(final long key, final double value, final boolean strict) {
        final int index = getArrayIndexNoThrow(key);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        set(JSType.toObject(key), JSType.toObject(value), strict);
    }

    @Override
    public void set(final long key, final Object value, final boolean strict) {
        final int index = getArrayIndexNoThrow(key);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        set(JSType.toObject(key), value, strict);
    }

    @Override
    public void set(final int key, final int value, final boolean strict) {
        final int index = getArrayIndexNoThrow(key);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        set(JSType.toObject(key), JSType.toObject(value), strict);
    }

    @Override
    public void set(final int key, final long value, final boolean strict) {
        final int index = getArrayIndexNoThrow(key);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        set(JSType.toObject(key), JSType.toObject(value), strict);
    }

    @Override
    public void set(final int key, final double value, final boolean strict) {
        final int index = getArrayIndexNoThrow(key);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        set(JSType.toObject(key), JSType.toObject(value), strict);
    }

    @Override
    public void set(final int key, final Object value, final boolean strict) {
        final int index = getArrayIndexNoThrow(key);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        set(JSType.toObject(key), value, strict);
    }

    @Override
    public boolean has(final Object key) {
        final int index = getArrayIndexNoThrow(key);

        if (isValidArrayIndex(index)) {
            for (ScriptObject self = this; self != null; self = self.getProto()) {
                if (self.getArray().has(index)) {
                    return true;
                }
            }
        }

        final FindProperty find = findProperty(convertKey(key), true);

        return find != null;
    }

    @Override
    public boolean has(final double key) {
        final int index = getArrayIndexNoThrow(key);

        if (isValidArrayIndex(index)) {
            for (ScriptObject self = this; self != null; self = self.getProto()) {
                if (self.getArray().has(index)) {
                    return true;
                }
            }
        }

        final FindProperty find = findProperty(convertKey(key), true);

        return find != null;
    }

    @Override
    public boolean has(final long key) {
        final int index = getArrayIndexNoThrow(key);

        if (isValidArrayIndex(index)) {
            for (ScriptObject self = this; self != null; self = self.getProto()) {
                if (self.getArray().has(index)) {
                    return true;
                }
            }
        }

        final FindProperty find = findProperty(convertKey(key), true);

        return find != null;
    }

    @Override
    public boolean has(final int key) {
        final int index = getArrayIndexNoThrow(key);

        if (isValidArrayIndex(index)) {
            for (ScriptObject self = this; self != null; self = self.getProto()) {
                if (self.getArray().has(index)) {
                    return true;
                }
            }
        }

        final FindProperty find = findProperty(convertKey(key), true);

        return find != null;
    }

    @Override
    public boolean hasOwnProperty(final Object key) {
        final int index = getArrayIndexNoThrow(key);

        if (getArray().has(index)) {
            return true;
        }

        final FindProperty find = findProperty(convertKey(key), false);

        return find != null;
    }

    @Override
    public boolean hasOwnProperty(final int key) {
        final int index = getArrayIndexNoThrow(key);

        if (getArray().has(index)) {
            return true;
        }

        final FindProperty find = findProperty(convertKey(key), false);

        return find != null;
    }

    @Override
    public boolean hasOwnProperty(final long key) {
        final int index = getArrayIndexNoThrow(key);

        if (getArray().has(index)) {
            return true;
        }

        final FindProperty find = findProperty(convertKey(key), false);

        return find != null;
    }

    @Override
    public boolean hasOwnProperty(final double key) {
        final int index = getArrayIndexNoThrow(key);

        if (getArray().has(index)) {
            return true;
        }

        final FindProperty find = findProperty(convertKey(key), false);

        return find != null;
    }

    @Override
    public boolean delete(final int key, final boolean strict) {
        final int index = getArrayIndexNoThrow(key);
        final ArrayData array = getArray();

        if (array.has(index)) {
            if (array.canDelete(index, strict)) {
                setArray(array.delete(index));
                return true;
            }
            return false;
        }

        return deleteObject(JSType.toObject(key), strict);
    }

    @Override
    public boolean delete(final long key, final boolean strict) {
        final int index = getArrayIndexNoThrow(key);
        final ArrayData array = getArray();

        if (array.has(index)) {
            if (array.canDelete(index, strict)) {
                setArray(array.delete(index));
                return true;
            }
            return false;
        }

        return deleteObject(JSType.toObject(key), strict);
    }

    @Override
    public boolean delete(final double key, final boolean strict) {
        final int index = getArrayIndexNoThrow(key);
        final ArrayData array = getArray();

        if (array.has(index)) {
            if (array.canDelete(index, strict)) {
                setArray(array.delete(index));
                return true;
            }
            return false;
        }

        return deleteObject(JSType.toObject(key), strict);
    }

    @Override
    public boolean delete(final Object key, final boolean strict) {
        final int index = getArrayIndexNoThrow(key);
        final ArrayData array = getArray();

        if (array.has(index)) {
            if (array.canDelete(index, strict)) {
                setArray(array.delete(index));
                return true;
            }
            return false;
        }

        return deleteObject(key, strict);
    }

    private boolean deleteObject(final Object key, final boolean strict) {
        final String propName = convertKey(key);
        final FindProperty find = findProperty(propName, false);

        if (find == null) {
            return true;
        }

        if (!find.isConfigurable()) {
            if (strict) {
                typeError("cant.delete.property", propName, ScriptRuntime.safeToString(this));
            }
            return false;
        }

        final Property prop = find.getProperty();
        notifyPropertyDeleted(this, prop);
        deleteOwnProperty(prop);

        return true;
    }

    /*
     * Embed management
     */

    /** Number of embed slots */
    public static final int EMBED_SIZE   = 4;
    /** Embed offset */
    public static final int EMBED_OFFSET = 32 - EMBED_SIZE;

    static final MethodHandle[] GET_EMBED;
    static final MethodHandle[] SET_EMBED;

    static {
        GET_EMBED = new MethodHandle[EMBED_SIZE];
        SET_EMBED = new MethodHandle[EMBED_SIZE];

        for (int i = 0; i < EMBED_SIZE; i++) {
            final String name = "embed" + i;
            GET_EMBED[i] = MH.asType(MH.getter(MethodHandles.lookup(), ScriptObject.class, name, Object.class), Lookup.GET_OBJECT_TYPE);
            SET_EMBED[i] = MH.asType(MH.setter(MethodHandles.lookup(), ScriptObject.class, name, Object.class), Lookup.SET_OBJECT_TYPE);
        }
    }

    void useEmbed(final int i) {
        flags |= 1 << (EMBED_OFFSET + i);
    }

    int findEmbed() {
        final int bits  = ~(flags >>> EMBED_OFFSET);
        final int least = bits ^ -bits;
        final int index = Integer.numberOfTrailingZeros(least) - 1;

        return index;
    }

    /*
     * Make a new UserAccessorProperty property. getter and setter functions are stored in
     * this ScriptObject and slot values are used in property object.
     */
    private UserAccessorProperty newUserAccessors(final String key, final int propertyFlags, final ScriptFunction getter, final ScriptFunction setter) {
        int oldSpillLength = getMap().getSpillLength();

        int getterSlot = findEmbed();
        if (getterSlot >= EMBED_SIZE) {
            getterSlot = oldSpillLength + EMBED_SIZE;
            ++oldSpillLength;
        } else {
            useEmbed(getterSlot);
        }
        setEmbedOrSpill(getterSlot, getter);
        // if getter function is null, flag the slot to be negative (less by 1)
        if (getter == null) {
            getterSlot = -getterSlot - 1;
        }

        int setterSlot = findEmbed();
        if (setterSlot >= EMBED_SIZE) {
            setterSlot = oldSpillLength + EMBED_SIZE;
        } else {
            useEmbed(setterSlot);
        }
        setEmbedOrSpill(setterSlot, setter);
        // if setter function is null, flag the slot to be negative (less by 1)
        if (setter == null) {
            setterSlot = -setterSlot - 1;
        }

        return new UserAccessorProperty(key, propertyFlags, getterSlot, setterSlot);
    }

    private void setEmbedOrSpill(final int slot, final Object value) {
        switch (slot) {
        case 0:
            embed0 = value;
            break;
        case 1:
            embed1 = value;
            break;
        case 2:
            embed2 = value;
            break;
        case 3:
            embed3 = value;
            break;
        default:
            if (slot >= 0) {
                final int index = (slot - EMBED_SIZE);
                if (spill == null) {
                    // create new spill.
                    spill = new Object[Math.max(index + 1, SPILL_RATE)];
                } else if (index >= spill.length) {
                    // grow spill as needed
                    final Object[] newSpill = new Object[index + 1];
                    System.arraycopy(spill, 0, newSpill, 0, spill.length);
                    spill = newSpill;
                }

                spill[index] = value;
            }
            break;
        }
    }

    // user accessors are either stored in embed fields or spill array slots
    // get the accessor value using slot number. Note that slot is either embed
    // field number or (spill array index + embedSize).
    Object getEmbedOrSpill(final int slot) {
        switch (slot) {
        case 0:
            return embed0;
        case 1:
            return embed1;
        case 2:
            return embed2;
        case 3:
            return embed3;
        default:
            final int index = (slot - EMBED_SIZE);
            return (index < 0 || (index >= spill.length)) ? null : spill[index];
        }
    }

    // User defined getter and setter are always called by "dyn:call". Note that the user
    // getter/setter may be inherited. If so, proto is bound during lookup. In either
    // inherited or self case, slot is also bound during lookup. Actual ScriptFunction
    // to be called is retrieved everytime and applied.
    @SuppressWarnings("unused")
    private static Object userAccessorGetter(final ScriptObject proto, final int slot, final Object self) {
        final ScriptObject container = (proto != null) ? proto : (ScriptObject)self;
        final Object       func      = container.getEmbedOrSpill(slot);

        if (func instanceof ScriptFunction) {
            try {
                return INVOKE_UA_GETTER.invokeExact(func, self);
            } catch(final Error|RuntimeException t) {
                throw t;
            } catch(final Throwable t) {
                throw new RuntimeException(t);
            }
        }

        return UNDEFINED;
    }

    @SuppressWarnings("unused")
    private static void userAccessorSetter(final ScriptObject proto, final int slot, final String name, final Object self, final Object value) {
        final ScriptObject container = (proto != null) ? proto : (ScriptObject)self;
        final Object       func      = container.getEmbedOrSpill(slot);

        if (func instanceof ScriptFunction) {
            try {
                INVOKE_UA_SETTER.invokeExact(func, self, value);
            } catch(final Error|RuntimeException t) {
                throw t;
            } catch(final Throwable t) {
                throw new RuntimeException(t);
            }
        }  else if (name != null) {
            typeError("property.has.no.setter", name, ScriptRuntime.safeToString(self));
        }
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        final Class<?>   own = ScriptObject.class;
        final MethodType mt  = MH.type(rtype, types);
        try {
            return MH.findStatic(MethodHandles.lookup(), own, name, mt);
        } catch (final MethodHandleFactory.LookupException e) {
            return MH.findVirtual(MethodHandles.lookup(), own, name, mt);
        }
    }

    private static MethodHandle getKnownFunctionPropertyGuard(final PropertyMap map, final MethodHandle getter, final Object where, final ScriptFunction func) {
        return MH.insertArguments(KNOWNFUNCPROPGUARD, 1, map, getter, where, func);
    }

    @SuppressWarnings("unused")
    private static boolean knownFunctionPropertyGuard(final Object self, final PropertyMap map, final MethodHandle getter, final Object where, final ScriptFunction func) {
        if (self instanceof ScriptObject && ((ScriptObject)self).getMap() == map) {
            try {
                return getter.invokeExact(where) == func;
            } catch (final RuntimeException | Error e) {
                throw e;
            } catch (final Throwable t) {
                throw new RuntimeException(t);
            }
        }

        return false;
    }

    /** This is updated only in debug mode - counts number of {@code ScriptObject} instances created */
    protected static int count;

    /** This is updated only in debug mode - counts number of {@code ScriptObject} instances created that are scope */
    protected static int scopeCount;

    /**
     * Get number of {@code ScriptObject} instances created. If not running in debug
     * mode this is always 0
     *
     * @return number of ScriptObjects created
     */
    public static int getCount() {
        return count;
    }

    /**
     * Get number of scope {@code ScriptObject} instances created. If not running in debug
     * mode this is always 0
     *
     * @return number of scope ScriptObjects created
     */
    public static int getScopeCount() {
        return scopeCount;
    }

}
