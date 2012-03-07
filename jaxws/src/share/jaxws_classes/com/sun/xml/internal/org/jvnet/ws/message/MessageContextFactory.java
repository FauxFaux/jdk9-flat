package com.sun.xml.internal.org.jvnet.ws.message;

import java.io.IOException;
import java.io.InputStream;

import com.sun.xml.internal.ws.api.SOAPVersion; // TODO leaking RI APIs
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Messages;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.util.ServiceFinder;

//import java.io.InputStream;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.Source;
import javax.xml.ws.WebServiceFeature;

import com.sun.xml.internal.org.jvnet.ws.EnvelopeStyle;
import com.sun.xml.internal.org.jvnet.ws.message.MessageContext;

public abstract class MessageContextFactory
{
    private static final MessageContextFactory DEFAULT = new com.sun.xml.internal.ws.api.message.MessageContextFactory(new WebServiceFeature[0]);

    /**
     * @deprecated
     */
    public abstract MessageContext doCreate();

    /**
     * @deprecated
     */
    public abstract MessageContext doCreate(SOAPMessage m);
    //public abstract MessageContext doCreate(InputStream x);

    /**
     * @deprecated
     */
    public abstract MessageContext doCreate(Source x, SOAPVersion soapVersion);

    /**
     * @deprecated
     */
    public static MessageContext create(final ClassLoader... classLoader) {
        return serviceFinder(classLoader,
                             new Creator() {
                                 public MessageContext create(final MessageContextFactory f) {
                                     return f.doCreate();
                                 }
                             });
    }

    /**
     * @deprecated
     */
    public static MessageContext create(final SOAPMessage m, final ClassLoader... classLoader) {
        return serviceFinder(classLoader,
                             new Creator() {
                                 public MessageContext create(final MessageContextFactory f) {
                                     return f.doCreate(m);
                                 }
                             });
    }

    /**
     * @deprecated
     */
    public static MessageContext create(final Source m, final SOAPVersion v, final ClassLoader... classLoader) {
        return serviceFinder(classLoader,
                             new Creator() {
                                 public MessageContext create(final MessageContextFactory f) {
                                     return f.doCreate(m, v);
                                 }
                             });
    }

    /**
     * @deprecated
     */
    private static MessageContext serviceFinder(final ClassLoader[] classLoader, final Creator creator) {
        final ClassLoader cl = classLoader.length == 0 ? null : classLoader[0];
        for (MessageContextFactory factory : ServiceFinder.find(MessageContextFactory.class, cl)) {
            final MessageContext messageContext = creator.create(factory);
            if (messageContext != null)
                return messageContext;
        }
        return creator.create(DEFAULT);
    }

    /**
     * @deprecated
     */
    private static interface Creator {
        public MessageContext create(MessageContextFactory f);
    }

    protected abstract MessageContextFactory newFactory(WebServiceFeature ... f);

    public abstract MessageContext createContext(SOAPMessage m);

    public abstract MessageContext createContext(Source m);

    public abstract MessageContext createContext(Source m, EnvelopeStyle.Style envelopeStyle);

    public abstract MessageContext createContext(InputStream in, String contentType) throws IOException;

    static public MessageContextFactory createFactory(WebServiceFeature ... f) {
        return createFactory(null, f);
    }

    static public MessageContextFactory createFactory(ClassLoader cl, WebServiceFeature ...f) {
        for (MessageContextFactory factory : ServiceFinder.find(MessageContextFactory.class, cl)) {
            MessageContextFactory newfac = factory.newFactory(f);
            if (newfac != null) return newfac;
        }
        return new com.sun.xml.internal.ws.api.message.MessageContextFactory(f);
    }
}
