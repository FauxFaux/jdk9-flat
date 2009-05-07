/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */



package com.sun.tools.internal.ws.wscompile;

import com.sun.codemodel.internal.JCodeModel;
import com.sun.tools.internal.ws.resources.ConfigurationMessages;
import com.sun.tools.internal.ws.resources.WscompileMessages;
import com.sun.tools.internal.ws.util.ForkEntityResolver;
import com.sun.tools.internal.ws.wsdl.document.jaxws.JAXWSBindingsConstants;
import com.sun.tools.internal.ws.wsdl.document.schema.SchemaConstants;
import com.sun.tools.internal.xjc.api.SchemaCompiler;
import com.sun.tools.internal.xjc.api.SpecVersion;
import com.sun.tools.internal.xjc.api.XJC;
import com.sun.tools.internal.xjc.reader.Util;
import com.sun.xml.internal.ws.api.streaming.XMLStreamReaderFactory;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderUtil;
import com.sun.xml.internal.ws.util.JAXWSUtils;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import org.w3c.dom.Element;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.LocatorImpl;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamReader;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Vivek Pandey
 */
public class WsimportOptions extends Options {
    /**
     * -wsdlLocation
     */
    public String wsdlLocation;

    /**
     * Actually stores {@link com.sun.org.apache.xml.internal.resolver.tools.CatalogResolver}, but the field
     * type is made to {@link org.xml.sax.EntityResolver} so that XJC can be
     * used even if resolver.jar is not available in the classpath.
     */
    public EntityResolver entityResolver = null;

    /**
     * The -p option that should control the default Java package that
     * will contain the generated code. Null if unspecified.
     */
    public String defaultPackage = null;

    /**
     * JAXB's {@link SchemaCompiler} to be used for handling the schema portion.
     * This object is also configured through options.
     */
    private SchemaCompiler schemaCompiler = XJC.createSchemaCompiler();

    public JCodeModel getCodeModel() {
        if(codeModel == null)
            codeModel = new JCodeModel();
        return codeModel;
    }

    public SchemaCompiler getSchemaCompiler() {
        schemaCompiler.setTargetVersion(SpecVersion.parse(target.getVersion()));
        schemaCompiler.setEntityResolver(entityResolver);
        return schemaCompiler;
    }

    public void setCodeModel(JCodeModel codeModel) {
        this.codeModel = codeModel;
    }

    private JCodeModel codeModel;

    /** -Xno-addressing-databinding option to disable addressing namespace data binding. This is
     * experimental switch and will be working as a temporary workaround till
     * jaxb can provide a better way to selelctively disable compiling of an
     * schema component.
     * **/
    public boolean noAddressingBbinding;

    @Override
    public int parseArguments(String[] args, int i) throws BadCommandLineException {
        int j = super.parseArguments(args ,i);
        if(j>0) return j;   // understood by the super class

        if (args[i].equals("-b")) {
            addBindings(requireArgument("-b", args, ++i));
            return 2;
        } else if (args[i].equals("-wsdllocation")) {
            wsdlLocation = requireArgument("-wsdllocation", args, ++i);
            return 2;
        } else if (args[i].equals("-p")) {
            defaultPackage = requireArgument("-p", args, ++i);
            return 2;
        } else if (args[i].equals("-catalog")) {
            String catalog = requireArgument("-catalog", args, ++i);
            try {
                if (entityResolver == null) {
                    if (catalog != null && catalog.length() > 0)
                        entityResolver = XmlUtil.createEntityResolver(JAXWSUtils.getFileOrURL(JAXWSUtils.absolutize(Util.escapeSpace(catalog))));
                } else if (catalog != null && catalog.length() > 0) {
                    EntityResolver er = XmlUtil.createEntityResolver(JAXWSUtils.getFileOrURL(JAXWSUtils.absolutize(Util.escapeSpace(catalog))));
                    entityResolver = new ForkEntityResolver(er, entityResolver);
                }
            } catch (IOException e) {
                throw new BadCommandLineException(WscompileMessages.WSIMPORT_FAILED_TO_PARSE(catalog, e.getMessage()));
            }
            return 2;
        } else if (args[i].startsWith("-httpproxy:")) {
            String value = args[i].substring(11);
            if (value.length() == 0) {
                throw new BadCommandLineException(WscompileMessages.WSCOMPILE_INVALID_OPTION(args[i]));
            }
            int index = value.indexOf(':');
            if (index == -1) {
                System.setProperty("proxySet", "true");
                System.setProperty("proxyHost", value);
                System.setProperty("proxyPort", "8080");
            } else {
                System.setProperty("proxySet", "true");
                System.setProperty("proxyHost", value.substring(0, index));
                System.setProperty("proxyPort", value.substring(index + 1));
            }
            return 1;
        } else if (args[i].equals("-Xno-addressing-databinding")) {
            noAddressingBbinding = true;
            return 1;
        } else if (args[i].startsWith("-B")) {
            // JAXB option pass through.
            String[] subCmd = new String[args.length-i];
            System.arraycopy(args,i,subCmd,0,subCmd.length);
            subCmd[0] = subCmd[0].substring(2); // trim off the first "-B"

            com.sun.tools.internal.xjc.Options jaxbOptions = schemaCompiler.getOptions();
            try {
                int r = jaxbOptions.parseArgument(subCmd, 0);
                if(r==0) {
                    //Driver.usage(jaxbOptions,false);
                    throw new BadCommandLineException(WscompileMessages.WSIMPORT_NO_SUCH_JAXB_OPTION(subCmd[0]));
                }
                return r;
            } catch (com.sun.tools.internal.xjc.BadCommandLineException e) {
                //Driver.usage(jaxbOptions,false);
                throw new BadCommandLineException(e.getMessage(),e);
            }
        }

        return 0; // what's this option?
    }

    public void validate() throws BadCommandLineException {
        if (wsdls.isEmpty()) {
            throw new BadCommandLineException(WscompileMessages.WSIMPORT_MISSING_FILE());
        }
        if(wsdlLocation == null){
            wsdlLocation = wsdls.get(0).getSystemId();
        }
    }


    @Override
    protected void addFile(String arg) throws BadCommandLineException {
        addFile(arg, wsdls, "*.wsdl");
    }

    private final List<InputSource> wsdls = new ArrayList<InputSource>();
    private final List<InputSource> schemas = new ArrayList<InputSource>();
    private final List<InputSource> bindingFiles = new ArrayList<InputSource>();
    private final List<InputSource> jaxwsCustomBindings = new ArrayList<InputSource>();
    private final List<InputSource> jaxbCustomBindings = new ArrayList<InputSource>();
    private final List<Element> handlerConfigs = new ArrayList<Element>();

    /**
     * There is supposed to be one handler chain per generated SEI.
     * TODO: There is possible bug, how to associate a @HandlerChain
     * with each port on the generated SEI. For now lets preserve the JAXWS 2.0 FCS
     * behaviour and generate only one @HandlerChain on the SEI
     */
    public Element getHandlerChainConfiguration(){
        if(handlerConfigs.size() > 0)
            return handlerConfigs.get(0);
        return null;
    }

    public void addHandlerChainConfiguration(Element config){
        handlerConfigs.add(config);
    }

    public InputSource[] getWSDLs() {
        return wsdls.toArray(new InputSource[wsdls.size()]);
    }

    public InputSource[] getSchemas() {
        return schemas.toArray(new InputSource[schemas.size()]);
    }

    public InputSource[] getWSDLBindings() {
        return jaxwsCustomBindings.toArray(new InputSource[jaxwsCustomBindings.size()]);
    }

    public InputSource[] getSchemaBindings() {
        return jaxbCustomBindings.toArray(new InputSource[jaxbCustomBindings.size()]);
    }

    public void addWSDL(File source) {
        addWSDL(fileToInputSource(source));
    }

    public void addWSDL(InputSource is) {
        wsdls.add(absolutize(is));
    }

    public void addSchema(File source) {
        addSchema(fileToInputSource(source));
    }

    public void addSchema(InputSource is) {
        schemas.add(is);
    }

    private InputSource fileToInputSource(File source) {
        try {
            String url = source.toURL().toExternalForm();
            return new InputSource(Util.escapeSpace(url));
        } catch (MalformedURLException e) {
            return new InputSource(source.getPath());
        }
    }

    /**
     * Recursively scan directories and add all XSD files in it.
     */
    public void addGrammarRecursive(File dir) {
        addRecursive(dir, ".wsdl", wsdls);
        addRecursive(dir, ".xsd", schemas);
    }

    /**
     * Adds a new input schema.
     */
    public void addWSDLBindFile(InputSource is) {
        jaxwsCustomBindings.add(absolutize(is));
    }

    public void addSchemmaBindFile(InputSource is) {
        jaxbCustomBindings.add(absolutize(is));
    }

    private void addRecursive(File dir, String suffix, List<InputSource> result) {
        File[] files = dir.listFiles();
        if (files == null) return; // work defensively

        for (File f : files) {
            if (f.isDirectory())
                addRecursive(f, suffix, result);
            else if (f.getPath().endsWith(suffix))
                result.add(absolutize(fileToInputSource(f)));
        }
    }

    private InputSource absolutize(InputSource is) {
        // absolutize all the system IDs in the input,
        // so that we can map system IDs to DOM trees.
        try {
            URL baseURL = new File(".").getCanonicalFile().toURL();
            is.setSystemId(new URL(baseURL, is.getSystemId()).toExternalForm());
        } catch (IOException e) {
            // ignore
        }
        return is;
    }

    public void addBindings(String name) throws BadCommandLineException {
        addFile(name, bindingFiles, null);
    }

    /**
     * Parses a token to a file (or a set of files)
     * and add them as {@link InputSource} to the specified list.
     *
     * @param suffix If the given token is a directory name, we do a recusive search
     *               and find all files that have the given suffix.
     */
    private void addFile(String name, List<InputSource> target, String suffix) throws BadCommandLineException {
        Object src;
        try {
            src = Util.getFileOrURL(name);
        } catch (IOException e) {
            throw new BadCommandLineException(WscompileMessages.WSIMPORT_NOT_A_FILE_NOR_URL(name));
        }
        if (src instanceof URL) {
            target.add(absolutize(new InputSource(Util.escapeSpace(((URL) src).toExternalForm()))));
        } else {
            File fsrc = (File) src;
            if (fsrc.isDirectory()) {
                addRecursive(fsrc, suffix, target);
            } else {
                target.add(absolutize(fileToInputSource(fsrc)));
            }
        }
    }


    /**
     * Exposing it as a public method to allow external tools such as NB to read from wsdl model and work on it.
     * TODO: WSDL model needs to be exposed - basically at tool time we need to use the runtimw wsdl model
     *
     * Binding files could be jaxws or jaxb. This method identifies jaxws and jaxb binding files and keeps them separately. jaxb binding files are given separately
     * to JAXB in {@link com.sun.tools.internal.ws.processor.modeler.wsdl.JAXBModelBuilder}
     *
     * @param receiver {@link ErrorReceiver}
     */
    public final void parseBindings(ErrorReceiver receiver){
        for (InputSource is : bindingFiles) {
            XMLStreamReader reader =
                    XMLStreamReaderFactory.create(is,true);
            XMLStreamReaderUtil.nextElementContent(reader);
            if (reader.getName().equals(JAXWSBindingsConstants.JAXWS_BINDINGS)) {
                jaxwsCustomBindings.add(is);
            } else if (reader.getName().equals(JAXWSBindingsConstants.JAXB_BINDINGS) ||
                    reader.getName().equals(new QName(SchemaConstants.NS_XSD, "schema"))) {
                jaxbCustomBindings.add(is);
            } else {
                LocatorImpl locator = new LocatorImpl();
                locator.setSystemId(reader.getLocation().getSystemId());
                locator.setPublicId(reader.getLocation().getPublicId());
                locator.setLineNumber(reader.getLocation().getLineNumber());
                locator.setColumnNumber(reader.getLocation().getColumnNumber());
                receiver.warning(locator, ConfigurationMessages.CONFIGURATION_NOT_BINDING_FILE(is.getSystemId()));
            }
        }
    }

}
