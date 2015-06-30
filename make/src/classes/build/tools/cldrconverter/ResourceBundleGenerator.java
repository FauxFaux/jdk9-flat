/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.cldrconverter;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Formatter;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;

class ResourceBundleGenerator implements BundleGenerator {
    // preferred timezones - keeping compatibility with JDK1.1 3 letter abbreviations
    private static final String[] preferredTZIDs = {
        "America/Los_Angeles",
        "America/Denver",
        "America/Phoenix",
        "America/Chicago",
        "America/New_York",
        "America/Indianapolis",
        "Pacific/Honolulu",
        "America/Anchorage",
        "America/Halifax",
        "America/Sitka",
        "America/St_Johns",
        "Europe/Paris",
        // Although CLDR does not support abbreviated zones, handle "GMT" as a
        // special case here, as it is specified in the javadoc.
        "GMT",
        "Africa/Casablanca",
        "Asia/Jerusalem",
        "Asia/Tokyo",
        "Europe/Bucharest",
        "Asia/Shanghai",
    };

    // For duplicated values
    private static final String META_VALUE_PREFIX = "metaValue_";

    @Override
    public void generateBundle(String packageName, String baseName, String localeID, boolean useJava,
                               Map<String, ?> map, BundleType type) throws IOException {
        String suffix = useJava ? ".java" : ".properties";
        String lang = CLDRConverter.getLanguageCode(localeID);
        String ctry = CLDRConverter.getCountryCode(localeID);
        String dirName = CLDRConverter.DESTINATION_DIR + File.separator + "sun" + File.separator
                + packageName + File.separator + "resources" + File.separator + "cldr";
        if (lang.length() > 0) {
            if (CLDRConverter.isBaseModule ^ isBaseLocale(localeID)) {
                return;
            }
            dirName = dirName + File.separator + lang +
                      (ctry != null && ctry.length() > 0 ? File.separator + ctry : "");
            packageName = packageName + ".resources.cldr." + lang +
                      (ctry != null && ctry.length() > 0 ? "." + ctry : "");
        } else {
            if (!CLDRConverter.isBaseModule) {
                return;
            }
            packageName = packageName + ".resources.cldr";
        }
        File dir = new File(dirName);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, baseName + ("root".equals(localeID) ? "" : "_" + localeID) + suffix);
        if (!file.exists()) {
            file.createNewFile();
        }
        CLDRConverter.info("\tWriting file " + file);

        String encoding;
        if (useJava) {
            if (CLDRConverter.USE_UTF8) {
                encoding = "utf-8";
            } else {
                encoding = "us-ascii";
            }
        } else {
            encoding = "iso-8859-1";
        }

        Formatter fmt = null;
        if (type == BundleType.TIMEZONE) {
            fmt = new Formatter();
            Set<String> metaKeys = new HashSet<>();
            for (String key : map.keySet()) {
                if (key.startsWith(CLDRConverter.METAZONE_ID_PREFIX)) {
                    String meta = key.substring(CLDRConverter.METAZONE_ID_PREFIX.length());
                    String[] value;
                    value = (String[]) map.get(key);
                    fmt.format("        final String[] %s = new String[] {\n", meta);
                    for (String s : value) {
                        fmt.format("               \"%s\",\n", CLDRConverter.saveConvert(s, useJava));
                    }
                    fmt.format("            };\n");
                    metaKeys.add(key);
                }
            }
            for (String key : metaKeys) {
                map.remove(key);
            }

            // Make it preferred ordered
            LinkedHashMap<String, Object> newMap = new LinkedHashMap<>();
            for (String preferred : preferredTZIDs) {
                if (map.containsKey(preferred)) {
                    newMap.put(preferred, map.remove(preferred));
                } else if ("GMT".equals(preferred) &&
                           metaKeys.contains(CLDRConverter.METAZONE_ID_PREFIX+preferred)) {
                    newMap.put(preferred, preferred);
                }
            }
            newMap.putAll(map);
            map = newMap;
        } else {
            // generic reduction of duplicated values
            Map<String, Object> newMap = null;
            for (String key : map.keySet()) {
                Object val = map.get(key);
                String metaVal = null;

                for (Map.Entry<String, ?> entry : map.entrySet()) {
                    String k = entry.getKey();
                    if (!k.equals(key) &&
                        Objects.deepEquals(val, entry.getValue()) &&
                        !(Objects.nonNull(newMap) && newMap.containsKey(k))) {
                        if (Objects.isNull(newMap)) {
                            newMap = new HashMap<>();
                            fmt = new Formatter();
                        }

                        if (Objects.isNull(metaVal)) {
                            metaVal = META_VALUE_PREFIX + key.replaceAll("\\.", "_");

                            if (val instanceof String[]) {
                                fmt.format("        final String[] %s = new String[] {\n", metaVal);
                                for (String s : (String[])val) {
                                    fmt.format("               \"%s\",\n", CLDRConverter.saveConvert(s, useJava));
                                }
                                fmt.format("            };\n");
                            } else {
                                fmt.format("        final String %s = \"%s\";\n", metaVal, CLDRConverter.saveConvert((String)val, useJava));
                            }
                        }

                        newMap.put(k, metaVal);
                    }
                }

                if (Objects.nonNull(metaVal)) {
                    newMap.put(key, metaVal);
                }
            }

            if (Objects.nonNull(newMap)) {
                for (String key : map.keySet()) {
                    newMap.putIfAbsent(key, map.get(key));
                }
                map = newMap;
            }
        }

        try (PrintWriter out = new PrintWriter(file, encoding)) {
            // Output copyright headers
            out.println(CopyrightHeaders.getOpenJDKCopyright());
            out.println(CopyrightHeaders.getUnicodeCopyright());

            if (useJava) {
                out.println("package sun." + packageName + ";\n");
                out.printf("import %s;\n\n", type.getPathName());
                out.printf("public class %s%s extends %s {\n", baseName, "root".equals(localeID) ? "" : "_" + localeID, type.getClassName());

                out.println("    @Override\n" +
                            "    protected final Object[][] getContents() {");
                if (fmt != null) {
                    out.print(fmt.toString());
                }
                out.println("        final Object[][] data = new Object[][] {");
            }
            for (String key : map.keySet()) {
                if (useJava) {
                    Object value = map.get(key);
                    if (value == null) {
                        CLDRConverter.warning("null value for " + key);
                    } else if (value instanceof String) {
                        if (type == BundleType.TIMEZONE ||
                            ((String)value).startsWith(META_VALUE_PREFIX)) {
                            out.printf("            { \"%s\", %s },\n", key, CLDRConverter.saveConvert((String) value, useJava));
                        } else {
                            out.printf("            { \"%s\", \"%s\" },\n", key, CLDRConverter.saveConvert((String) value, useJava));
                        }
                    } else if (value instanceof String[]) {
                        String[] values = (String[]) value;
                        out.println("            { \"" + key + "\",\n                new String[] {");
                        for (String s : values) {
                            out.println("                    \"" + CLDRConverter.saveConvert(s, useJava) + "\",");
                        }
                        out.println("                }\n            },");
                    } else {
                        throw new RuntimeException("unknown value type: " + value.getClass().getName());
                    }
                } else {
                    out.println(key + "=" + CLDRConverter.saveConvert((String) map.get(key), useJava));
                }
            }
            if (useJava) {
                out.println("        };\n        return data;\n    }\n}");
            }
        }
    }

    @Override
    public void generateMetaInfo(Map<String, SortedSet<String>> metaInfo) throws IOException {
        String dirName = CLDRConverter.DESTINATION_DIR + File.separator + "sun" + File.separator + "util" +
            File.separator +
            (CLDRConverter.isBaseModule ? "cldr" + File.separator + File.separator :
                      "resources" + File.separator + "cldr" + File.separator + "provider" + File.separator);
        File dir = new File(dirName);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String className =
            (CLDRConverter.isBaseModule ? "CLDRBaseLocaleDataMetaInfo" :
                "CLDRLocaleDataMetaInfo_" +
                CLDRConverter.DESTINATION_DIR.substring(CLDRConverter.DESTINATION_DIR.lastIndexOf('/')+1)
                    .replaceAll("\\.", "_"));
        File file = new File(dir, className + ".java");
        if (!file.exists()) {
            file.createNewFile();
        }
        CLDRConverter.info("Generating file " + file);

        try (PrintWriter out = new PrintWriter(file, "us-ascii")) {
            out.println(CopyrightHeaders.getOpenJDKCopyright());

            out.println((CLDRConverter.isBaseModule ? "package sun.util.cldr;\n\n" :
                                  "package sun.util.resources.cldr.provider;\n\n")
                      + "import java.util.HashMap;\n"
                      + "import java.util.Map;\n"
                      + "import java.util.ListResourceBundle;\n"
                      + "import sun.util.locale.provider.LocaleProviderAdapter;\n"
                      + "import sun.util.locale.provider.LocaleDataMetaInfo;\n");
            out.printf("public class %s extends ListResourceBundle implements LocaleDataMetaInfo {\n", className);
            out.println("    @Override\n" +
                        "    protected final Object[][] getContents() {\n" +
                        "        final Object[][] data = new Object[][] {");
            for (String key : metaInfo.keySet()) {
                out.printf("            { \"%s\",\n", key);
                out.printf("              \"%s\" },\n",
                    toLocaleList(key.equals("FormatData") ? metaInfo.get("AvailableLocales") :
                                                            metaInfo.get(key),
                                 key.startsWith(CLDRConverter.PARENT_LOCALE_PREFIX)));
            }
            out.println("        };\n        return data;\n    }\n\n");

            out.println("    @Override\n" +
                        "    public LocaleProviderAdapter.Type getType() {\n" +
                        "        return LocaleProviderAdapter.Type.CLDR;\n" +
                        "    }\n\n");

            out.println("    @Override\n" +
                        "    public String availableLanguageTags(String category) {\n" +
                        "        return getString(category);\n" +
                        "    };\n\n");

            if (CLDRConverter.isBaseModule) {
                out.printf("    public Map<String, String> parentLocales() {\n" +
                           "        Map<String, String> ret = new HashMap<>();\n" +
                           "        keySet().stream()\n" +
                           "            .filter(key -> key.startsWith(\"%s\"))\n" +
                           "            .forEach(key -> ret.put(key.substring(%d), getString(key)));\n" +
                           "        return ret.isEmpty() ? null : ret;\n" +
                           "    };\n}",
                           CLDRConverter.PARENT_LOCALE_PREFIX,
                           CLDRConverter.PARENT_LOCALE_PREFIX.length());
            } else {
                out.println("}");
            }
        }
    }

    private static final Locale.Builder LOCALE_BUILDER = new Locale.Builder();
    private static boolean isBaseLocale(String localeID) {
        localeID = localeID.replaceAll("-", "_");
        // ignore script here
        Locale locale = LOCALE_BUILDER
                            .clear()
                            .setLanguage(CLDRConverter.getLanguageCode(localeID))
                            .setRegion(CLDRConverter.getCountryCode(localeID))
                            .build();
        return CLDRConverter.BASE_LOCALES.contains(locale);
    }

    private static String toLocaleList(SortedSet<String> set, boolean all) {
        StringBuilder sb = new StringBuilder(set.size() * 6);
        for (String id : set) {
            if (!"root".equals(id)) {
                if (!all && CLDRConverter.isBaseModule ^ isBaseLocale(id)) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(id);
            }
        }
        return sb.toString();
    }
}
