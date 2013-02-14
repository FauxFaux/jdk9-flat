/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * (C) Copyright Taligent, Inc. 1996, 1997 - All Rights Reserved
 * (C) Copyright IBM Corp. 1996 - 1998 - All Rights Reserved
 *
 * The original version of this source code and documentation
 * is copyrighted and owned by Taligent, Inc., a wholly-owned
 * subsidiary of IBM. These materials are provided under terms
 * of a License Agreement between Taligent and Sun. This technology
 * is protected by multiple US and International patents.
 *
 * This notice and attribution to Taligent may not be removed.
 * Taligent is a registered trademark of Taligent, Inc.
 *
 */

/*
 * COPYRIGHT AND PERMISSION NOTICE
 *
 * Copyright (C) 1991-2012 Unicode, Inc. All rights reserved. Distributed under
 * the Terms of Use in http://www.unicode.org/copyright.html.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of the Unicode data files and any associated documentation (the "Data
 * Files") or Unicode software and any associated documentation (the
 * "Software") to deal in the Data Files or Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, and/or sell copies of the Data Files or Software, and
 * to permit persons to whom the Data Files or Software are furnished to do so,
 * provided that (a) the above copyright notice(s) and this permission notice
 * appear with all copies of the Data Files or Software, (b) both the above
 * copyright notice(s) and this permission notice appear in associated
 * documentation, and (c) there is clear notice in each modified Data File or
 * in the Software as well as in the documentation associated with the Data
 * File(s) or Software that the data or software has been modified.
 *
 * THE DATA FILES AND SOFTWARE ARE PROVIDED "AS IS", WITHOUT WARRANTY OF ANY
 * KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT OF
 * THIRD PARTY RIGHTS. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR HOLDERS
 * INCLUDED IN THIS NOTICE BE LIABLE FOR ANY CLAIM, OR ANY SPECIAL INDIRECT OR
 * CONSEQUENTIAL DAMAGES, OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE,
 * DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER
 * TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE
 * OF THE DATA FILES OR SOFTWARE.
 *
 * Except as contained in this notice, the name of a copyright holder shall not
 * be used in advertising or otherwise to promote the sale, use or other
 * dealings in these Data Files or Software without prior written authorization
 * of the copyright holder.
 */

package sun.text.resources.hr;

import java.util.ListResourceBundle;

public class FormatData_hr extends ListResourceBundle {
    /**
     * Overrides ListResourceBundle
     */
    @Override
    protected final Object[][] getContents() {
        final String[] rocEras ={
            "prije R.O.C.",
            "R.O.C.",
        };
        return new Object[][] {
            { "MonthNames",
                new String[] {
                    "sije\u010dnja",
                    "velja\u010de",
                    "o\u017eujka",
                    "travnja",
                    "svibnja",
                    "lipnja",
                    "srpnja",
                    "kolovoza",
                    "rujna",
                    "listopada",
                    "studenoga",
                    "prosinca",
                    "",
                }
            },
            { "standalone.MonthNames",
                new String[] {
                    "sije\u010danj", // january
                    "velja\u010da", // february
                    "o\u017eujak", // march
                    "travanj", // april
                    "svibanj", // may
                    "lipanj", // june
                    "srpanj", // july
                    "kolovoz", // august
                    "rujan", // september
                    "listopad", // october
                    "studeni", // november
                    "prosinac", // december
                    "" // month 13 if applicable
                }
            },
            { "MonthAbbreviations",
                new String[] {
                    "sij",
                    "velj",
                    "o\u017eu",
                    "tra",
                    "svi",
                    "lip",
                    "srp",
                    "kol",
                    "ruj",
                    "lis",
                    "stu",
                    "pro",
                    "",
                }
            },
            { "standalone.MonthAbbreviations",
                new String[] {
                    "sij", // abb january
                    "vel", // abb february
                    "o\u017eu", // abb march
                    "tra", // abb april
                    "svi", // abb may
                    "lip", // abb june
                    "srp", // abb july
                    "kol", // abb august
                    "ruj", // abb september
                    "lis", // abb october
                    "stu", // abb november
                    "pro", // abb december
                    "" // abb month 13 if applicable
                }
            },
            { "standalone.MonthNarrows",
                new String[] {
                    "1.",
                    "2.",
                    "3.",
                    "4.",
                    "5.",
                    "6.",
                    "7.",
                    "8.",
                    "9.",
                    "10.",
                    "11.",
                    "12.",
                    "",
                }
            },
            { "DayNames",
                new String[] {
                    "nedjelja", // Sunday
                    "ponedjeljak", // Monday
                    "utorak", // Tuesday
                    "srijeda", // Wednesday
                    "\u010detvrtak", // Thursday
                    "petak", // Friday
                    "subota" // Saturday
                }
            },
            { "DayAbbreviations",
                new String[] {
                    "ned", // abb Sunday
                    "pon", // abb Monday
                    "uto", // abb Tuesday
                    "sri", // abb Wednesday
                    "\u010det", // abb Thursday
                    "pet", // abb Friday
                    "sub" // abb Saturday
                }
            },
            { "DayNarrows",
                new String[] {
                    "N",
                    "P",
                    "U",
                    "S",
                    "\u010c",
                    "P",
                    "S",
                }
            },
            { "standalone.DayNarrows",
                new String[] {
                    "n",
                    "p",
                    "u",
                    "s",
                    "\u010d",
                    "p",
                    "s",
                }
            },
            { "NumberElements",
                new String[] {
                    ",", // decimal separator
                    ".", // group (thousands) separator
                    ";", // list separator
                    "%", // percent sign
                    "0", // native 0 digit
                    "#", // pattern digit
                    "-", // minus sign
                    "E", // exponential
                    "\u2030", // per mille
                    "\u221e", // infinity
                    "\ufffd" // NaN
                }
            },
            { "TimePatterns",
                new String[] {
                    "HH:mm:ss z", // full time pattern
                    "HH:mm:ss z", // long time pattern
                    "HH:mm:ss", // medium time pattern
                    "HH:mm", // short time pattern
                }
            },
            { "DatePatterns",
                new String[] {
                    "yyyy. MMMM dd", // full date pattern
                    "yyyy. MMMM dd", // long date pattern
                    "yyyy.MM.dd", // medium date pattern
                    "yyyy.MM.dd", // short date pattern
                }
            },
            { "DateTimePatterns",
                new String[] {
                    "{1} {0}" // date-time pattern
                }
            },
            { "DateTimePatternChars", "GanjkHmsSEDFwWxhKzZ" },
            { "cldr.buddhist.DatePatterns",
                new String[] {
                    "EEEE, d. MMMM y. G",
                    "d. MMMM y. G",
                    "d. M. y. G",
                    "d.M.y.",
                }
            },
            { "cldr.japanese.DatePatterns",
                new String[] {
                    "EEEE, d. MMMM y. G",
                    "d. MMMM y. G",
                    "d. M. y. G",
                    "d.M.y. G",
                }
            },
            { "roc.Eras", rocEras },
            { "roc.short.Eras", rocEras },
            { "cldr.roc.DatePatterns",
                new String[] {
                    "EEEE, d. MMMM y. G",
                    "d. MMMM y. G",
                    "d. M. y. G",
                    "d.M.y. G",
                }
            },
            { "roc.DatePatterns",
                new String[] {
                    "EEEE, d. MMMM y. GGGG",
                    "d. MMMM y. GGGG",
                    "d. M. y. GGGG",
                    "d.M.y. GGGG",
                }
            },
            { "calendarname.islamic-civil", "islamski civilni kalendar" },
            { "calendarname.islamicc", "islamski civilni kalendar" },
            { "calendarname.roc", "kalendar Republike Kine" },
            { "calendarname.islamic", "islamski kalendar" },
            { "calendarname.buddhist", "budisti\u010dki kalendar" },
            { "calendarname.japanese", "japanski kalendar" },
            { "calendarname.gregorian", "gregorijanski kalendar" },
            { "calendarname.gregory", "gregorijanski kalendar" },
            { "field.era", "era" },
            { "field.year", "godina" },
            { "field.month", "mjesec" },
            { "field.week", "tjedan" },
            { "field.weekday", "dan u tjednu" },
            { "field.dayperiod", "dio dana" },
            { "field.hour", "sat" },
            { "field.minute", "minuta" },
            { "field.second", "sekunda" },
            { "field.zone", "zona" },
        };
    }
}
