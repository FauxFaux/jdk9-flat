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

package jdk.nashorn.api.scripting;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import netscape.javascript.JSObject;

/**
 * Mirror object that wraps a given ScriptObject instance. User can
 * access ScriptObject via the java.util.Map interface.
 */
final class ScriptObjectMirror extends JSObject implements Map<Object, Object> {
    private final ScriptObject sobj;
    private final ScriptObject global;

    ScriptObjectMirror(final ScriptObject sobj, final ScriptObject global) {
        this.sobj = sobj;
        this.global = global;
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof ScriptObjectMirror) {
            return sobj.equals(((ScriptObjectMirror)other).sobj);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return sobj.hashCode();
    }

    private <V> V inGlobal(final Callable<V> callable) {
        final ScriptObject oldGlobal = Context.getGlobal();
        final boolean globalChanged = (oldGlobal != global);
        if (globalChanged) {
            NashornScriptEngine.setNashornGlobal(global);
        }
        try {
            return callable.call();
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new AssertionError("Cannot happen", e);
        } finally {
            if (globalChanged) {
                NashornScriptEngine.setNashornGlobal(oldGlobal);
            }
        }
    }

    // JSObject methods
    @Override
    public Object call(final String methodName, final Object args[]) {
        final Object val = sobj.get(methodName);
        final ScriptObject oldGlobal = Context.getGlobal();
        final boolean globalChanged = (oldGlobal != global);

        if (val instanceof ScriptFunction) {
            final Object[] modifiedArgs = unwrapArray(args, global);
            if (modifiedArgs != null) {
                for (int i = 0; i < modifiedArgs.length; i++) {
                    final Object arg = modifiedArgs[i];
                    if (arg instanceof ScriptObject) {
                        modifiedArgs[i] = wrap(arg, oldGlobal);
                    }
                }
            }

            try {
                if (globalChanged) {
                    NashornScriptEngine.setNashornGlobal(global);
                }
                return wrap(((ScriptFunction)val).invoke(sobj, modifiedArgs), global);
            } catch (final RuntimeException | Error e) {
                throw e;
            } catch (final Throwable t) {
                throw new RuntimeException(t);
            } finally {
                if (globalChanged) {
                    NashornScriptEngine.setNashornGlobal(oldGlobal);
                }
            }
       }

       throw new RuntimeException("No such method: " + methodName);
    }

    @Override
    public Object eval(final String s) {
        return inGlobal(new Callable<Object>() {
            @Override
            public Object call() {
                return wrap(global.getContext().eval(global, s, null, null, false), global);
            }
        });
    }

    @Override
    public Object getMember(final String name) {
        return get(name);
    }

    @Override
    public Object getSlot(final int index) {
        return get(Integer.valueOf(index));
    }

    @Override
    public void removeMember(final String name) {
        remove(name);
    }

    @Override
    public void setMember(final String name, final Object value) {
        put(name, wrap(value, Context.getGlobal()));
    }

    @Override
    public void setSlot(final int index, final Object value) {
        put(Integer.valueOf(index), wrap(value, Context.getGlobal()));
    }

    @Override
    public void clear() {
        inGlobal(new Callable<Object>() {
            @Override public Object call() {
                sobj.clear();
                return null;
            }});
    }

    @Override
    public boolean containsKey(final Object key) {
        return inGlobal(new Callable<Boolean>() {
            @Override public Boolean call() {
                return sobj.containsKey(unwrap(key, global));
            }});
    }

    @Override
    public boolean containsValue(final Object value) {
        return inGlobal(new Callable<Boolean>() {
            @Override public Boolean call() {
                return sobj.containsValue(unwrap(value, global));
            }});
    }

    @Override
    public Set<Map.Entry<Object, Object>> entrySet() {
        return inGlobal(new Callable<Set<Map.Entry<Object, Object>>>() {
            @Override public Set<Map.Entry<Object, Object>> call() {
                final Iterator<String>               iter    = sobj.propertyIterator();
                final Set<Map.Entry<Object, Object>> entries = new HashSet<>();

                while (iter.hasNext()) {
                    final Object key   = wrap(iter.next(), global);
                    final Object value = wrap(sobj.get(key), global);
                    entries.add(new AbstractMap.SimpleImmutableEntry<>(key, value));
                }

                return Collections.unmodifiableSet(entries);
            }
        });
    }

    @Override
    public Object get(final Object key) {
        return inGlobal(new Callable<Object>() { @Override public Object call() {
            return wrap(sobj.get(key), global);
        }});
    }

    @Override
    public boolean isEmpty() {
        return inGlobal(new Callable<Boolean>() { @Override public Boolean call() {
            return sobj.isEmpty();
        }});
    }

    @Override
    public Set<Object> keySet() {
        return inGlobal(new Callable<Set<Object>>() { @Override public Set<Object> call() {
            final Iterator<String> iter   = sobj.propertyIterator();
            final Set<Object>      keySet = new HashSet<>();

            while (iter.hasNext()) {
                keySet.add(wrap(iter.next(), global));
            }

            return Collections.unmodifiableSet(keySet);
        }});
    }

    @Override
    public Object put(final Object key, final Object value) {
        return inGlobal(new Callable<Object>() {
            @Override public Object call() {
                return sobj.put(unwrap(key, global), unwrap(value, global));
        }});
    }

    @Override
    public void putAll(final Map<?, ?> map) {
        final boolean strict = sobj.getContext()._strict;
        inGlobal(new Callable<Object>() { @Override public Object call() {
            for (final Map.Entry<?, ?> entry : map.entrySet()) {
                sobj.set(unwrap(entry.getKey(), global), unwrap(entry.getValue(), global), strict);
            }
            return null;
        }});
    }

    @Override
    public Object remove(final Object key) {
        return inGlobal(new Callable<Object>() {
            @Override public Object call() {
                return wrap(sobj.remove(unwrap(key, global)), global);
            }
        });
    }

    @Override
    public int size() {
        return inGlobal(new Callable<Integer>() {
            @Override public Integer call() {
                return sobj.size();
            }
        });
    }

    @Override
    public Collection<Object> values() {
        return inGlobal(new Callable<Collection<Object>>() { @Override public Collection<Object> call() {
            final List<Object>     values = new ArrayList<>(size());
            final Iterator<Object> iter   = sobj.valueIterator();

            while (iter.hasNext()) {
                values.add(wrap(iter.next(), global));
            }

            return Collections.unmodifiableList(values);
        }});
    }

    static Object wrap(final Object obj, final ScriptObject homeGlobal) {
        return (obj instanceof ScriptObject) ? new ScriptObjectMirror((ScriptObject)obj, homeGlobal) : obj;
    }

    static Object unwrap(final Object obj, final ScriptObject homeGlobal) {
        if (obj instanceof ScriptObjectMirror) {
            final ScriptObjectMirror mirror = (ScriptObjectMirror)obj;
            return (mirror.global == homeGlobal)? mirror.sobj : obj;
        }

        return obj;
    }

    static Object[] wrapArray(final Object[] args, final ScriptObject homeGlobal) {
        if (args == null || args.length == 0) {
            return args;
        }

        final Object[] newArgs = new Object[args.length];
        int index = 0;
        for (final Object obj : args) {
            newArgs[index] = wrap(obj, homeGlobal);
            index++;
        }
        return newArgs;
    }

    static Object[] unwrapArray(final Object[] args, final ScriptObject homeGlobal) {
        if (args == null || args.length == 0) {
            return args;
        }

        final Object[] newArgs = new Object[args.length];
        int index = 0;
        for (final Object obj : args) {
            newArgs[index] = unwrap(obj, homeGlobal);
            index++;
        }
        return newArgs;
    }
}
