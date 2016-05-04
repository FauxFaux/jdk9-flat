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

/*
 * This file defines build profiles for the JIB tool and others.
 *
 * A build profile defines a set of configuration options and external
 * dependencies that we for some reason or other care about specifically.
 * Typically, build profiles are defined for the build configurations we
 * build regularly.
 *
 * Contract against this file from the tools that use it, is to provide
 * a function on the form:
 *
 * getJibProfiles(input)
 *
 * which returns an object graph describing the profiles and their
 * dependencies. The name of the function is based on the name of this
 * file, minus the extension and the '-', camel cased and prefixed with
 * 'get'.
 *
 *
 * The parameter 'input' is an object that optionally contains  some data.
 * Optionally because a tool may read the configuration for different purposes.
 * To initially get a list of available profiles, the active profile may not
 * yet be known for instance.
 *
 * Data that may be set on the input object:
 *
 * input.profile = <name of active profile>
 *
 * If the active profile is set, the following data from it must also
 * be provided:
 *
 * input.profile
 * input.target_os
 * input.target_cpu
 * input.build_os
 * input.build_cpu
 * input.target_platform
 * input.build_platform
 * // The build_osenv_* variables describe the unix layer on Windows systems,
 * // i.e. Cygwin, which may also be 32 or 64 bit.
 * input.build_osenv
 * input.build_osenv_cpu
 * input.build_osenv_platform
 *
 * For more complex nested attributes, there is a method "get":
 *
 * input.get("<dependency>", "<attribute>")
 *
 * Valid attributes are:
 * install_path
 * download_path
 * download_dir
 *
 *
 * The output data generated by this configuration file has the following
 * format:
 *
 * data: {
 *   // Identifies the version of this format to the tool reading it
 *   format_version: "1.0",
 *
 *   // Name of base outputdir. JIB assumes the actual output dir is formed
 *   // by adding the configuration name: <output_basedir>/<config-name>
 *   output_basedir: "build",
 *   // Configure argument to use to specify configuration name
 *   configuration_configure_arg:
 *   // Make argument to use to specify configuration name
 *   configuration_make_arg:
 *
 *   profiles: {
 *     <profile-name>: {
 *       // Name of os the profile is built to run on
 *       target_os; <string>
 *       // Name of cpu the profile is built to run on
 *       target_cpu; <string>
 *       // Combination of target_os and target_cpu for convenience
 *       target_platform; <string>
 *       // Name of os the profile is built on
 *       build_os; <string>
 *       // Name of cpu the profile is built on
 *       build_cpu; <string>
 *       // Combination of build_os and build_cpu for convenience
 *       build_platform; <string>
 *
 *       // List of dependencies needed to build this profile
 *       dependencies: <Array of strings>
 *
 *       // List of configure args to use for this profile
 *       configure_args: <Array of strings>
 *
 *       // List of free form labels describing aspects of this profile
 *       labels: <Array of strings>
 *     }
 *   }
 *
 *   // Dependencies use a Maven like deployment structure
 *   dependencies: {
 *     <dependency-name>: {
 *       // Organization part of path defining this dependency
 *       organization: <string>
 *       // File extension for this dependency
 *       ext: <string>
 *       // Module part of path for defining this dependency,
 *       // defaults to <dependency-name>
 *       module: <string>
 *       // Revision part of path for defining this dependency
 *       revision: <string>
 *
 *       // List of configure args to add when using this dependency,
 *       // defaults to
 *       // "--with-<dependency-name>=input.get("<dependency-name", "install_path")"
 *       configure_args: <array of strings>
 *
 *       // Name of environment variable to set when using this dependency
 *       // when running make
 *       environment_name: <string>
 *       // Value of environment variable to set when using this dependency
 *       // when running make
 *       environment_value: <string>
 *
 *       // Value to add to the PATH variable when using this dependency,
 *       // applies to both make and configure
 *       environment_path: <string>
 *     }
 *
 *     <dependency-name>: {
 *       // For certain dependencies where a legacy distribution mechanism is
 *       // already in place, the "javare" server layout is also supported
 *       // Indicate that an alternate server source and layout should be used
 *       server: "javare"
 *
 *       // For "javare", a combination of module, revision,
 *       // build number (optional), files and checksum file is possible for
 *       // artifacts following the standard layout.
 *       module: <string>
 *       revision: <string>
 *       build_number: <string>
 *       checksum_file: <string>
 *       file: <string>
 *
 *       // For other files, use checksum path and path instead
 *       checksum_path: <string>
 *       path: <string>
 *     }
 *   }
 * }
 */

/**
 * Main entry to generate the profile configuration
 *
 * @param input External data to use for generating the configuration
 * @returns {{}} Profile configuration
 */
var getJibProfiles = function (input) {

    var data = {};

    // Identifies the version of this format to the tool reading it
    data.format_version = "1.0";

    // Organization is used when uploading/publishing build results
    data.organization = "com.oracle.jpg.jdk";

    // The base directory for the build output. JIB will assume that the
    // actual build directory will be <output_basedir>/<configuration>
    data.output_basedir = "build";
    // The configure argument to use to specify the name of the configuration
    data.configuration_configure_arg = "--with-conf-name=";
    // The make argument to use to specify the name of the configuration
    data.configuration_make_arg = "CONF_NAME=";

    // Define some common values
    var common = getJibProfilesCommon(input);
    // Generate the profiles part of the configuration
    data.profiles = getJibProfilesProfiles(input, common);
    // Generate the dependencies part of the configuration
    data.dependencies = getJibProfilesDependencies(input, common);

    return data;
};

/**
 * Generates some common values
 *
 * @param input External data to use for generating the configuration
 * @returns Common values
 */
var getJibProfilesCommon = function (input) {
    var common = {};

    common.dependencies = ["boot_jdk", "gnumake", "jtreg"],
    common.default_make_targets = ["product-images", "test-image"],
    common.default_make_targets_debug = common.default_make_targets;
    common.default_make_targets_slowdebug = common.default_make_targets;
    common.configure_args = ["--enable-jtreg-failure-handler"],
    common.configure_args_32bit = ["--with-target-bits=32"],
    common.configure_args_debug = ["--enable-debug"],
    common.configure_args_slowdebug = ["--with-debug-level=slowdebug"],
    common.organization = "jpg.infra.builddeps"

    return common;
};

/**
 * Generates the profiles part of the configuration.
 *
 * @param input External data to use for generating the configuration
 * @param common The common values
 * @returns {{}} Profiles part of the configuration
 */
var getJibProfilesProfiles = function (input, common) {
    var profiles = {};

    // Main SE profiles
    var mainProfiles = {

        "linux-x64": {
            target_os: "linux",
            target_cpu: "x64",
            dependencies: concat(common.dependencies, "devkit"),
            configure_args: concat(common.configure_args, "--with-zlib=system"),
            default_make_targets: concat(common.default_make_targets, "docs-image")
        },

        "linux-x86": {
            target_os: "linux",
            target_cpu: "x86",
            build_cpu: "x64",
            dependencies: concat(common.dependencies, "devkit"),
            configure_args: concat(common.configure_args, common.configure_args_32bit,
                "--with-jvm-variants=minimal,client,server", "--with-zlib=system"),
            default_make_targets: common.default_make_targets
        },

        "macosx-x64": {
            target_os: "macosx",
            target_cpu: "x64",
            dependencies: concat(common.dependencies, "devkit"),
            configure_args: concat(common.configure_args, "--with-zlib=system"),
            default_make_targets: common.default_make_targets
        },

        "solaris-x64": {
            target_os: "solaris",
            target_cpu: "x64",
            dependencies: concat(common.dependencies, "devkit", "cups"),
            configure_args: concat(common.configure_args, "--with-zlib=system"),
            default_make_targets: common.default_make_targets
        },

        "solaris-sparcv9": {
            target_os: "solaris",
            target_cpu: "sparcv9",
            dependencies: concat(common.dependencies, "devkit", "cups"),
            configure_args: concat(common.configure_args, "--with-zlib=system"),
            default_make_targets: common.default_make_targets
        },

        "windows-x64": {
            target_os: "windows",
            target_cpu: "x64",
            dependencies: concat(common.dependencies, "devkit", "freetype"),
            configure_args: concat(common.configure_args),
            default_make_targets: common.default_make_targets
        },

        "windows-x86": {
            target_os: "windows",
            target_cpu: "x86",
            build_cpu: "x64",
            dependencies: concat(common.dependencies, "devkit", "freetype"),
            configure_args: concat(common.configure_args,
                "--with-jvm-variants=client,server", common.configure_args_32bit),
            default_make_targets: common.default_make_targets
        }
    };
    profiles = concatObjects(profiles, mainProfiles);
    // Generate debug versions of all the main profiles
    profiles = concatObjects(profiles, generateDebugProfiles(common, mainProfiles));
    // Generate slowdebug versions of all the main profiles
    profiles = concatObjects(profiles, generateSlowdebugProfiles(common, mainProfiles));

    // Generate open only profiles for all the main profiles for JPRT and reference
    // implementation builds.
    var openOnlyProfiles = generateOpenOnlyProfiles(common, mainProfiles);
    // The open only profiles on linux are used for reference builds and should
    // produce the compact profile images by default. This adds "profiles" as an
    // extra default target.
    var openOnlyProfilesExtra = {
        "linux-x64-open": {
            default_make_targets: "profiles"
        },

        "linux-x86-open": {
            default_make_targets: "profiles"
        }
    };
    var openOnlyProfiles = concatObjects(openOnlyProfiles, openOnlyProfilesExtra);

    profiles = concatObjects(profiles, openOnlyProfiles);
    // Generate debug profiles for the open jprt profiles
    profiles = concatObjects(profiles, generateDebugProfiles(common, openOnlyProfiles));

    // Profiles used to run tests. Used in JPRT.
    var testOnlyProfiles = {

        "run-test": {
            target_os: input.build_os,
            target_cpu: input.build_cpu,
            dependencies: [ "jtreg", "gnumake" ],
            labels: "test"
        }
    };
    profiles = concatObjects(profiles, testOnlyProfiles);

    // Generate the missing platform attributes
    profiles = generatePlatformAttributes(profiles);
    profiles = generateDefaultMakeTargetsConfigureArg(common, profiles);
    return profiles;
};

/**
 * Generate the dependencies part of the configuration
 *
 * @param input External data to use for generating the configuration
 * @param common The common values
 * @returns {{}} Dependencies part of configuration
 */
var getJibProfilesDependencies = function (input, common) {

    var boot_jdk_platform = input.build_os + "-"
        + (input.build_cpu == "x86" ? "i586" : input.build_cpu);

    var devkit_platform_revisions = {
        linux_x64: "gcc4.9.2-OEL6.4+1.0",
        macosx_x64: "Xcode6.3-MacOSX10.9+1.0",
        solaris_x64: "SS12u4-Solaris11u1+1.0",
        solaris_sparcv9: "SS12u4-Solaris11u1+1.0",
        windows_x64: "VS2013SP4+1.0"
    };

    var devkit_platform = (input.target_cpu == "x86"
        ? input.target_os + "_x64"
        : input.target_platform);

    var dependencies = {

        boot_jdk: {
            server: "javare",
            module: "jdk",
            revision: "8",
            checksum_file: boot_jdk_platform + "/MD5_VALUES",
            file: boot_jdk_platform + "/jdk-8-" + boot_jdk_platform + ".tar.gz",
            configure_args: (input.build_os == "macosx"
                ? "--with-boot-jdk=" + input.get("boot_jdk", "install_path") + "/jdk1.8.0.jdk/Contents/Home"
                : "--with-boot-jdk=" + input.get("boot_jdk", "install_path") + "/jdk1.8.0")
        },

        devkit: {
            organization: common.organization,
            ext: "tar.gz",
            module: "devkit-" + devkit_platform,
            revision: devkit_platform_revisions[devkit_platform]
        },

        build_devkit: {
            organization: common.organization,
            ext: "tar.gz",
            module: "devkit-" + input.build_platform,
            revision: devkit_platform_revisions[input.build_platform]
        },

        cups: {
            organization: common.organization,
            ext: "tar.gz",
            revision: "1.0118+1.0"
        },

        jtreg: {
            server: "javare",
            revision: "4.2",
            build_number: "b01",
            checksum_file: "MD5_VALUES",
            file: "jtreg_bin-4.2.zip",
            environment_name: "JT_HOME"
        },

        gnumake: {
            organization: common.organization,
            ext: "tar.gz",
            revision: "4.0+1.0",

            module: (input.build_os == "windows"
                ? "gnumake-" + input.build_osenv_platform
                : "gnumake-" + input.build_platform),

            configure_args: (input.build_os == "windows"
                ? "MAKE=" + input.get("gnumake", "install_path") + "/cygwin/bin/make"
                : "MAKE=" + input.get("gnumake", "install_path") + "/bin/make"),

            environment_path: (input.build_os == "windows"
                ? input.get("gnumake", "install_path") + "/cygwin/bin"
                : input.get("gnumake", "install_path") + "/bin")
        },

        freetype: {
            organization: common.organization,
            ext: "tar.gz",
            revision: "2.3.4+1.0",
            module: "freetype-" + input.target_platform
        }
    };

    return dependencies;
};

/**
 * Generate the missing platform attributes for profiles
 *
 * @param profiles Profiles map to generate attributes on
 * @returns {{}} New profiles map with platform attributes fully filled in
 */
var generatePlatformAttributes = function (profiles) {
    var ret = concatObjects(profiles, {});
    for (var profile in profiles) {
        if (ret[profile].build_os == null) {
            ret[profile].build_os = ret[profile].target_os;
        }
        if (ret[profile].build_cpu == null) {
            ret[profile].build_cpu = ret[profile].target_cpu;
        }
        ret[profile].target_platform = ret[profile].target_os + "_" + ret[profile].target_cpu;
        ret[profile].build_platform = ret[profile].build_os + "_" + ret[profile].build_cpu;
    }
    return ret;
};

/**
 * Generates debug versions of profiles. Clones the given profiles and adds
 * debug metadata.
 *
 * @param common Common values
 * @param profiles Profiles map to generate debug profiles for
 * @returns {{}} New map of profiles containing debug profiles
 */
var generateDebugProfiles = function (common, profiles) {
    var newProfiles = {};
    for (var profile in profiles) {
        var debugProfile = profile + "-debug";
        newProfiles[debugProfile] = clone(profiles[profile]);
        newProfiles[debugProfile].debug_level = "fastdebug";
        newProfiles[debugProfile].default_make_targets
            = common.default_make_targets_debug;
        newProfiles[debugProfile].labels
            = concat(newProfiles[debugProfile].labels || [], "debug"),
            newProfiles[debugProfile].configure_args
                = concat(newProfiles[debugProfile].configure_args,
                common.configure_args_debug);
    }
    return newProfiles;
};

/**
 * Generates slowdebug versions of profiles. Clones the given profiles and adds
 * debug metadata.
 *
 * @param common Common values
 * @param profiles Profiles map to generate debug profiles for
 * @returns {{}} New map of profiles containing debug profiles
 */
var generateSlowdebugProfiles = function (common, profiles) {
    var newProfiles = {};
    for (var profile in profiles) {
        var debugProfile = profile + "-slowdebug";
        newProfiles[debugProfile] = clone(profiles[profile]);
        newProfiles[debugProfile].debug_level = "slowdebug";
        newProfiles[debugProfile].default_make_targets
            = common.default_make_targets_slowdebug;
        newProfiles[debugProfile].labels
            = concat(newProfiles[debugProfile].labels || [], "slowdebug"),
            newProfiles[debugProfile].configure_args
                = concat(newProfiles[debugProfile].configure_args,
                common.configure_args_slowdebug);
    }
    return newProfiles;
};

/**
 * Generates open only versions of profiles. Clones the given profiles and adds
 * open metadata.
 *
 * @param common Common values
 * @param profiles Profiles map to generate open only profiles for
 * @returns {{}} New map of profiles containing open only profiles
 */
var generateOpenOnlyProfiles = function (common, profiles) {
    var newProfiles = {};
    for (var profile in profiles) {
        var openProfile = profile + "-open";
        newProfiles[openProfile] = clone(profiles[profile]);
        newProfiles[openProfile].labels
            = concat(newProfiles[openProfile].labels || [], "open"),
            newProfiles[openProfile].configure_args
                = concat(newProfiles[openProfile].configure_args,
                "--enable-openjdk-only");
    }
    return newProfiles;
};

/**
 * The default_make_targets attribute on a profile is not a real Jib attribute.
 * This function rewrites that attribute into the corresponding configure arg.
 * Calling this function multiple times on the same profiles object is safe.
 *
 * @param common Common values
 * @param profiles Profiles map to rewrite profiles for
 * @returns {{}} New map of profiles with the make targets converted
 */
var generateDefaultMakeTargetsConfigureArg = function (common, profiles) {
    var ret = concatObjects(profiles, {});
    for (var profile in ret) {
        if (ret[profile]["default_make_targets"] != null) {
            var targetsString = concat(ret[profile].default_make_targets).join(" ");
            // Iterate over all configure args and see if --with-default-make-target
            // is already there and change it, otherwise add it.
            var found = false;
            for (var arg in ret[profile].configure_args) {
                if (arg.startsWith("--with-default-make-target")) {
                    found = true;
                    arg.replace(/=.*/, "=" + targetsString);
                }
            }
            if (!found) {
                ret[profile].configure_args = concat(
                    ret[profile].configure_args,
                    "--with-default-make-target=" + targetsString);
            }
        }
    }
    return ret;
}

/**
 * Deep clones an object tree.
 *
 * @param o Object to clone
 * @returns {{}} Clone of o
 */
var clone = function (o) {
    return JSON.parse(JSON.stringify(o));
};

/**
 * Concatenates all arguments into a new array
 *
 * @returns {Array.<T>} New array containing all arguments
 */
var concat = function () {
    return Array.prototype.concat.apply([], arguments);
};

/**
 * Copies all elements in an array into a new array but replacing all
 * occurrences of original with replacement.
 *
 * @param original Element to look for
 * @param replacement Element to replace with
 * @param a Array to copy
 * @returns {Array} New array with all occurrences of original replaced
 *                  with replacement
 */
var replace = function (original, replacement, a) {
    var newA = [];
    for (var i in a) {
        if (original == a[i]) {
            newA.push(replacement);
        } else {
            newA.push(a[i]);
        }
    }
    return newA;
};

/**
 * Deep concatenation of two objects. For each node encountered, merge
 * the contents with the corresponding node in the other object tree,
 * treating all strings as array elements.
 *
 * @param o1 Object to concatenate
 * @param o2 Object to concatenate
 * @returns {{}} New object tree containing the concatenation of o1 and o2
 */
var concatObjects = function (o1, o2) {
    var ret = {};
    for (var a in o1) {
        if (o2[a] == null) {
            ret[a] = o1[a];
        }
    }
    for (var a in o2) {
        if (o1[a] == null) {
            ret[a] = o2[a];
        } else {
            if (typeof o1[a] == 'string') {
                ret[a] = [o1[a]].concat(o2[a]);
            } else if (Array.isArray(o1[a])) {
                ret[a] = o1[a].concat(o2[a]);
            } else if (typeof o1[a] == 'object') {
                ret[a] = concatObjects(o1[a], o2[a]);
            }
        }
    }
    return ret;
};
