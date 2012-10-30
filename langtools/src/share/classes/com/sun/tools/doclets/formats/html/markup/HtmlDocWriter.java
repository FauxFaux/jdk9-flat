/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.formats.html.markup;

import java.io.*;
import java.util.*;

import com.sun.javadoc.*;
import com.sun.tools.doclets.formats.html.ConfigurationImpl;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.DocLink;
import com.sun.tools.doclets.internal.toolkit.util.DocPath;
import com.sun.tools.doclets.internal.toolkit.util.DocPaths;


/**
 * Class for the Html Format Code Generation specific to JavaDoc.
 * This Class contains methods related to the Html Code Generation which
 * are used by the Sub-Classes in the package com.sun.tools.doclets.standard
 * and com.sun.tools.doclets.oneone.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @since 1.2
 * @author Atul M Dambalkar
 * @author Robert Field
 */
public abstract class HtmlDocWriter extends HtmlWriter {

    /**
     * Constructor. Initializes the destination file name through the super
     * class HtmlWriter.
     *
     * @param filename String file name.
     */
    public HtmlDocWriter(Configuration configuration, DocPath filename)
            throws IOException {
        super(configuration, filename);
        configuration.message.notice("doclet.Generating_0",
            filename.resolveAgainst(configuration.destDirName));
    }

    /**
     * Accessor for configuration.
     */
    public abstract Configuration configuration();

    /**
     * Return Html Hyper Link string.
     *
     * @param link       String name of the file.
     * @param label      Tag for the link.
     * @param strong       Boolean that sets label to strong.
     * @return String    Hyper Link.
     */
    public String getHyperLinkString(DocPath link,
                               String label, boolean strong) {
        return getHyperLinkString(link, label, strong, "", "", "");
    }

    public String getHyperLinkString(DocLink link,
                               String label, boolean strong) {
        return getHyperLinkString(link, label, strong, "", "", "");
    }

    /**
     * Get Html Hyper Link string.
     *
     * @param link       String name of the file.
     * @param label      Tag for the link.
     * @param strong       Boolean that sets label to strong.
     * @param stylename  String style of text defined in style sheet.
     * @return String    Hyper Link.
     */
    public String getHyperLinkString(DocPath link,
                               String label, boolean strong,
                               String stylename) {
        return getHyperLinkString(link, label, strong, stylename, "", "");
    }

    public String getHyperLinkString(DocLink link,
                               String label, boolean strong,
                               String stylename) {
        return getHyperLinkString(link, label, strong, stylename, "", "");
    }

    /**
     * Get Html Hyper Link string.
     *
     * @param where      Position of the link in the file. Character '#' is not
     *                   needed.
     * @param label      Tag for the link.
     * @return a content tree for the hyper link
     */
    public Content getHyperLink(String where,
                               Content label) {
        return getHyperLink(DocLink.fragment(where), label, "", "");
    }

    /**
     * Get Html Hyper Link string.
     *
     * @param link       String name of the file.
     * @param label      Tag for the link.
     * @return a content tree for the hyper link
     */
    public Content getHyperLink(DocPath link,
                               Content label) {
        return getHyperLink(link, label, "", "");
    }

    public Content getHyperLink(DocLink link,
                               Content label) {
        return getHyperLink(link, label, "", "");
    }

    /**
     * Get Html Hyper Link string.
     *
     * @param link       String name of the file.
     * @param label      Tag for the link.
     * @param strong       Boolean that sets label to strong.
     * @param stylename  String style of text defined in style sheet.
     * @param title      String that describes the links content for accessibility.
     * @param target     Target frame.
     * @return String    Hyper Link.
     */
    public String getHyperLinkString(DocPath link,
                               String label, boolean strong,
                               String stylename, String title, String target) {
        return getHyperLinkString(new DocLink(link), label, strong,
                stylename, title, target);
    }

    public String getHyperLinkString(DocLink link,
                               String label, boolean strong,
                               String stylename, String title, String target) {
        StringBuilder retlink = new StringBuilder();
        retlink.append("<a href=\"").append(link).append('"');
        if (title != null && title.length() != 0) {
            retlink.append(" title=\"").append(title).append('"');
        }
        if (target != null && target.length() != 0) {
            retlink.append(" target=\"").append(target).append('"');
        }
        retlink.append(">");
        if (stylename != null && stylename.length() != 0) {
            retlink.append("<FONT CLASS=\"");
            retlink.append(stylename);
            retlink.append("\">");
        }
        if (strong) {
            retlink.append("<span class=\"strong\">");
        }
        retlink.append(label);
        if (strong) {
            retlink.append("</span>");
        }
        if (stylename != null && stylename.length() != 0) {
            retlink.append("</FONT>");
        }
        retlink.append("</a>");
        return retlink.toString();
    }

    /**
     * Get Html Hyper Link.
     *
     * @param link       String name of the file.
     * @param label      Tag for the link.
     * @param title      String that describes the link's content for accessibility.
     * @param target     Target frame.
     * @return a content tree for the hyper link.
     */
    public Content getHyperLink(DocPath link,
            Content label, String title, String target) {
        return getHyperLink(new DocLink(link), label, title, target);
    }

    public Content getHyperLink(DocLink link,
            Content label, String title, String target) {
        HtmlTree anchor = HtmlTree.A(link.toString(), label);
        if (title != null && title.length() != 0) {
            anchor.addAttr(HtmlAttr.TITLE, title);
        }
        if (target != null && target.length() != 0) {
            anchor.addAttr(HtmlAttr.TARGET, target);
        }
        return anchor;
    }

    /**
     * Get link string without positioning in the file.
     *
     * @param link       String name of the file.
     * @param label      Tag for the link.
     * @return Strign    Hyper link.
     */
    public String getHyperLinkString(DocPath link, String label) {
        return getHyperLinkString(link, label, false);
    }

    /**
     * Get the name of the package, this class is in.
     *
     * @param cd    ClassDoc.
     */
    public String getPkgName(ClassDoc cd) {
        String pkgName = cd.containingPackage().name();
        if (pkgName.length() > 0) {
            pkgName += ".";
            return pkgName;
        }
        return "";
    }

    public boolean getMemberDetailsListPrinted() {
        return memberDetailsListPrinted;
    }

    /**
     * Print the frameset version of the Html file header.
     * Called only when generating an HTML frameset file.
     *
     * @param title Title of this HTML document
     * @param noTimeStamp If true, don't print time stamp in header
     * @param frameset the frameset to be added to the HTML document
     */
    public void printFramesetDocument(String title, boolean noTimeStamp,
            Content frameset) throws IOException {
        Content htmlDocType = DocType.Frameset();
        Content htmlComment = new Comment(configuration.getText("doclet.New_Page"));
        Content head = new HtmlTree(HtmlTag.HEAD);
        if (! noTimeStamp) {
            Content headComment = new Comment(getGeneratedByString());
            head.addContent(headComment);
        }
        if (configuration.charset.length() > 0) {
            Content meta = HtmlTree.META("Content-Type", "text/html",
                    configuration.charset);
            head.addContent(meta);
        }
        Content windowTitle = HtmlTree.TITLE(new StringContent(title));
        head.addContent(windowTitle);
        head.addContent(getFramesetJavaScript());
        Content htmlTree = HtmlTree.HTML(configuration.getLocale().getLanguage(),
                head, frameset);
        Content htmlDocument = new HtmlDocument(htmlDocType,
                htmlComment, htmlTree);
        write(htmlDocument);
    }

    /**
     * Print the appropriate spaces to format the class tree in the class page.
     *
     * @param len   Number of spaces.
     */
    public String spaces(int len) {
        String space = "";

        for (int i = 0; i < len; i++) {
            space += " ";
        }
        return space;
    }

    protected String getGeneratedByString() {
        Calendar calendar = new GregorianCalendar(TimeZone.getDefault());
        Date today = calendar.getTime();
        return "Generated by javadoc ("+ ConfigurationImpl.BUILD_DATE + ") on " + today;
    }
}
