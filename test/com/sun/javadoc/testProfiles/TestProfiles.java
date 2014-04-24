/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug      8006124 8009684 8016921 8023700 8024096 8008164 8026567 8026770
 * @summary  Test javadoc support for profiles.
 * @author   Bhavesh Patel, Evgeniya Stepanova
 * @library  ../lib/
 * @build    JavadocTester TestProfiles
 * @run main TestProfiles
 */
public class TestProfiles extends JavadocTester {

    //Test information.
    private static final String PROFILE_OUTPUT_DIR = OUTPUT_DIR + "-1";
    private static final String PACKAGE_OUTPUT_DIR = OUTPUT_DIR + "-2";
    //Javadoc arguments.
    private static final String[] ARGS1 = new String[]{
        "-d", PROFILE_OUTPUT_DIR, "-sourcepath", SRC_DIR, "-Xprofilespath",
         SRC_DIR + "/profile-rtjar-includes.txt", "pkg1", "pkg2",
         "pkg3", "pkg4", "pkg5", "pkgDeprecated"
    };
    private static final String[] ARGS2 = new String[]{
        "-d", PACKAGE_OUTPUT_DIR, "-sourcepath", SRC_DIR, "pkg1", "pkg2",
        "pkg3", "pkg4", "pkg5"
    };
    //Input for string tests for profiles.
    private static final String[][] PROFILES_TEST = {
        // Tests for profile-overview-frame.html listing all profiles.
        { "profile-overview-frame.html",
            "<span><a href=\"overview-frame.html\" "
            + "target=\"packageListFrame\">All&nbsp;Packages</a></span>"
        },
        { "profile-overview-frame.html",
            "<li><a href=\"compact1-frame.html\" target=\"packageListFrame\">"
            + "compact1</a></li>"
        },
        // Tests for profileName-frame.html listing all packages in a profile.
        { "compact2-frame.html",
            "<span><a href=\"overview-frame.html\" target=\"packageListFrame\">"
            + "All&nbsp;Packages</a></span><span><a href=\"profile-overview-frame.html\" "
            + "target=\"packageListFrame\">All&nbsp;Profiles</a></span>"
        },
        { "compact2-frame.html",
            "<li><a href=\"pkg4/compact2-package-frame.html\" "
            + "target=\"packageFrame\">pkg4</a></li>"
        },
        // Test for profileName-package-frame.html listing all types in a
        // package of a profile.
        { "pkg2/compact2-package-frame.html",
            "<a href=\"../compact2-summary.html\" target=\"classFrame\">"
            + "compact2</a> - <a href=\"../pkg2/compact2-package-summary.html\" "
            + "target=\"classFrame\">pkg2</a>"
        },
        // Tests for profileName-summary.html listing the summary for a profile.
        { "compact2-summary.html",
            "<li><a href=\"compact1-summary.html\">Prev&nbsp;Profile</a></li>\n"
            + "<li><a href=\"compact3-summary.html\">Next&nbsp;Profile</a></li>"
        },
        { "compact2-summary.html",
            "<h1 title=\"Profile\" class=\"title\">Profile&nbsp;compact2</h1>"
        },
        { "compact2-summary.html",
            "<h3><a href=\"pkg2/compact2-package-summary.html\" "
            + "target=\"classFrame\">pkg2</a></h3>"
        },
        { "compact2-summary.html",
            "<ul class=\"blockList\">\n" +
            "<li class=\"blockList\">\n"
            + "<h3><a href=\"pkg2/compact2-package-summary.html\" target=\"classFrame\">"
            + "pkg2</a></h3>\n" +
            "<table class=\"typeSummary\" border=\"0\" "
            + "cellpadding=\"3\" cellspacing=\"0\" summary=\"Class Summary table, "
            + "listing classes, and an explanation\">"
        },
        { "compact2-summary.html",
            "<ul class=\"blockList\">\n" +
            "<li class=\"blockList\">\n"
            + "<h3><a href=\"pkg4/compact2-package-summary.html\" target=\"classFrame\">"
            + "pkg4</a></h3>\n" +
            "<table class=\"typeSummary\" border=\"0\" "
            + "cellpadding=\"3\" cellspacing=\"0\" summary=\"Class Summary table, "
            + "listing classes, and an explanation\">"
        },
        // Tests for profileName-package-summary.html listing the summary for a
        // package in a profile.
        { "pkg5/compact3-package-summary.html",
            "<li><a href=\"../pkg4/compact3-package-summary.html\">Prev&nbsp;Package"
            + "</a></li>"
        },
        { "pkg5/compact3-package-summary.html",
            "<div class=\"subTitle\">compact3</div>"
        },
        { "pkg5/compact3-package-summary.html",
            "<ul class=\"blockList\">\n" +
            "<li class=\"blockList\">\n"
            + "<table class=\"typeSummary\" border=\"0\" cellpadding=\"3\" "
            + "cellspacing=\"0\" summary=\"Interface Summary table, listing "
            + "interfaces, and an explanation\">"
        },
        //Test for "overview-frame.html" showing the "All Profiles" link.
        { "overview-frame.html",
            "<span><a href=\"profile-overview-frame.html\" "
            + "target=\"packageListFrame\">All&nbsp;Profiles</a></span>"
        },
        //Test for "className.html" showing the profile information for the type.
        { "pkg2/Class1Pkg2.html",
            "<div class=\"subTitle\">compact1, compact2, compact3</div>"
        },
        { "index.html",
            "<frame src=\"overview-frame.html\" name=\"packageListFrame\" " +
            "title=\"All Packages\">"
        },
        //Test for "overview-summary.html" showing the profile list.
        { "overview-summary.html",
            "<ul>\n" +
            "<li><a href=\"compact1-summary.html\" target=\"classFrame\">" +
            "compact1</a></li>\n" +
            "<li><a href=\"compact2-summary.html\" " +
            "target=\"classFrame\">compact2</a></li>\n" +
            "<li><a href=\"" +
            "compact3-summary.html\" target=\"classFrame\">compact3</a></li>\n" +
            "</ul>"
        },
        //Test deprecated class in profiles
        { "compact1-summary.html",
            "<td class=\"colFirst\"><a href=\"pkg2/Class1Pkg2.html\" title=\"class in pkg2\">Class1Pkg2</a></td>\n"
            + "<td class=\"colLast\">Deprecated"
        },
        { "deprecated-list.html",
            "<td class=\"colOne\"><a href=\"pkg2/Class1Pkg2.html\" title=\"class in pkg2\">pkg2.Class1Pkg2</a>\n"
            +"<div class=\"block\"><span class=\"deprecationComment\">Class1Pkg2. This class is deprecated</span></div>"
        },
        //Test deprecated package in profile
        { "deprecated-list.html",
            "<td class=\"colOne\"><a href=\"pkgDeprecated/package-summary.html\">pkgDeprecated</a>\n"
            +"<div class=\"block\"><span class=\"deprecationComment\">This package is <b>Deprecated</b>."
            + " Use pkg1.</span></div>"
        },
        { "pkgDeprecated/package-summary.html",
            "<div class=\"deprecatedContent\"><span class=\"deprecatedLabel\">Deprecated.</span>\n"
            + "<div class=\"block\"><span class=\"deprecationComment\">This package is <b>Deprecated</b>."
            + " Use pkg1.</span></div>"
        },
        // need to add teststring when JDK-8015496 will be fixed
        //Test exception in profiles
        { "compact1-summary.html",
            "<table class=\"typeSummary\" "
            + "border=\"0\" cellpadding=\"3\" cellspacing=\"0\" "
            + "summary=\"Exception Summary table, listing exceptions, and an explanation\">\n"
            + "<caption><span>Exception Summary</span><span class=\"tabEnd\">"
            + "&nbsp;</span></caption>\n" +
            "<tr>\n" +
            "<th class=\"colFirst\" "
            + "scope=\"col\">Exception</th>\n" +
            "<th class=\"colLast\" scope=\"col\">"
            + "Description</th>\n" +
            "</tr>\n" +
            "<tbody>\n" +
            "<tr class=\"altColor\">\n"
            + "<td class=\"colFirst\"><a href=\"pkg2/ClassException.html\""
            + " title=\"class in pkg2\">ClassException</a></td>"
        },
        //Test errors in profiles
        { "compact1-summary.html",
            "<table class=\"typeSummary\" border=\"0\" cellpadding=\"3\" cellspacing=\"0\" "
            + "summary=\"Error Summary table, listing errors, and an explanation\">\n"
            + "<caption><span>Error Summary</span><span class=\"tabEnd\">&nbsp;"
            + "</span></caption>\n" +
            "<tr>\n" +
            "<th class=\"colFirst\""
            + " scope=\"col\">Error</th>\n" +
            "<th class=\"colLast\" "
            + "scope=\"col\">Description</th>\n" +
            "</tr>\n" +
            "<tbody>\n"
            + "<tr class=\"altColor\">\n" +
            "<td class=\"colFirst\">"
            + "<a href=\"pkg2/ClassError.html\" title=\"class in pkg2\">ClassError</a></td>"
        }
    };
    private static final String[][] PROFILES_NEGATED_TEST = {
        { "pkg3/Class2Pkg3.html",
            "<div class=\"subTitle\">compact1"
        },
        { "pkg3/Interface1Pkg3.html",
            "<div class=\"subTitle\">compact1"
        },
        { "pkg4/compact2-package-frame.html",
            "<li><a href=\"Anno1Pkg4.html\" title=\"annotation in pkg4\" "
            + "target=\"classFrame\">Anno1Pkg4</a></li>"
        },
        { "compact1-summary.html","<li>Use</li>"
        },
        { "compact2-summary.html",
            "<ul class=\"blockList\">\n" +
            "<li class=\"blockList\">\n"
            + "<h3><a href=\"pkg2/compact2-package-summary.html\" target=\"classFrame\">"
            + "pkg2</a></h3>\n" +
            "<li class=\"blockList\">\n"
            + "<table class=\"typeSummary\" border=\"0\" "
            + "cellpadding=\"3\" cellspacing=\"0\" summary=\"Class Summary table, "
            + "listing classes, and an explanation\">"
        },
        { "pkg5/compact3-package-summary.html",
            "<ul class=\"blockList\">\n" +
            "<li class=\"blockList\">\n"
            + "<li class=\"blockList\">\n"
            + "<table class=\"typeSummary\" border=\"0\" cellpadding=\"3\" "
            + "cellspacing=\"0\" summary=\"Interface Summary table, listing "
            + "interfaces, and an explanation\">"
        }
    };
    private static final String[][] PACKAGES_TEST = {
        { "overview-frame.html",
            "<h2 title=\"Packages\">Packages</h2>"
        },
        { "pkg4/package-frame.html",
            "<h1 class=\"bar\"><a href=\"../pkg4/package-summary.html\" "
            + "target=\"classFrame\">pkg4</a></h1>"
        },
        { "pkg4/package-summary.html",
            "<div class=\"header\">\n" +
            "<h1 title=\"Package\" "
            + "class=\"title\">Package&nbsp;pkg4</h1>\n" +
            "</div>"
        }
    };
    private static final String[][] PACKAGES_NEGATED_TEST = {
        { "overview-frame.html",
            "<span><a href=\"profile-overview-frame.html\" "
            + "target=\"packageListFrame\">All&nbsp;Profiles</a></span>"
        },
        { "pkg2/Class1Pkg2.html",
            "<div class=\"subTitle\">compact1, compact2, compact3</div>"
        },
        { "overview-summary.html",
            "<ul>\n" +
            "<li><a href=\"compact1-summary.html\" target=\"classFrame\">" +
            "compact1</a></li>\n" +
            "<li><a href=\"compact2-summary.html\" " +
            "target=\"classFrame\">compact2</a></li>\n" +
            "<li><a href=\"" +
            "compact3-summary.html\" target=\"classFrame\">compact3</a></li>\n" +
            "</ul>"
        }
    };
    private static final String[] PACKAGES_NEGATED_FILE_TEST = {
        "profile-overview-frame.html",
        "compact2-frame.html",
        "pkg2/compact2-package-frame.html",
        "compact2-summary.html",
        "pkg5/compact3-package-summary.html"
    };

    /**
     * The entry point of the test.
     *
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestProfiles tester = new TestProfiles();
        tester.run(ARGS1, PROFILES_TEST, PROFILES_NEGATED_TEST);
        tester.run(ARGS2, PACKAGES_TEST, PACKAGES_NEGATED_TEST, NO_FILE_TEST, PACKAGES_NEGATED_FILE_TEST);
        tester.printSummary();
    }
}
