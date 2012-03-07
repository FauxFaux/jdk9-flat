/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.db;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.WebServiceFeature;

import com.sun.xml.internal.org.jvnet.ws.databinding.Databinding;
import com.sun.xml.internal.org.jvnet.ws.databinding.DatabindingModeFeature;
import com.sun.xml.internal.org.jvnet.ws.databinding.Databinding.Builder;
import com.sun.xml.internal.org.jvnet.ws.databinding.Databinding.WSDLGenerator;
import org.xml.sax.EntityResolver;

import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.databinding.DatabindingConfig;
import com.sun.xml.internal.ws.api.databinding.DatabindingFactory;
import com.sun.xml.internal.ws.api.databinding.WSDLGenInfo;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.spi.db.DatabindingException;
import com.sun.xml.internal.ws.spi.db.DatabindingProvider;
import com.sun.xml.internal.ws.util.ServiceFinder;

/**
 * DatabindingFactoryImpl
 *
 * @author shih-chang.chen@oracle.com
 */
public class DatabindingFactoryImpl extends DatabindingFactory {

//      static final String WsRuntimeFactoryProperties = DatabindingProvider.class.getName() + ".properties";
        static final String WsRuntimeFactoryDefaultImpl = "com.sun.xml.internal.ws.db.DatabindingProviderImpl";

        protected Map<String, Object> properties = new HashMap<String, Object>();
        protected DatabindingProvider defaultRuntimeFactory;
//      protected Map<String, DatabindingProvider> runtimeFactories = new HashMap<String, DatabindingProvider>();
//      protected Properties wsRuntimeFactoryMap;
        protected List<DatabindingProvider> providers;

    static private List<DatabindingProvider> providers() {
        List<DatabindingProvider> factories = new java.util.ArrayList<DatabindingProvider>();
        for (DatabindingProvider p : ServiceFinder.find(DatabindingProvider.class)) {
            factories.add(p);
        }
        return factories;
    }

        public DatabindingFactoryImpl() {
        }

        public Map<String, Object> properties() {
                return properties;
        }

        <T> T property(Class<T> propType, String propName) {
                if (propName == null) propName = propType.getName();
                return propType.cast(properties.get(propName));
        }

    public DatabindingProvider provider(DatabindingConfig config) {
        String mode = databindingMode(config);
        if (providers == null)
            providers = providers();
        DatabindingProvider provider = null;
        if (providers != null) {
            for (DatabindingProvider p : providers)
                if (p.isFor(mode))
                    provider = p;
        } if (provider == null) {
            // if (defaultRuntimeFactory == null) {
            // defaultRuntimeFactory =
            // newRuntimeFactory(WsRuntimeFactoryDefaultImpl);
            // }
            // provider = defaultRuntimeFactory;
            provider = new DatabindingProviderImpl();
        }
        return provider;
    }

        public Databinding createRuntime(DatabindingConfig config) {
            DatabindingProvider provider = provider(config);
                return provider.create(config);
        }

    public Databinding.WSDLGenerator createWsdlGen(DatabindingConfig config) {
        DatabindingProvider provider = provider(config);
        return provider.wsdlGen(config);
    }

//      DatabindingProvider newRuntimeFactory(String name) {
//              ClassLoader classLoader = classLoader();
//              DatabindingProvider factory = null;
//              try {
//                      Class cls = (classLoader != null) ? classLoader.loadClass(name) : Class.forName(name);
//                      factory = DatabindingProvider.class.cast(cls.newInstance());
//              } catch (Exception e) {
//                      throw new DatabindingException("Unknown DatabindingFactory: " + name, e);
//              }
//              factory.init(properties);
//              return factory;
//      }

        String databindingMode(DatabindingConfig config) {
//              if ( config.getOverrideMappingInfo() != null &&
//                   config.getOverrideMappingInfo().getDatabindingMode() != null)
//                      return config.getOverrideMappingInfo().getDatabindingMode();
//              if ( config.getDefaultMappingInfo() != null &&
//                   config.getDefaultMappingInfo().getDatabindingMode() != null)
//                      return config.getDefaultMappingInfo().getDatabindingMode();

                if ( config.getMappingInfo() != null &&
                     config.getMappingInfo().getDatabindingMode() != null)
                        return config.getMappingInfo().getDatabindingMode();
        if ( config.getFeatures() != null) for (WebServiceFeature f : config.getFeatures()) {
            if (f instanceof DatabindingModeFeature) {
                DatabindingModeFeature dmf = (DatabindingModeFeature) f;
                return dmf.getMode();
            }
        }
                return null;
        }

        ClassLoader classLoader() {
                ClassLoader classLoader = property(ClassLoader.class, null);
                if (classLoader == null) classLoader = Thread.currentThread().getContextClassLoader();
                return classLoader;
        }

        Properties loadPropertiesFile(String fileName) {
                ClassLoader classLoader = classLoader();
                Properties p = new Properties();
                try {
                        InputStream is = null;
                        if (classLoader == null) {
                                is = ClassLoader.getSystemResourceAsStream(fileName);
                        } else {
                                is = classLoader.getResourceAsStream(fileName);
                        }
                        if (is != null) {
                                p.load(is);
                        }
                } catch (Exception e) {
                        throw new WebServiceException(e);
                }
                return p;
        }

    public Builder createBuilder(Class<?> contractClass, Class<?> endpointClass) {
        return new ConfigBuilder(this, contractClass, endpointClass);
    }

    static class ConfigBuilder implements Builder {
        DatabindingConfig config;
        DatabindingFactoryImpl factory;

        ConfigBuilder(DatabindingFactoryImpl f, Class<?> contractClass, Class<?> implBeanClass) {
            factory = f;
            config = new DatabindingConfig();
            config.setContractClass(contractClass);
            config.setEndpointClass(implBeanClass);
        }
        public Builder targetNamespace(String targetNamespace) {
            config.getMappingInfo().setTargetNamespace(targetNamespace);
            return this;
        }
        public Builder serviceName(QName serviceName) {
            config.getMappingInfo().setServiceName(serviceName);
            return this;
        }
        public Builder portName(QName portName) {
            config.getMappingInfo().setPortName(portName);
            return this;
        }
        public Builder wsdlURL(URL wsdlURL) {
            config.setWsdlURL(wsdlURL);
            return this;
        }
        public Builder wsdlSource(Source wsdlSource) {
            config.setWsdlSource(wsdlSource);
            return this;
        }
        public Builder entityResolver(EntityResolver entityResolver) {
            config.setEntityResolver(entityResolver);
            return this;
        }
        public Builder classLoader(ClassLoader classLoader) {
            config.setClassLoader(classLoader);
            return this;
        }
        public Builder feature(WebServiceFeature... f) {
            config.setFeatures(f);
            return this;
        }
        public Builder property(String name, Object value) {
            config.properties().put(name, value);
            if (isfor(BindingID.class, name, value)) {
                config.getMappingInfo().setBindingID((BindingID)value);
            }
            if (isfor(WSBinding.class, name, value)) {
                config.setWSBinding((WSBinding)value);
            }
            if (isfor(WSDLPort.class, name, value)) {
                config.setWsdlPort((WSDLPort)value);
            }
            return this;
        }
        boolean isfor(Class<?> type, String name, Object value) {
            return type.getName().equals(name) && type.isInstance(value);
        }

        public com.sun.xml.internal.org.jvnet.ws.databinding.Databinding build() {
            return factory.createRuntime(config);
        }
        public WSDLGenerator createWSDLGenerator() {
            return factory.createWsdlGen(config);
        }
    }
}
