/*
 * Copyright (c) 1998, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.internal.toolkit.util;

import java.util.*;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.*;

/**
 * Build Class Hierarchy for all the Classes. This class builds the Class
 * Tree and the Interface Tree separately.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @see java.util.HashMap
 * @see java.util.List
 * @see com.sun.javadoc.Type
 * @see com.sun.javadoc.ClassDoc
 * @author Atul M Dambalkar
 */
public class ClassTree {

    /**
     * List of baseclasses. Contains only java.lang.Object. Can be used to get
     * the mapped listing of sub-classes.
     */
    private List<ClassDoc> baseclasses = new ArrayList<>();

    /**
    * Mapping for each Class with their SubClasses
    */
    private Map<ClassDoc,List<ClassDoc>> subclasses = new HashMap<>();

    /**
     * List of base-interfaces. Contains list of all the interfaces who do not
     * have super-interfaces. Can be used to get the mapped listing of
     * sub-interfaces.
     */
    private List<ClassDoc> baseinterfaces = new ArrayList<>();

    /**
    * Mapping for each Interface with their SubInterfaces
    */
    private Map<ClassDoc,List<ClassDoc>> subinterfaces = new HashMap<>();

    private List<ClassDoc> baseEnums = new ArrayList<>();
    private Map<ClassDoc,List<ClassDoc>> subEnums = new HashMap<>();

    private List<ClassDoc> baseAnnotationTypes = new ArrayList<>();
    private Map<ClassDoc,List<ClassDoc>> subAnnotationTypes = new HashMap<>();

    /**
    * Mapping for each Interface with classes who implement it.
    */
    private Map<ClassDoc,List<ClassDoc>> implementingclasses = new HashMap<>();

    private final Configuration configuration;
    private final Utils utils;
    /**
     * Constructor. Build the Tree using the Root of this Javadoc run.
     *
     * @param configuration the configuration of the doclet.
     * @param noDeprecated Don't add deprecated classes in the class tree, if
     * true.
     */
    public ClassTree(Configuration configuration, boolean noDeprecated) {
        configuration.message.notice("doclet.Building_Tree");
        this.configuration = configuration;
        this.utils = configuration.utils;
        buildTree(configuration.root.classes());
    }

    /**
     * Constructor. Build the Tree using the Root of this Javadoc run.
     *
     * @param root Root of the Document.
     * @param configuration The current configuration of the doclet.
     */
    public ClassTree(RootDoc root, Configuration configuration) {
        this.configuration = configuration;
        this.utils = configuration.utils;
        buildTree(root.classes());
    }

    /**
     * Constructor. Build the tree for the given array of classes.
     *
     * @param classes Array of classes.
     * @param configuration The curren configuration of the doclet.
     */
    public ClassTree(ClassDoc[] classes, Configuration configuration) {
        this.configuration = configuration;
        this.utils = configuration.utils;
        buildTree(classes);
    }

    /**
     * Generate mapping for the sub-classes for every class in this run.
     * Return the sub-class list for java.lang.Object which will be having
     * sub-class listing for itself and also for each sub-class itself will
     * have their own sub-class lists.
     *
     * @param classes all the classes in this run.
     * @param configuration the current configuration of the doclet.
     */
    private void buildTree(ClassDoc[] classes) {
        for (ClassDoc aClass : classes) {
            // In the tree page (e.g overview-tree.html) do not include
            // information of classes which are deprecated or are a part of a
            // deprecated package.
            if (configuration.nodeprecated &&
                    (utils.isDeprecated(aClass) ||
                    utils.isDeprecated(aClass.containingPackage()))) {
                continue;
            }

            if (configuration.javafx
                    && aClass.tags("treatAsPrivate").length > 0) {
                continue;
            }

            if (aClass.isEnum()) {
                processType(aClass, configuration, baseEnums, subEnums);
            } else if (aClass.isClass()) {
                processType(aClass, configuration, baseclasses, subclasses);
            } else if (aClass.isInterface()) {
                processInterface(aClass);
                List<ClassDoc> list = implementingclasses.get(aClass);
                if (list != null) {
                    Collections.sort(list);
                }
            } else if (aClass.isAnnotationType()) {
                processType(aClass, configuration, baseAnnotationTypes,
                    subAnnotationTypes);
            }
        }

        Collections.sort(baseinterfaces);
        for (List<ClassDoc> docs : subinterfaces.values()) {
            Collections.sort(docs);
        }
        for (List<ClassDoc> docs : subclasses.values()) {
            Collections.sort(docs);
        }
    }

    /**
     * For the class passed map it to it's own sub-class listing.
     * For the Class passed, get the super class,
     * if superclass is non null, (it is not "java.lang.Object")
     *    get the "value" from the hashmap for this key Class
     *    if entry not found create one and get that.
     *    add this Class as a sub class in the list
     *    Recurse till hits java.lang.Object Null SuperClass.
     *
     * @param cd class for which sub-class mapping to be generated.
     * @param configuration the current configurtation of the doclet.
     */
    private void processType(ClassDoc cd, Configuration configuration,
            List<ClassDoc> bases, Map<ClassDoc,List<ClassDoc>> subs) {
        ClassDoc superclass = utils.getFirstVisibleSuperClassCD(cd, configuration);
        if (superclass != null) {
            if (!add(subs, superclass, cd)) {
                return;
            } else {
                processType(superclass, configuration, bases, subs);
            }
        } else {     // cd is java.lang.Object, add it once to the list
            if (!bases.contains(cd)) {
                bases.add(cd);
            }
        }
        List<Type> intfacs = utils.getAllInterfaces(cd, configuration);
        for (Type intfac : intfacs) {
            add(implementingclasses, intfac.asClassDoc(), cd);
        }
    }

    /**
     * For the interface passed get the interfaces which it extends, and then
     * put this interface in the sub-interface list of those interfaces. Do it
     * recursively. If a interface doesn't have super-interface just attach
     * that interface in the list of all the baseinterfaces.
     *
     * @param cd Interface under consideration.
     */
    private void processInterface(ClassDoc cd) {
        ClassDoc[] intfacs = cd.interfaces();
        if (intfacs.length > 0) {
            for (ClassDoc intfac : intfacs) {
                if (!add(subinterfaces, intfac, cd)) {
                    return;
                } else {
                    processInterface(intfac);   // Recurse
                }
            }
        } else {
            // we need to add all the interfaces who do not have
            // super-interfaces to baseinterfaces list to traverse them
            if (!baseinterfaces.contains(cd)) {
                baseinterfaces.add(cd);
            }
        }
    }

    /**
     * Adjust the Class Tree. Add the class interface  in to it's super-class'
     * or super-interface's sub-interface list.
     *
     * @param map the entire map.
     * @param superclass java.lang.Object or the super-interface.
     * @param cd sub-interface to be mapped.
     * @returns boolean true if class added, false if class already processed.
     */
    private boolean add(Map<ClassDoc,List<ClassDoc>> map, ClassDoc superclass, ClassDoc cd) {
        List<ClassDoc> list = map.get(superclass);
        if (list == null) {
            list = new ArrayList<>();
            map.put(superclass, list);
        }
        if (list.contains(cd)) {
            return false;
        } else {
            list.add(cd);
        }
        return true;
    }

    /**
     * From the map return the list of sub-classes or sub-interfaces. If list
     * is null create a new one and return it.
     *
     * @param map The entire map.
     * @param cd class for which the sub-class list is requested.
     * @returns List Sub-Class list for the class passed.
     */
    private List<ClassDoc> get(Map<ClassDoc,List<ClassDoc>> map, ClassDoc cd) {
        List<ClassDoc> list = map.get(cd);
        if (list == null) {
            return new ArrayList<>();
        }
        return list;
    }

    /**
     *  Return the sub-class list for the class passed.
     *
     * @param cd class whose sub-class list is required.
     */
    public List<ClassDoc> subclasses(ClassDoc cd) {
        return get(subclasses, cd);
    }

    /**
     *  Return the sub-interface list for the interface passed.
     *
     * @param cd interface whose sub-interface list is required.
     */
    public List<ClassDoc> subinterfaces(ClassDoc cd) {
        return get(subinterfaces, cd);
    }

    /**
     *  Return the list of classes which implement the interface passed.
     *
     * @param cd interface whose implementing-classes list is required.
     */
    public List<ClassDoc> implementingclasses(ClassDoc cd) {
        List<ClassDoc> result = get(implementingclasses, cd);
        List<ClassDoc> subinterfaces = allSubs(cd, false);

        //If class x implements a subinterface of cd, then it follows
        //that class x implements cd.
        Iterator<ClassDoc> implementingClassesIter, subInterfacesIter = subinterfaces.listIterator();
        ClassDoc c;
        while(subInterfacesIter.hasNext()){
            implementingClassesIter = implementingclasses(
                    subInterfacesIter.next()).listIterator();
            while(implementingClassesIter.hasNext()){
                c = implementingClassesIter.next();
                if(! result.contains(c)){
                    result.add(c);
                }
            }
        }
        Collections.sort(result);
        return result;
    }

    /**
     *  Return the sub-class/interface list for the class/interface passed.
     *
     * @param cd class/interface whose sub-class/interface list is required.
     * @param isEnum true if the subclasses should be forced to come from the
     * enum tree.
     */
    public List<ClassDoc> subs(ClassDoc cd, boolean isEnum) {
        if (isEnum) {
            return get(subEnums, cd);
        } else if (cd.isAnnotationType()) {
            return get(subAnnotationTypes, cd);
        } else if (cd.isInterface()) {
            return get(subinterfaces, cd);
        } else if (cd.isClass()) {
            return get(subclasses, cd);
        } else {
            return null;
        }

    }

    /**
     * Return a list of all direct or indirect, sub-classes and subinterfaces
     * of the ClassDoc argument.
     *
     * @param cd ClassDoc whose sub-classes or sub-interfaces are requested.
     * @param isEnum true if the subclasses should be forced to come from the
     * enum tree.
     */
    public List<ClassDoc> allSubs(ClassDoc cd, boolean isEnum) {
        List<ClassDoc> list = subs(cd, isEnum);
        for (int i = 0; i < list.size(); i++) {
            cd = list.get(i);
            List<ClassDoc> tlist = subs(cd, isEnum);
            for (ClassDoc tcd : tlist) {
                if (!list.contains(tcd)) {
                    list.add(tcd);
                }
            }
        }
        Collections.sort(list);
        return list;
    }

    /**
     *  Return the base-classes list. This will have only one element namely
     *  thw classdoc for java.lang.Object, since this is the base class for all
     *  classes.
     */
    public List<ClassDoc> baseclasses() {
        return baseclasses;
    }

    /**
     *  Return the list of base interfaces. This is the list of interfaces
     *  which do not have super-interface.
     */
    public List<ClassDoc> baseinterfaces() {
        return baseinterfaces;
    }

    /**
     *  Return the list of base enums. This is the list of enums
     *  which do not have super-enums.
     */
    public List<ClassDoc> baseEnums() {
        return baseEnums;
    }

    /**
     *  Return the list of base annotation types. This is the list of
     *  annotation types which do not have super-annotation types.
     */
    public List<ClassDoc> baseAnnotationTypes() {
        return baseAnnotationTypes;
    }
}
