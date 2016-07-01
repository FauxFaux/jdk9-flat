/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jlink.internal;

import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.jimage.decompressor.Decompressor;
import jdk.tools.jlink.plugin.Plugin;
import jdk.tools.jlink.builder.ImageBuilder;
import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.plugin.ModulePool;
import jdk.tools.jlink.plugin.LinkModule;
import jdk.tools.jlink.plugin.ModuleEntry;

/**
 * Plugins Stack. Plugins entry point to apply transformations onto resources
 * and files.
 */
public final class ImagePluginStack {

    public interface ImageProvider {

        ExecutableImage retrieve(ImagePluginStack stack) throws IOException;
    }

    public static final class OrderedResourcePool extends ModulePoolImpl {

        private final List<ModuleEntry> orderedList = new ArrayList<>();

        public OrderedResourcePool(ByteOrder order, StringTable table) {
            super(order, table);
        }

        /**
         * Add a resource.
         *
         * @param resource The Resource to add.
         */
        @Override
        public void add(ModuleEntry resource) {
            super.add(resource);
            orderedList.add(resource);
        }

        List<ModuleEntry> getOrderedList() {
            return Collections.unmodifiableList(orderedList);
        }
    }

    private final static class CheckOrderResourcePool extends ModulePoolImpl {

        private final List<ModuleEntry> orderedList;
        private int currentIndex;

        public CheckOrderResourcePool(ByteOrder order, List<ModuleEntry> orderedList, StringTable table) {
            super(order, table);
            this.orderedList = Objects.requireNonNull(orderedList);
        }

        /**
         * Add a resource.
         *
         * @param resource The Resource to add.
         */
        @Override
        public void add(ModuleEntry resource) {
            ModuleEntry ordered = orderedList.get(currentIndex);
            if (!resource.equals(ordered)) {
                throw new PluginException("Resource " + resource.getPath() + " not in the right order");
            }
            super.add(resource);
            currentIndex += 1;
        }
    }

    private static final class PreVisitStrings implements StringTable {

        private int currentid = 0;
        private final Map<String, Integer> stringsUsage = new HashMap<>();
        private final Map<String, Integer> stringsMap = new HashMap<>();
        private final Map<Integer, String> reverseMap = new HashMap<>();

        @Override
        public int addString(String str) {
            Objects.requireNonNull(str);
            Integer count = stringsUsage.get(str);
            if (count == null) {
                count = 0;
            }
            count += 1;
            stringsUsage.put(str, count);
            Integer id = stringsMap.get(str);
            if (id == null) {
                id = currentid;
                stringsMap.put(str, id);
                currentid += 1;
                reverseMap.put(id, str);
            }

            return id;
        }

        private List<String> getSortedStrings() {
            Stream<java.util.Map.Entry<String, Integer>> stream
                    = stringsUsage.entrySet().stream();
            // Remove strings that have a single occurence
            List<String> result = stream.sorted(Comparator.comparing(e -> e.getValue(),
                    Comparator.reverseOrder())).filter((e) -> {
                        return e.getValue() > 1;
                    }).map(java.util.Map.Entry::getKey).
                    collect(Collectors.toList());
            return result;
        }

        @Override
        public String getString(int id) {
            return reverseMap.get(id);
        }
    }

    private final ImageBuilder imageBuilder;
    private final Plugin lastSorter;
    private final List<Plugin> plugins = new ArrayList<>();
    private final List<ResourcePrevisitor> resourcePrevisitors = new ArrayList<>();


    public ImagePluginStack() {
        this(null, Collections.emptyList(), null);
    }

    public ImagePluginStack(ImageBuilder imageBuilder,
            List<Plugin> plugins,
            Plugin lastSorter) {
        this.imageBuilder = Objects.requireNonNull(imageBuilder);
        this.lastSorter = lastSorter;
        this.plugins.addAll(Objects.requireNonNull(plugins));
        plugins.stream().forEach((p) -> {
            Objects.requireNonNull(p);
            if (p instanceof ResourcePrevisitor) {
                resourcePrevisitors.add((ResourcePrevisitor) p);
            }
        });
    }

    public void operate(ImageProvider provider) throws Exception {
        ExecutableImage img = provider.retrieve(this);
        List<String> arguments = new ArrayList<>();
        plugins.stream()
                .filter(PostProcessor.class::isInstance)
                .map((plugin) -> ((PostProcessor)plugin).process(img))
                .filter((lst) -> (lst != null))
                .forEach((lst) -> {
                     arguments.addAll(lst);
                });
        img.storeLaunchArgs(arguments);
    }

    public DataOutputStream getJImageFileOutputStream() throws IOException {
        return imageBuilder.getJImageOutputStream();
    }

    public ImageBuilder getImageBuilder() {
        return imageBuilder;
    }

    /**
     * Resource Plugins stack entry point. All resources are going through all
     * the plugins.
     *
     * @param resources The set of resources to visit
     * @return The result of the visit.
     * @throws IOException
     */
    public ModulePoolImpl visitResources(ModulePoolImpl resources)
            throws Exception {
        Objects.requireNonNull(resources);
        resources.setReadOnly();
        if (resources.isEmpty()) {
            return new ModulePoolImpl(resources.getByteOrder(),
                    resources.getStringTable());
        }
        PreVisitStrings previsit = new PreVisitStrings();
        resourcePrevisitors.stream().forEach((p) -> {
            p.previsit(resources, previsit);
        });

        // Store the strings resulting from the previsit.
        List<String> sorted = previsit.getSortedStrings();
        sorted.stream().forEach((s) -> {
            resources.getStringTable().addString(s);
        });

        ModulePoolImpl current = resources;
        List<ModuleEntry> frozenOrder = null;
        for (Plugin p : plugins) {
            current.setReadOnly();
            ModulePoolImpl output = null;
            if (p == lastSorter) {
                if (frozenOrder != null) {
                    throw new Exception("Order of resources is already frozen. Plugin "
                            + p.getName() + " is badly located");
                }
                // Create a special Resource pool to compute the indexes.
                output = new OrderedResourcePool(current.getByteOrder(),
                        resources.getStringTable());
            } else {// If we have an order, inject it
                if (frozenOrder != null) {
                    output = new CheckOrderResourcePool(current.getByteOrder(),
                            frozenOrder, resources.getStringTable());
                } else {
                    output = new ModulePoolImpl(current.getByteOrder(),
                            resources.getStringTable());
                }
            }
            p.visit(current, output);
            if (output.isEmpty()) {
                throw new Exception("Invalid resource pool for plugin " + p);
            }
            if (output instanceof OrderedResourcePool) {
                frozenOrder = ((OrderedResourcePool) output).getOrderedList();
            }

            current = output;
        }
        current.setReadOnly();
        return current;
    }

    /**
     * This pool wrap the original pool and automatically uncompress ModuleEntry
     * if needed.
     */
    private class LastPool implements ModulePool {
        private class LastModule implements LinkModule {

            final LinkModule module;

            LastModule(LinkModule module) {
                this.module = module;
            }

            @Override
            public String getName() {
                return module.getName();
            }

            @Override
            public Optional<ModuleEntry> findEntry(String path) {
                Optional<ModuleEntry> d = module.findEntry(path);
                return d.isPresent()? Optional.of(getUncompressed(d.get())) : Optional.empty();
            }

            @Override
            public ModuleDescriptor getDescriptor() {
                return module.getDescriptor();
            }

            @Override
            public void add(ModuleEntry data) {
                throw new PluginException("pool is readonly");
            }

            @Override
            public Set<String> getAllPackages() {
                return module.getAllPackages();
            }

            @Override
            public String toString() {
                return getName();
            }

            @Override
            public Stream<ModuleEntry> entries() {
                List<ModuleEntry> lst = new ArrayList<>();
                module.entries().forEach(md -> {
                    lst.add(getUncompressed(md));
                });
                return lst.stream();
            }

            @Override
            public int getEntryCount() {
                return module.getEntryCount();
            }
        }
        private final ModulePoolImpl pool;
        Decompressor decompressor = new Decompressor();
        Collection<ModuleEntry> content;

        LastPool(ModulePoolImpl pool) {
            this.pool = pool;
        }

        @Override
        public boolean isReadOnly() {
            return true;
        }

        @Override
        public void add(ModuleEntry resource) {
            throw new PluginException("pool is readonly");
        }

        @Override
        public Optional<LinkModule> findModule(String name) {
            Optional<LinkModule> module = pool.findModule(name);
            return module.isPresent()? Optional.of(new LastModule(module.get())) : Optional.empty();
        }

        /**
         * The collection of modules contained in this pool.
         *
         * @return The collection of modules.
         */
        @Override
        public Stream<? extends LinkModule> modules() {
            List<LinkModule> modules = new ArrayList<>();
            pool.modules().forEach(m -> {
                modules.add(new LastModule(m));
            });
            return modules.stream();
        }

        @Override
        public int getModuleCount() {
            return pool.getModuleCount();
        }

        /**
         * Get all resources contained in this pool instance.
         *
         * @return The stream of resources;
         */
        @Override
        public Stream<? extends ModuleEntry> entries() {
            if (content == null) {
                content = new ArrayList<>();
                pool.entries().forEach(md -> {
                    content.add(getUncompressed(md));
                });
            }
            return content.stream();
        }

        @Override
        public int getEntryCount() {
            return pool.getEntryCount();
        }

        /**
         * Get the resource for the passed path.
         *
         * @param path A resource path
         * @return A Resource instance if the resource is found
         */
        @Override
        public Optional<ModuleEntry> findEntry(String path) {
            Objects.requireNonNull(path);
            Optional<ModuleEntry> res = pool.findEntry(path);
            return res.isPresent()? Optional.of(getUncompressed(res.get())) : Optional.empty();
        }

        @Override
        public boolean contains(ModuleEntry res) {
            return pool.contains(res);
        }

        @Override
        public boolean isEmpty() {
            return pool.isEmpty();
        }

        @Override
        public void transformAndCopy(Function<ModuleEntry, ModuleEntry> visitor, ModulePool output) {
            pool.transformAndCopy(visitor, output);
        }

        @Override
        public ByteOrder getByteOrder() {
            return pool.getByteOrder();
        }

        @Override
        public Map<String, String> getReleaseProperties() {
            return Collections.unmodifiableMap(pool.getReleaseProperties());
        }

        private ModuleEntry getUncompressed(ModuleEntry res) {
            if (res != null) {
                if (res instanceof ModulePoolImpl.CompressedModuleData) {
                    try {
                        byte[] bytes = decompressor.decompressResource(getByteOrder(),
                                (int offset) -> pool.getStringTable().getString(offset),
                                res.getBytes());
                        res = res.create(bytes);
                    } catch (IOException ex) {
                        throw new PluginException(ex);
                    }
                }
            }
            return res;
        }
    }

    /**
     * Make the imageBuilder to store files.
     *
     * @param original
     * @param transformed
     * @param writer
     * @throws java.lang.Exception
     */
    public void storeFiles(ModulePoolImpl original, ModulePoolImpl transformed,
            BasicImageWriter writer)
            throws Exception {
        Objects.requireNonNull(original);
        Objects.requireNonNull(transformed);
        Optional<LinkModule> javaBase = transformed.findModule("java.base");
        javaBase.ifPresent(mod -> {
            try {
                Map<String, String> release = transformed.getReleaseProperties();
                // fill release information available from transformed "java.base" module!
                ModuleDescriptor desc = mod.getDescriptor();
                desc.osName().ifPresent(s -> release.put("OS_NAME", s));
                desc.osVersion().ifPresent(s -> release.put("OS_VERSION", s));
                desc.osArch().ifPresent(s -> release.put("OS_ARCH", s));
            } catch (Exception ignored) {}
        });

        imageBuilder.storeFiles(new LastPool(transformed));
    }

    public ExecutableImage getExecutableImage() throws IOException {
        return imageBuilder.getExecutableImage();
    }
}
