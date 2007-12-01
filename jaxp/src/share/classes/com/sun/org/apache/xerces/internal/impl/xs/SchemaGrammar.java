/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2001-2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.xerces.internal.impl.xs;

import java.util.Vector;

import com.sun.org.apache.xerces.internal.impl.Constants;
import com.sun.org.apache.xerces.internal.impl.dv.SchemaDVFactory;
import com.sun.org.apache.xerces.internal.impl.dv.ValidatedInfo;
import com.sun.org.apache.xerces.internal.impl.dv.XSSimpleType;
import com.sun.org.apache.xerces.internal.impl.xs.identity.IdentityConstraint;
import com.sun.org.apache.xerces.internal.impl.xs.util.SimpleLocator;
import com.sun.org.apache.xerces.internal.impl.xs.util.StringListImpl;
import com.sun.org.apache.xerces.internal.impl.xs.util.XSNamedMap4Types;
import com.sun.org.apache.xerces.internal.impl.xs.util.XSNamedMapImpl;
import com.sun.org.apache.xerces.internal.impl.xs.util.XSObjectListImpl;
import com.sun.org.apache.xerces.internal.parsers.DOMParser;
import com.sun.org.apache.xerces.internal.parsers.IntegratedParserConfiguration;
import com.sun.org.apache.xerces.internal.parsers.SAXParser;
import com.sun.org.apache.xerces.internal.util.SymbolHash;
import com.sun.org.apache.xerces.internal.util.SymbolTable;
import com.sun.org.apache.xerces.internal.xni.NamespaceContext;
import com.sun.org.apache.xerces.internal.xni.grammars.XMLGrammarDescription;
import com.sun.org.apache.xerces.internal.xni.grammars.XSGrammar;
import com.sun.org.apache.xerces.internal.xs.StringList;
import com.sun.org.apache.xerces.internal.xs.XSAnnotation;
import com.sun.org.apache.xerces.internal.xs.XSAttributeDeclaration;
import com.sun.org.apache.xerces.internal.xs.XSAttributeGroupDefinition;
import com.sun.org.apache.xerces.internal.xs.XSConstants;
import com.sun.org.apache.xerces.internal.xs.XSElementDeclaration;
import com.sun.org.apache.xerces.internal.xs.XSModel;
import com.sun.org.apache.xerces.internal.xs.XSModelGroupDefinition;
import com.sun.org.apache.xerces.internal.xs.XSNamedMap;
import com.sun.org.apache.xerces.internal.xs.XSNamespaceItem;
import com.sun.org.apache.xerces.internal.xs.XSNotationDeclaration;
import com.sun.org.apache.xerces.internal.xs.XSObjectList;
import com.sun.org.apache.xerces.internal.xs.XSParticle;
import com.sun.org.apache.xerces.internal.xs.XSTypeDefinition;
import com.sun.org.apache.xerces.internal.xs.XSWildcard;

/**
 * This class is to hold all schema component declaration that are declared
 * within one namespace.
 *
 * The Grammar class this class extends contains what little
 * commonality there is between XML Schema and DTD grammars.  It's
 * useful to distinguish grammar objects from other kinds of object
 * when they exist in pools or caches.
 *
 * @xerces.internal
 *
 * @author Sandy Gao, IBM
 * @author Elena Litani, IBM
 *
 */

public class SchemaGrammar implements XSGrammar, XSNamespaceItem {

    // the target namespace of grammar
    String fTargetNamespace;

    // global decls: map from decl name to decl object
    SymbolHash fGlobalAttrDecls;
    SymbolHash fGlobalAttrGrpDecls;
    SymbolHash fGlobalElemDecls;
    SymbolHash fGlobalGroupDecls;
    SymbolHash fGlobalNotationDecls;
    SymbolHash fGlobalIDConstraintDecls;
    SymbolHash fGlobalTypeDecls;

    // the XMLGrammarDescription member
    XSDDescription fGrammarDescription = null;

    // annotations associated with the "root" schema of this targetNamespace
    XSAnnotationImpl [] fAnnotations = null;

    // number of annotations declared
    int fNumAnnotations;

    // symbol table for constructing parsers (annotation support)
    private SymbolTable fSymbolTable = null;
    // parsers for annotation support
    private SAXParser fSAXParser = null;
    private DOMParser fDOMParser = null;

    //
    // Constructors
    //

    // needed to make BuiltinSchemaGrammar work.
    protected SchemaGrammar() {}

    /**
     * Default constructor.
     *
     * @param targetNamespace
     * @param grammarDesc the XMLGrammarDescription corresponding to this objec
     *          at the least a systemId should always be known.
     * @param symbolTable   needed for annotation support
     */
    public SchemaGrammar(String targetNamespace, XSDDescription grammarDesc,
                SymbolTable symbolTable) {
        fTargetNamespace = targetNamespace;
        fGrammarDescription = grammarDesc;
        fSymbolTable = symbolTable;

        // REVISIT: do we know the numbers of the following global decls
        // when creating this grammar? If so, we can pass the numbers in,
        // and use that number to initialize the following hashtables.
        fGlobalAttrDecls  = new SymbolHash();
        fGlobalAttrGrpDecls = new SymbolHash();
        fGlobalElemDecls = new SymbolHash();
        fGlobalGroupDecls = new SymbolHash();
        fGlobalNotationDecls = new SymbolHash();
        fGlobalIDConstraintDecls = new SymbolHash();

        // if we are parsing S4S, put built-in types in first
        // they might get overwritten by the types from S4S, but that's
        // considered what the application wants to do.
        if (fTargetNamespace == SchemaSymbols.URI_SCHEMAFORSCHEMA)
            fGlobalTypeDecls = SG_SchemaNS.fGlobalTypeDecls.makeClone();
        else
            fGlobalTypeDecls = new SymbolHash();
    } // <init>(String, XSDDescription)

    // number of built-in XSTypes we need to create for base and full
    // datatype set
    private static final int BASICSET_COUNT = 29;
    private static final int FULLSET_COUNT  = 46;

    private static final int GRAMMAR_XS  = 1;
    private static final int GRAMMAR_XSI = 2;

    // this class makes sure the static, built-in schema grammars
    // are immutable.
    public static class BuiltinSchemaGrammar extends SchemaGrammar {
        /**
         * Special constructor to create the grammars for the schema namespaces
         *
         * @param grammar
         */
        public BuiltinSchemaGrammar(int grammar) {
            SchemaDVFactory schemaFactory = SchemaDVFactory.getInstance();

            if (grammar == GRAMMAR_XS) {
                // target namespace
                fTargetNamespace = SchemaSymbols.URI_SCHEMAFORSCHEMA;

                // grammar description
                fGrammarDescription = new XSDDescription();
                fGrammarDescription.fContextType = XSDDescription.CONTEXT_PREPARSE;
                fGrammarDescription.setNamespace(SchemaSymbols.URI_SCHEMAFORSCHEMA);

                // no global decls other than types
                fGlobalAttrDecls  = new SymbolHash(1);
                fGlobalAttrGrpDecls = new SymbolHash(1);
                fGlobalElemDecls = new SymbolHash(1);
                fGlobalGroupDecls = new SymbolHash(1);
                fGlobalNotationDecls = new SymbolHash(1);
                fGlobalIDConstraintDecls = new SymbolHash(1);

                // get all built-in types
                fGlobalTypeDecls = schemaFactory.getBuiltInTypes();
                // add anyType
                fGlobalTypeDecls.put(fAnyType.getName(), fAnyType);
            }
            else if (grammar == GRAMMAR_XSI) {
                // target namespace
                fTargetNamespace = SchemaSymbols.URI_XSI;
                // grammar description
                fGrammarDescription = new XSDDescription();
                fGrammarDescription.fContextType = XSDDescription.CONTEXT_PREPARSE;
                fGrammarDescription.setNamespace(SchemaSymbols.URI_XSI);

                // no global decls other than attributes
                fGlobalAttrGrpDecls = new SymbolHash(1);
                fGlobalElemDecls = new SymbolHash(1);
                fGlobalGroupDecls = new SymbolHash(1);
                fGlobalNotationDecls = new SymbolHash(1);
                fGlobalIDConstraintDecls = new SymbolHash(1);
                fGlobalTypeDecls = new SymbolHash(1);

                // 4 attributes, so initialize the size as 4*2 = 8
                fGlobalAttrDecls  = new SymbolHash(8);
                String name = null;
                String tns = null;
                XSSimpleType type = null;
                short scope = XSConstants.SCOPE_GLOBAL;

                // xsi:type
                name = SchemaSymbols.XSI_TYPE;
                tns = SchemaSymbols.URI_XSI;
                type = schemaFactory.getBuiltInType(SchemaSymbols.ATTVAL_QNAME);
                fGlobalAttrDecls.put(name, new BuiltinAttrDecl(name, tns, type, scope));

                // xsi:nil
                name = SchemaSymbols.XSI_NIL;
                tns = SchemaSymbols.URI_XSI;
                type = schemaFactory.getBuiltInType(SchemaSymbols.ATTVAL_BOOLEAN);
                fGlobalAttrDecls.put(name, new BuiltinAttrDecl(name, tns, type, scope));

                XSSimpleType anyURI = schemaFactory.getBuiltInType(SchemaSymbols.ATTVAL_ANYURI);

                // xsi:schemaLocation
                name = SchemaSymbols.XSI_SCHEMALOCATION;
                tns = SchemaSymbols.URI_XSI;
                type = schemaFactory.createTypeList(null, SchemaSymbols.URI_XSI, (short)0, anyURI, null);
                fGlobalAttrDecls.put(name, new BuiltinAttrDecl(name, tns, type, scope));

                // xsi:noNamespaceSchemaLocation
                name = SchemaSymbols.XSI_NONAMESPACESCHEMALOCATION;
                tns = SchemaSymbols.URI_XSI;
                type = anyURI;
                fGlobalAttrDecls.put(name, new BuiltinAttrDecl(name, tns, type, scope));
            }
        } // <init>(int)

        // return the XMLGrammarDescription corresponding to this
        // object
        public XMLGrammarDescription getGrammarDescription() {
            return fGrammarDescription.makeClone();
        } // getGrammarDescription():  XMLGrammarDescription

        // override these methods solely so that these
        // objects cannot be modified once they're created.
        public void setImportedGrammars(Vector importedGrammars) {
            // ignore
        }
        public void addGlobalAttributeDecl(XSAttributeDecl decl) {
            // ignore
        }
        public void addGlobalAttributeGroupDecl(XSAttributeGroupDecl decl) {
            // ignore
        }
        public void addGlobalElementDecl(XSElementDecl decl) {
            // ignore
        }
        public void addGlobalGroupDecl(XSGroupDecl decl) {
            // ignore
        }
        public void addGlobalNotationDecl(XSNotationDecl decl) {
            // ignore
        }
        public void addGlobalTypeDecl(XSTypeDefinition decl) {
            // ignore
        }
        public void addComplexTypeDecl(XSComplexTypeDecl decl, SimpleLocator locator) {
            // ignore
        }
        public void addRedefinedGroupDecl(XSGroupDecl derived, XSGroupDecl base, SimpleLocator locator) {
            // ignore
        }
        public synchronized void addDocument(Object document, String location) {
            // ignore
        }

        // annotation support
        synchronized DOMParser getDOMParser() {
            return null;
        }
        synchronized SAXParser getSAXParser() {
            return null;
        }
    }

    /**
     * <p>A partial schema for schemas for validating annotations.</p>
     *
     * @xerces.internal
     *
     * @author Michael Glavassevich, IBM
     */
    public static final class Schema4Annotations extends SchemaGrammar {

        /**
         * Special constructor to create a schema
         * capable of validating annotations.
         */
        public Schema4Annotations() {

            // target namespace
            fTargetNamespace = SchemaSymbols.URI_SCHEMAFORSCHEMA;

            // grammar description
            fGrammarDescription = new XSDDescription();
            fGrammarDescription.fContextType = XSDDescription.CONTEXT_PREPARSE;
            fGrammarDescription.setNamespace(SchemaSymbols.URI_SCHEMAFORSCHEMA);

            // no global decls other than types and
            // element declarations for <annotation>, <documentation> and <appinfo>.
            fGlobalAttrDecls  = new SymbolHash(1);
            fGlobalAttrGrpDecls = new SymbolHash(1);
            fGlobalElemDecls = new SymbolHash(6);
            fGlobalGroupDecls = new SymbolHash(1);
            fGlobalNotationDecls = new SymbolHash(1);
            fGlobalIDConstraintDecls = new SymbolHash(1);

            // get all built-in types
            fGlobalTypeDecls = SG_SchemaNS.fGlobalTypeDecls;

            // create element declarations for <annotation>, <documentation> and <appinfo>
            XSElementDecl annotationDecl = createAnnotationElementDecl(SchemaSymbols.ELT_ANNOTATION);
            XSElementDecl documentationDecl = createAnnotationElementDecl(SchemaSymbols.ELT_DOCUMENTATION);
            XSElementDecl appinfoDecl = createAnnotationElementDecl(SchemaSymbols.ELT_APPINFO);

            // add global element declarations
            fGlobalElemDecls.put(annotationDecl.fName, annotationDecl);
            fGlobalElemDecls.put(documentationDecl.fName, documentationDecl);
            fGlobalElemDecls.put(appinfoDecl.fName, appinfoDecl);

            // create complex type declarations for <annotation>, <documentation> and <appinfo>
            XSComplexTypeDecl annotationType = new XSComplexTypeDecl();
            XSComplexTypeDecl documentationType = new XSComplexTypeDecl();
            XSComplexTypeDecl appinfoType = new XSComplexTypeDecl();

            // set the types on their element declarations
            annotationDecl.fType = annotationType;
            documentationDecl.fType = documentationType;
            appinfoDecl.fType = appinfoType;

            // create attribute groups for <annotation>, <documentation> and <appinfo>
            XSAttributeGroupDecl annotationAttrs = new XSAttributeGroupDecl();
            XSAttributeGroupDecl documentationAttrs = new XSAttributeGroupDecl();
            XSAttributeGroupDecl appinfoAttrs = new XSAttributeGroupDecl();

            // fill in attribute groups
            {
                // create and fill attribute uses for <annotation>, <documentation> and <appinfo>
                XSAttributeUseImpl annotationIDAttr = new XSAttributeUseImpl();
                annotationIDAttr.fAttrDecl = new XSAttributeDecl();
                annotationIDAttr.fAttrDecl.setValues(SchemaSymbols.ATT_ID, null, (XSSimpleType) fGlobalTypeDecls.get(SchemaSymbols.ATTVAL_ID),
                        XSConstants.VC_NONE, XSConstants.SCOPE_LOCAL, null, annotationType, null);
                annotationIDAttr.fUse = SchemaSymbols.USE_OPTIONAL;
                annotationIDAttr.fConstraintType = XSConstants.VC_NONE;

                XSAttributeUseImpl documentationSourceAttr = new XSAttributeUseImpl();
                documentationSourceAttr.fAttrDecl = new XSAttributeDecl();
                documentationSourceAttr.fAttrDecl.setValues(SchemaSymbols.ATT_SOURCE, null, (XSSimpleType) fGlobalTypeDecls.get(SchemaSymbols.ATTVAL_ANYURI),
                        XSConstants.VC_NONE, XSConstants.SCOPE_LOCAL, null, documentationType, null);
                documentationSourceAttr.fUse = SchemaSymbols.USE_OPTIONAL;
                documentationSourceAttr.fConstraintType = XSConstants.VC_NONE;

                XSAttributeUseImpl documentationLangAttr = new XSAttributeUseImpl();
                documentationLangAttr.fAttrDecl = new XSAttributeDecl();
                documentationLangAttr.fAttrDecl.setValues("lang".intern(), NamespaceContext.XML_URI, (XSSimpleType) fGlobalTypeDecls.get(SchemaSymbols.ATTVAL_LANGUAGE),
                        XSConstants.VC_NONE, XSConstants.SCOPE_LOCAL, null, documentationType, null);
                documentationLangAttr.fUse = SchemaSymbols.USE_OPTIONAL;
                documentationLangAttr.fConstraintType = XSConstants.VC_NONE;

                XSAttributeUseImpl appinfoSourceAttr = new XSAttributeUseImpl();
                appinfoSourceAttr.fAttrDecl = new XSAttributeDecl();
                appinfoSourceAttr.fAttrDecl.setValues(SchemaSymbols.ATT_SOURCE, null, (XSSimpleType) fGlobalTypeDecls.get(SchemaSymbols.ATTVAL_ANYURI),
                        XSConstants.VC_NONE, XSConstants.SCOPE_LOCAL, null, appinfoType, null);
                appinfoSourceAttr.fUse = SchemaSymbols.USE_OPTIONAL;
                appinfoSourceAttr.fConstraintType = XSConstants.VC_NONE;

                // create lax attribute wildcard for <annotation>, <documentation> and <appinfo>
                XSWildcardDecl otherAttrs = new XSWildcardDecl();
                otherAttrs.fNamespaceList = new String [] {fTargetNamespace, null};
                otherAttrs.fType = XSWildcard.NSCONSTRAINT_NOT;
                otherAttrs.fProcessContents = XSWildcard.PC_LAX;

                // add attribute uses and wildcards to attribute groups for <annotation>, <documentation> and <appinfo>
                annotationAttrs.addAttributeUse(annotationIDAttr);
                annotationAttrs.fAttributeWC = otherAttrs;

                documentationAttrs.addAttributeUse(documentationSourceAttr);
                documentationAttrs.addAttributeUse(documentationLangAttr);
                documentationAttrs.fAttributeWC = otherAttrs;

                appinfoAttrs.addAttributeUse(appinfoSourceAttr);
                appinfoAttrs.fAttributeWC = otherAttrs;
            }

            // create particles for <annotation>
            XSParticleDecl annotationParticle = createUnboundedModelGroupParticle();
            {
                XSModelGroupImpl annotationChoice = new XSModelGroupImpl();
                annotationChoice.fCompositor = XSModelGroupImpl.MODELGROUP_CHOICE;
                annotationChoice.fParticleCount = 2;
                annotationChoice.fParticles = new XSParticleDecl[2];
                annotationChoice.fParticles[0] = createChoiceElementParticle(appinfoDecl);
                annotationChoice.fParticles[1] = createChoiceElementParticle(documentationDecl);
                annotationParticle.fValue = annotationChoice;
            }

            // create wildcard particle for <documentation> and <appinfo>
            XSParticleDecl anyWCSequenceParticle = createUnboundedAnyWildcardSequenceParticle();

            // fill complex types
            annotationType.setValues("#AnonType_" + SchemaSymbols.ELT_ANNOTATION, fTargetNamespace, SchemaGrammar.fAnyType,
                    XSConstants.DERIVATION_RESTRICTION, XSConstants.DERIVATION_NONE, (short) (XSConstants.DERIVATION_EXTENSION | XSConstants.DERIVATION_RESTRICTION),
                    XSComplexTypeDecl.CONTENTTYPE_ELEMENT, false, annotationAttrs, null, annotationParticle, new XSObjectListImpl(null, 0));
            annotationType.setName("#AnonType_" + SchemaSymbols.ELT_ANNOTATION);
            annotationType.setIsAnonymous();

            documentationType.setValues("#AnonType_" + SchemaSymbols.ELT_DOCUMENTATION, fTargetNamespace, SchemaGrammar.fAnyType,
                    XSConstants.DERIVATION_RESTRICTION, XSConstants.DERIVATION_NONE, (short) (XSConstants.DERIVATION_EXTENSION | XSConstants.DERIVATION_RESTRICTION),
                    XSComplexTypeDecl.CONTENTTYPE_MIXED, false, documentationAttrs, null, anyWCSequenceParticle, new XSObjectListImpl(null, 0));
            documentationType.setName("#AnonType_" + SchemaSymbols.ELT_DOCUMENTATION);
            documentationType.setIsAnonymous();

            appinfoType.setValues("#AnonType_" + SchemaSymbols.ELT_APPINFO, fTargetNamespace, SchemaGrammar.fAnyType,
                    XSConstants.DERIVATION_RESTRICTION, XSConstants.DERIVATION_NONE, (short) (XSConstants.DERIVATION_EXTENSION | XSConstants.DERIVATION_RESTRICTION),
                    XSComplexTypeDecl.CONTENTTYPE_MIXED, false, appinfoAttrs, null, anyWCSequenceParticle, new XSObjectListImpl(null, 0));
            appinfoType.setName("#AnonType_" + SchemaSymbols.ELT_APPINFO);
            appinfoType.setIsAnonymous();

        } // <init>(int)

        // return the XMLGrammarDescription corresponding to this
        // object
        public XMLGrammarDescription getGrammarDescription() {
            return fGrammarDescription.makeClone();
        } // getGrammarDescription():  XMLGrammarDescription

        // override these methods solely so that these
        // objects cannot be modified once they're created.
        public void setImportedGrammars(Vector importedGrammars) {
            // ignore
        }
        public void addGlobalAttributeDecl(XSAttributeDecl decl) {
            // ignore
        }
        public void addGlobalAttributeGroupDecl(XSAttributeGroupDecl decl) {
            // ignore
        }
        public void addGlobalElementDecl(XSElementDecl decl) {
            // ignore
        }
        public void addGlobalGroupDecl(XSGroupDecl decl) {
            // ignore
        }
        public void addGlobalNotationDecl(XSNotationDecl decl) {
            // ignore
        }
        public void addGlobalTypeDecl(XSTypeDefinition decl) {
            // ignore
        }
        public void addComplexTypeDecl(XSComplexTypeDecl decl, SimpleLocator locator) {
            // ignore
        }
        public void addRedefinedGroupDecl(XSGroupDecl derived, XSGroupDecl base, SimpleLocator locator) {
            // ignore
        }
        public synchronized void addDocument(Object document, String location) {
            // ignore
        }

        // annotation support
        synchronized DOMParser getDOMParser() {
            return null;
        }
        synchronized SAXParser getSAXParser() {
            return null;
        }

        //
        // private helper methods
        //

        private XSElementDecl createAnnotationElementDecl(String localName) {
            XSElementDecl eDecl = new XSElementDecl();
            eDecl.fName = localName;
            eDecl.fTargetNamespace = fTargetNamespace;
            eDecl.setIsGlobal();
            eDecl.fBlock = (XSConstants.DERIVATION_EXTENSION |
                    XSConstants.DERIVATION_RESTRICTION | XSConstants.DERIVATION_SUBSTITUTION);
            eDecl.setConstraintType(XSConstants.VC_NONE);
            return eDecl;
        }

        private XSParticleDecl createUnboundedModelGroupParticle() {
            XSParticleDecl particle = new XSParticleDecl();
            particle.fMinOccurs = 0;
            particle.fMaxOccurs = SchemaSymbols.OCCURRENCE_UNBOUNDED;
            particle.fType = XSParticleDecl.PARTICLE_MODELGROUP;
            return particle;
        }

        private XSParticleDecl createChoiceElementParticle(XSElementDecl ref) {
            XSParticleDecl particle = new XSParticleDecl();
            particle.fMinOccurs = 1;
            particle.fMaxOccurs = 1;
            particle.fType = XSParticleDecl.PARTICLE_ELEMENT;
            particle.fValue = ref;
            return particle;
        }

        private XSParticleDecl createUnboundedAnyWildcardSequenceParticle() {
            XSParticleDecl particle = createUnboundedModelGroupParticle();
            XSModelGroupImpl sequence = new XSModelGroupImpl();
            sequence.fCompositor = XSModelGroupImpl.MODELGROUP_SEQUENCE;
            sequence.fParticleCount = 1;
            sequence.fParticles = new XSParticleDecl[1];
            sequence.fParticles[0] = createAnyLaxWildcardParticle();
            particle.fValue = sequence;
            return particle;
        }

        private XSParticleDecl createAnyLaxWildcardParticle() {
            XSParticleDecl particle = new XSParticleDecl();
            particle.fMinOccurs = 1;
            particle.fMaxOccurs = 1;
            particle.fType = XSParticleDecl.PARTICLE_WILDCARD;

            XSWildcardDecl anyWC = new XSWildcardDecl();
            anyWC.fNamespaceList = null;
            anyWC.fType = XSWildcard.NSCONSTRAINT_ANY;
            anyWC.fProcessContents = XSWildcard.PC_LAX;

            particle.fValue = anyWC;
            return particle;
        }
    }

    // Grammar methods

    // return the XMLGrammarDescription corresponding to this
    // object
    public XMLGrammarDescription getGrammarDescription() {
        return fGrammarDescription;
    } // getGrammarDescription():  XMLGrammarDescription

    // DTDGrammar methods
    public boolean isNamespaceAware () {
        return true;
    } // isNamespaceAware():boolean

    Vector fImported = null;

    public void setImportedGrammars(Vector importedGrammars) {
        fImported = importedGrammars;
    }

    public Vector getImportedGrammars() {
        return fImported;
    }

    /**
     * Returns this grammar's target namespace.
     */
    public final String getTargetNamespace() {
        return fTargetNamespace;
    } // getTargetNamespace():String

    /**
     * register one global attribute
     */
    public void addGlobalAttributeDecl(XSAttributeDecl decl) {
        fGlobalAttrDecls.put(decl.fName, decl);
    }

    /**
     * register one global attribute group
     */
    public void addGlobalAttributeGroupDecl(XSAttributeGroupDecl decl) {
        fGlobalAttrGrpDecls.put(decl.fName, decl);
    }

    /**
     * register one global element
     */
    public void addGlobalElementDecl(XSElementDecl decl) {
        fGlobalElemDecls.put(decl.fName, decl);

        // if there is a substitution group affiliation, store in an array,
        // for further constraint checking: UPA, PD, EDC
        if (decl.fSubGroup != null) {
            if (fSubGroupCount == fSubGroups.length)
                fSubGroups = resize(fSubGroups, fSubGroupCount+INC_SIZE);
            fSubGroups[fSubGroupCount++] = decl;
        }
    }

    /**
     * register one global group
     */
    public void addGlobalGroupDecl(XSGroupDecl decl) {
        fGlobalGroupDecls.put(decl.fName, decl);
    }

    /**
     * register one global notation
     */
    public void addGlobalNotationDecl(XSNotationDecl decl) {
        fGlobalNotationDecls.put(decl.fName, decl);
    }

    /**
     * register one global type
     */
    public void addGlobalTypeDecl(XSTypeDefinition decl) {
        fGlobalTypeDecls.put(decl.getName(), decl);
    }

    /**
     * register one identity constraint
     */
    public final void addIDConstraintDecl(XSElementDecl elmDecl, IdentityConstraint decl) {
        elmDecl.addIDConstraint(decl);
        fGlobalIDConstraintDecls.put(decl.getIdentityConstraintName(), decl);
    }

    /**
     * get one global attribute
     */
    public final XSAttributeDecl getGlobalAttributeDecl(String declName) {
        return(XSAttributeDecl)fGlobalAttrDecls.get(declName);
    }

    /**
     * get one global attribute group
     */
    public final XSAttributeGroupDecl getGlobalAttributeGroupDecl(String declName) {
        return(XSAttributeGroupDecl)fGlobalAttrGrpDecls.get(declName);
    }

    /**
     * get one global element
     */
    public final XSElementDecl getGlobalElementDecl(String declName) {
        return(XSElementDecl)fGlobalElemDecls.get(declName);
    }

    /**
     * get one global group
     */
    public final XSGroupDecl getGlobalGroupDecl(String declName) {
        return(XSGroupDecl)fGlobalGroupDecls.get(declName);
    }

    /**
     * get one global notation
     */
    public final XSNotationDecl getGlobalNotationDecl(String declName) {
        return(XSNotationDecl)fGlobalNotationDecls.get(declName);
    }

    /**
     * get one global type
     */
    public final XSTypeDefinition getGlobalTypeDecl(String declName) {
        return(XSTypeDefinition)fGlobalTypeDecls.get(declName);
    }

    /**
     * get one identity constraint
     */
    public final IdentityConstraint getIDConstraintDecl(String declName) {
        return(IdentityConstraint)fGlobalIDConstraintDecls.get(declName);
    }

    /**
     * get one identity constraint
     */
    public final boolean hasIDConstraints() {
        return fGlobalIDConstraintDecls.getLength() > 0;
    }

    // array to store complex type decls
    private static final int INITIAL_SIZE = 16;
    private static final int INC_SIZE     = 16;

    private int fCTCount = 0;
    private XSComplexTypeDecl[] fComplexTypeDecls = new XSComplexTypeDecl[INITIAL_SIZE];
    private SimpleLocator[] fCTLocators = new SimpleLocator[INITIAL_SIZE];

    // an array to store groups being redefined by restriction
    // even-numbered elements are the derived groups, odd-numbered ones their bases
    private static final int REDEFINED_GROUP_INIT_SIZE = 2;
    private int fRGCount = 0;
    private XSGroupDecl[] fRedefinedGroupDecls = new XSGroupDecl[REDEFINED_GROUP_INIT_SIZE];
    private SimpleLocator[] fRGLocators = new SimpleLocator[REDEFINED_GROUP_INIT_SIZE/2];

    // a flag to indicate whether we have checked the 3 constraints on this
    // grammar.
    boolean fFullChecked = false;

    /**
     * add one complex type decl: for later constraint checking
     */
    public void addComplexTypeDecl(XSComplexTypeDecl decl, SimpleLocator locator) {
        if (fCTCount == fComplexTypeDecls.length) {
            fComplexTypeDecls = resize(fComplexTypeDecls, fCTCount+INC_SIZE);
            fCTLocators = resize(fCTLocators, fCTCount+INC_SIZE);
        }
        fCTLocators[fCTCount] = locator;
        fComplexTypeDecls[fCTCount++] = decl;
    }

    /**
     * add a group redefined by restriction: for later constraint checking
     */
    public void addRedefinedGroupDecl(XSGroupDecl derived, XSGroupDecl base, SimpleLocator locator) {
        if (fRGCount == fRedefinedGroupDecls.length) {
            // double array size each time.
            fRedefinedGroupDecls = resize(fRedefinedGroupDecls, fRGCount << 1);
            fRGLocators = resize(fRGLocators, fRGCount);
        }
        fRGLocators[fRGCount/2] = locator;
        fRedefinedGroupDecls[fRGCount++] = derived;
        fRedefinedGroupDecls[fRGCount++] = base;
    }

    /**
     * get all complex type decls: for later constraint checking
     */
    final XSComplexTypeDecl[] getUncheckedComplexTypeDecls() {
        if (fCTCount < fComplexTypeDecls.length) {
            fComplexTypeDecls = resize(fComplexTypeDecls, fCTCount);
            fCTLocators = resize(fCTLocators, fCTCount);
        }
        return fComplexTypeDecls;
    }

    /**
     * get the error locator of all complex type decls
     */
    final SimpleLocator[] getUncheckedCTLocators() {
        if (fCTCount < fCTLocators.length) {
            fComplexTypeDecls = resize(fComplexTypeDecls, fCTCount);
            fCTLocators = resize(fCTLocators, fCTCount);
        }
        return fCTLocators;
    }

    /**
     * get all redefined groups: for later constraint checking
     */
    final XSGroupDecl[] getRedefinedGroupDecls() {
        if (fRGCount < fRedefinedGroupDecls.length) {
            fRedefinedGroupDecls = resize(fRedefinedGroupDecls, fRGCount);
            fRGLocators = resize(fRGLocators, fRGCount/2);
        }
        return fRedefinedGroupDecls;
    }

    /**
     * get the error locator of all redefined groups
     */
    final SimpleLocator[] getRGLocators() {
        if (fRGCount < fRedefinedGroupDecls.length) {
            fRedefinedGroupDecls = resize(fRedefinedGroupDecls, fRGCount);
            fRGLocators = resize(fRGLocators, fRGCount/2);
        }
        return fRGLocators;
    }

    /**
     * after the first-round checking, some types don't need to be checked
     * against UPA again. here we trim the array to the proper size.
     */
    final void setUncheckedTypeNum(int newSize) {
        fCTCount = newSize;
        fComplexTypeDecls = resize(fComplexTypeDecls, fCTCount);
        fCTLocators = resize(fCTLocators, fCTCount);
    }

    // used to store all substitution group information declared in
    // this namespace
    private int fSubGroupCount = 0;
    private XSElementDecl[] fSubGroups = new XSElementDecl[INITIAL_SIZE];

    /**
     * get all substitution group information: for the 3 constraint checking
     */
    final XSElementDecl[] getSubstitutionGroups() {
        if (fSubGroupCount < fSubGroups.length)
            fSubGroups = resize(fSubGroups, fSubGroupCount);
        return fSubGroups;
    }

    // anyType and anySimpleType: because there are so many places where
    // we need direct access to these two types
    public final static XSComplexTypeDecl fAnyType = new XSAnyType();
    private static class XSAnyType extends XSComplexTypeDecl {
        public XSAnyType () {
            fName = SchemaSymbols.ATTVAL_ANYTYPE;
            super.fTargetNamespace = SchemaSymbols.URI_SCHEMAFORSCHEMA;
            fBaseType = this;
            fDerivedBy = XSConstants.DERIVATION_RESTRICTION;
            fContentType = XSComplexTypeDecl.CONTENTTYPE_MIXED;

            fParticle = null;
            fAttrGrp = null;
        }

        // overridden methods
        public void setValues(String name, String targetNamespace,
                XSTypeDefinition baseType, short derivedBy, short schemaFinal,
                short block, short contentType,
                boolean isAbstract, XSAttributeGroupDecl attrGrp,
                XSSimpleType simpleType, XSParticleDecl particle) {
            // don't allow this.
        }

        public void setName(String name){
            // don't allow this.
        }

        public void setIsAbstractType() {
            // null implementation
        }

        public void setContainsTypeID() {
            // null implementation
        }

        public void setIsAnonymous() {
            // null implementation
        }

        public void reset() {
            // null implementation
        }

        public XSObjectList getAttributeUses() {
            return new XSObjectListImpl(null, 0);
        }

        public XSAttributeGroupDecl getAttrGrp() {
            XSWildcardDecl wildcard = new XSWildcardDecl();
            wildcard.fProcessContents = XSWildcardDecl.PC_LAX;
            XSAttributeGroupDecl attrGrp = new XSAttributeGroupDecl();
            attrGrp.fAttributeWC = wildcard;
            return attrGrp;
        }

        public XSWildcard getAttributeWildcard() {
            XSWildcardDecl wildcard = new XSWildcardDecl();
            wildcard.fProcessContents = XSWildcardDecl.PC_LAX;
            return wildcard;
        }

        public XSParticle getParticle() {
            // the wildcard used in anyType (content and attribute)
            // the spec will change strict to skip for anyType
            XSWildcardDecl wildcard = new XSWildcardDecl();
            wildcard.fProcessContents = XSWildcardDecl.PC_LAX;
            // the particle for the content wildcard
            XSParticleDecl particleW = new XSParticleDecl();
            particleW.fMinOccurs = 0;
            particleW.fMaxOccurs = SchemaSymbols.OCCURRENCE_UNBOUNDED;
            particleW.fType = XSParticleDecl.PARTICLE_WILDCARD;
            particleW.fValue = wildcard;
            // the model group of a sequence of the above particle
            XSModelGroupImpl group = new XSModelGroupImpl();
            group.fCompositor = XSModelGroupImpl.MODELGROUP_SEQUENCE;
            group.fParticleCount = 1;
            group.fParticles = new XSParticleDecl[1];
            group.fParticles[0] = particleW;
            // the content of anyType: particle of the above model group
            XSParticleDecl particleG = new XSParticleDecl();
            particleG.fType = XSParticleDecl.PARTICLE_MODELGROUP;
            particleG.fValue = group;

            return particleG;
        }

        public XSObjectList getAnnotations() {
            return null;
        }
    }
    private static class BuiltinAttrDecl extends XSAttributeDecl {
        public BuiltinAttrDecl(String name, String tns,
                XSSimpleType type, short scope) {
            fName = name;
            super.fTargetNamespace = tns;
            fType = type;
            fScope = scope;
        }

        public void setValues(String name, String targetNamespace,
                XSSimpleType simpleType, short constraintType, short scope,
                ValidatedInfo valInfo, XSComplexTypeDecl enclosingCT) {
            // ignore this call.
        }

        public void reset () {
            // also ignore this call.
        }
        public XSAnnotation getAnnotation() {
            return null;
        }
    } // class BuiltinAttrDecl

    // the grammars to hold components of the schema namespace
    public final static BuiltinSchemaGrammar SG_SchemaNS = new BuiltinSchemaGrammar(GRAMMAR_XS);

    public final static Schema4Annotations SG_Schema4Annotations = new Schema4Annotations();

    public final static XSSimpleType fAnySimpleType = (XSSimpleType)SG_SchemaNS.getGlobalTypeDecl(SchemaSymbols.ATTVAL_ANYSIMPLETYPE);

    // the grammars to hold components of the schema-instance namespace
    public final static BuiltinSchemaGrammar SG_XSI = new BuiltinSchemaGrammar(GRAMMAR_XSI);

    static final XSComplexTypeDecl[] resize(XSComplexTypeDecl[] oldArray, int newSize) {
        XSComplexTypeDecl[] newArray = new XSComplexTypeDecl[newSize];
        System.arraycopy(oldArray, 0, newArray, 0, Math.min(oldArray.length, newSize));
        return newArray;
    }

    static final XSGroupDecl[] resize(XSGroupDecl[] oldArray, int newSize) {
        XSGroupDecl[] newArray = new XSGroupDecl[newSize];
        System.arraycopy(oldArray, 0, newArray, 0, Math.min(oldArray.length, newSize));
        return newArray;
    }

    static final XSElementDecl[] resize(XSElementDecl[] oldArray, int newSize) {
        XSElementDecl[] newArray = new XSElementDecl[newSize];
        System.arraycopy(oldArray, 0, newArray, 0, Math.min(oldArray.length, newSize));
        return newArray;
    }

    static final SimpleLocator[] resize(SimpleLocator[] oldArray, int newSize) {
        SimpleLocator[] newArray = new SimpleLocator[newSize];
        System.arraycopy(oldArray, 0, newArray, 0, Math.min(oldArray.length, newSize));
        return newArray;
    }

    // XSNamespaceItem methods

    // the max index / the max value of XSObject type
    private static final short MAX_COMP_IDX = XSTypeDefinition.SIMPLE_TYPE;
    private static final boolean[] GLOBAL_COMP = {false,    // null
                                                  true,     // attribute
                                                  true,     // element
                                                  true,     // type
                                                  false,    // attribute use
                                                  true,     // attribute group
                                                  true,     // group
                                                  false,    // model group
                                                  false,    // particle
                                                  false,    // wildcard
                                                  false,    // idc
                                                  true,     // notation
                                                  false,    // annotation
                                                  false,    // facet
                                                  false,    // multi value facet
                                                  true,     // complex type
                                                  true      // simple type
                                                 };

    // store a certain kind of components from all namespaces
    private XSNamedMap[] fComponents = null;

    // store the documents and their locations contributing to this namespace
    // REVISIT: use StringList and XSObjectList for there fields.
    private Vector fDocuments = null;
    private Vector fLocations = null;

    public synchronized void addDocument(Object document, String location) {
        if (fDocuments == null) {
            fDocuments = new Vector();
            fLocations = new Vector();
        }
        fDocuments.addElement(document);
        fLocations.addElement(location);
    }

    /**
     * [schema namespace]
     * @see <a href="http://www.w3.org/TR/xmlschema-1/#nsi-schema_namespace">[schema namespace]</a>
     * @return The target namespace of this item.
     */
    public String getSchemaNamespace() {
        return fTargetNamespace;
    }

    // annotation support
    synchronized DOMParser getDOMParser() {
        if (fDOMParser != null) return fDOMParser;
        // REVISIT:  when schema handles XML 1.1, will need to
        // revisit this (and the practice of not prepending an XML decl to the annotation string
        IntegratedParserConfiguration config = new IntegratedParserConfiguration(fSymbolTable);
        // note that this should never produce errors or require
        // entity resolution, so just a barebones configuration with
        // a couple of feature  set will do fine
        config.setFeature(Constants.SAX_FEATURE_PREFIX + Constants.NAMESPACES_FEATURE, true);
        config.setFeature(Constants.SAX_FEATURE_PREFIX + Constants.VALIDATION_FEATURE, false);
        fDOMParser = new DOMParser(config);
        return fDOMParser;
    }

    synchronized SAXParser getSAXParser() {
        if (fSAXParser != null) return fSAXParser;
        // REVISIT:  when schema handles XML 1.1, will need to
        // revisit this (and the practice of not prepending an XML decl to the annotation string
        IntegratedParserConfiguration config = new IntegratedParserConfiguration(fSymbolTable);
        // note that this should never produce errors or require
        // entity resolution, so just a barebones configuration with
        // a couple of feature  set will do fine
        config.setFeature(Constants.SAX_FEATURE_PREFIX + Constants.NAMESPACES_FEATURE, true);
        config.setFeature(Constants.SAX_FEATURE_PREFIX + Constants.VALIDATION_FEATURE, false);
        fSAXParser = new SAXParser(config);
        return fSAXParser;
    }

    /**
     * [schema components]: a list of top-level components, i.e. element
     * declarations, attribute declarations, etc.
     * @param objectType The type of the declaration, i.e.
     *   <code>ELEMENT_DECLARATION</code>. Note that
     *   <code>XSTypeDefinition.SIMPLE_TYPE</code> and
     *   <code>XSTypeDefinition.COMPLEX_TYPE</code> can also be used as the
     *   <code>objectType</code> to retrieve only complex types or simple
     *   types, instead of all types.
     * @return  A list of top-level definition of the specified type in
     *   <code>objectType</code> or an empty <code>XSNamedMap</code> if no
     *   such definitions exist.
     */
    public synchronized XSNamedMap getComponents(short objectType) {
        if (objectType <= 0 || objectType > MAX_COMP_IDX ||
            !GLOBAL_COMP[objectType]) {
            return XSNamedMapImpl.EMPTY_MAP;
        }

        if (fComponents == null)
            fComponents = new XSNamedMap[MAX_COMP_IDX+1];

        // get the hashtable for this type of components
        if (fComponents[objectType] == null) {
            SymbolHash table = null;
            switch (objectType) {
            case XSConstants.TYPE_DEFINITION:
            case XSTypeDefinition.COMPLEX_TYPE:
            case XSTypeDefinition.SIMPLE_TYPE:
                table = fGlobalTypeDecls;
                break;
            case XSConstants.ATTRIBUTE_DECLARATION:
                table = fGlobalAttrDecls;
                break;
            case XSConstants.ELEMENT_DECLARATION:
                table = fGlobalElemDecls;
                break;
            case XSConstants.ATTRIBUTE_GROUP:
                table = fGlobalAttrGrpDecls;
                break;
            case XSConstants.MODEL_GROUP_DEFINITION:
                table = fGlobalGroupDecls;
                break;
            case XSConstants.NOTATION_DECLARATION:
                table = fGlobalNotationDecls;
                break;
            }

            // for complex/simple types, create a special implementation,
            // which take specific types out of the hash table
            if (objectType == XSTypeDefinition.COMPLEX_TYPE ||
                objectType == XSTypeDefinition.SIMPLE_TYPE) {
                fComponents[objectType] = new XSNamedMap4Types(fTargetNamespace, table, objectType);
            }
            else {
                fComponents[objectType] = new XSNamedMapImpl(fTargetNamespace, table);
            }
        }

        return fComponents[objectType];
    }

    /**
     * Convenience method. Returns a top-level simple or complex type
     * definition.
     * @param name The name of the definition.
     * @return An <code>XSTypeDefinition</code> or null if such definition
     *   does not exist.
     */
    public XSTypeDefinition getTypeDefinition(String name) {
        return getGlobalTypeDecl(name);
    }

    /**
     * Convenience method. Returns a top-level attribute declaration.
     * @param name The name of the declaration.
     * @return A top-level attribute declaration or null if such declaration
     *   does not exist.
     */
    public XSAttributeDeclaration getAttributeDeclaration(String name) {
        return getGlobalAttributeDecl(name);
    }

    /**
     * Convenience method. Returns a top-level element declaration.
     * @param name The name of the declaration.
     * @return A top-level element declaration or null if such declaration
     *   does not exist.
     */
    public XSElementDeclaration getElementDeclaration(String name) {
        return getGlobalElementDecl(name);
    }

    /**
     * Convenience method. Returns a top-level attribute group definition.
     * @param name The name of the definition.
     * @return A top-level attribute group definition or null if such
     *   definition does not exist.
     */
    public XSAttributeGroupDefinition getAttributeGroup(String name) {
        return getGlobalAttributeGroupDecl(name);
    }

    /**
     * Convenience method. Returns a top-level model group definition.
     *
     * @param name      The name of the definition.
     * @return A top-level model group definition definition or null if such
     *         definition does not exist.
     */
    public XSModelGroupDefinition getModelGroupDefinition(String name) {
        return getGlobalGroupDecl(name);
    }

    /**
     * Convenience method. Returns a top-level notation declaration.
     *
     * @param name      The name of the declaration.
     * @return A top-level notation declaration or null if such declaration
     *         does not exist.
     */
    public XSNotationDeclaration getNotationDeclaration(String name) {
        return getGlobalNotationDecl(name);
    }


    /**
     * [document location]
     * @see <a href="http://www.w3.org/TR/xmlschema-1/#sd-document_location">[document location]</a>
     * @return a list of document information item
     */
    public StringList getDocumentLocations() {
        return new StringListImpl(fLocations);
    }

    /**
     * Return an <code>XSModel</code> that represents components in this schema
     * grammar.
     *
     * @return  an <code>XSModel</code> representing this schema grammar
     */
    public XSModel toXSModel() {
        return new XSModelImpl(new SchemaGrammar[]{this});
    }

    public XSModel toXSModel(XSGrammar[] grammars) {
        if (grammars == null || grammars.length == 0)
            return toXSModel();

        int len = grammars.length;
        boolean hasSelf = false;
        for (int i = 0; i < len; i++) {
            if (grammars[i] == this) {
                hasSelf = true;
                break;
            }
        }

        SchemaGrammar[] gs = new SchemaGrammar[hasSelf ? len : len+1];
        for (int i = 0; i < len; i++)
            gs[i] = (SchemaGrammar)grammars[i];
        if (!hasSelf)
            gs[len] = this;
        return new XSModelImpl(gs);
    }

    /**
     * @see com.sun.org.apache.xerces.internal.xs.XSNamespaceItem#getAnnotations()
     */
    public XSObjectList getAnnotations() {
        return new XSObjectListImpl(fAnnotations, fNumAnnotations);
    }

    public void addAnnotation(XSAnnotationImpl annotation) {
        if(annotation == null)
            return;
        if(fAnnotations == null) {
            fAnnotations = new XSAnnotationImpl[2];
        } else if(fNumAnnotations == fAnnotations.length) {
            XSAnnotationImpl[] newArray = new XSAnnotationImpl[fNumAnnotations << 1];
            System.arraycopy(fAnnotations, 0, newArray, 0, fNumAnnotations);
            fAnnotations = newArray;
        }
        fAnnotations[fNumAnnotations++] = annotation;
    }

} // class SchemaGrammar
