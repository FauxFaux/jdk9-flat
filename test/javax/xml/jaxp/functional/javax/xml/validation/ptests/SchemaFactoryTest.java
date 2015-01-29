/*
 * Copyright (c) 1999, 2015, Oracle and/or its affiliates. All rights reserved.
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
package javax.xml.validation.ptests;

import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;
import static javax.xml.validation.ptests.ValidationTestConst.XML_DIR;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;

/*
 * @summary Class containing the test cases for SchemaFactory
 */
@Test(singleThreaded = true)
public class SchemaFactoryTest {

    @BeforeClass
    public void setup() throws SAXException, IOException, ParserConfigurationException {
        sf = newSchemaFactory();

        assertNotNull(sf);

        xsd1 = Files.readAllBytes(Paths.get(XML_DIR + "test.xsd"));
        xsd2 = Files.readAllBytes(Paths.get(XML_DIR + "test1.xsd"));

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        xsdDoc1 = db.parse(newInputStream(xsd1));
        xsdDoc2 = db.parse(newInputStream(xsd2));

        xml = Files.readAllBytes(Paths.get(XML_DIR + "test.xml"));
    }

    @Test(expectedExceptions = SAXParseException.class)
    public void testNewSchemaDefault() throws SAXException, IOException {
        validate(sf.newSchema());
    }

    @Test
    public void testNewSchemaWithFile() throws SAXException, IOException {
        validate(sf.newSchema(new File(XML_DIR + "test.xsd")));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNewSchemaWithNullFile() throws SAXException {
        sf.newSchema((File) null);
    }

    @DataProvider(name = "valid-source")
    public Object[][] getValidSource() {
        return new Object[][] {
                { streamSource(xsd1) },
                { saxSource(xsd1) },
                { domSource(xsdDoc1) } };

    }

    @Test(dataProvider = "valid-source")
    public void testNewSchemaWithValidSource(Source schema) throws SAXException, IOException {
        validate(sf.newSchema(schema));
    }

    @DataProvider(name = "invalid-source")
    public Object[][] getInvalidSource() {
        return new Object[][] {
                { nullStreamSource() },
                { nullSaxSource() } };
    }

    @Test(dataProvider = "invalid-source", expectedExceptions = SAXParseException.class)
    public void testNewSchemaWithInvalidSource(Source schema) throws SAXException {
        sf.newSchema(schema);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNewSchemaWithNullSource() throws SAXException {
        sf.newSchema((Source)null);
    }

    @DataProvider(name = "valid-sources")
    public Object[][] getValidSources() {
        return new Object[][] {
                { streamSource(xsd1), streamSource(xsd2) },
                { saxSource(xsd1), saxSource(xsd2) },
                { domSource(xsdDoc1), domSource(xsdDoc2) } };

    }

    @Test(dataProvider = "valid-sources")
    public void testNewSchemaWithValidSourceArray(Source schema1, Source schema2) throws SAXException, IOException {
        validate(sf.newSchema(new Source[] { schema1, schema2 }));
    }

    @DataProvider(name = "invalid-sources")
    public Object[][] getInvalidSources() {
        return new Object[][] {
                { streamSource(xsd1), nullStreamSource() },
                { nullStreamSource(), nullStreamSource() },
                { saxSource(xsd1), nullSaxSource() },
                { nullSaxSource(), nullSaxSource() } };
    }

    @Test(dataProvider = "invalid-sources", expectedExceptions = SAXParseException.class)
    public void testNewSchemaWithInvalidSourceArray(Source schema1, Source schema2) throws SAXException {
        sf.newSchema(new Source[] { schema1, schema2 });
    }

    @DataProvider(name = "null-sources")
    public Object[][] getNullSources() {
        return new Object[][] {
                { new Source[] { domSource(xsdDoc1), null } },
                { new Source[] { null, null } },
                { null } };

    }

    @Test(dataProvider = "null-sources", expectedExceptions = NullPointerException.class)
    public void testNewSchemaWithNullSourceArray(Source[] schemas) throws SAXException {
        sf.newSchema(schemas);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNewSchemaWithNullUrl() throws SAXException {
        sf.newSchema((URL) null);
    }


    @Test
    public void testErrorHandler() {
        SchemaFactory sf = newSchemaFactory();
        assertNull(sf.getErrorHandler(), "When SchemaFactory is created, initially ErrorHandler should not be set.");

        ErrorHandler handler = new MyErrorHandler();
        sf.setErrorHandler(handler);
        assertSame(sf.getErrorHandler(), handler);

        sf.setErrorHandler(null);
        assertNull(sf.getErrorHandler());
    }

    @Test(expectedExceptions = SAXNotRecognizedException.class)
    public void testGetUnrecognizedProperty() throws SAXNotRecognizedException, SAXNotSupportedException {
        SchemaFactory sf = newSchemaFactory();
        sf.getProperty(UNRECOGNIZED_NAME);

    }

    @Test(expectedExceptions = SAXNotRecognizedException.class)
    public void testSetUnrecognizedProperty() throws SAXNotRecognizedException, SAXNotSupportedException {
        SchemaFactory sf = newSchemaFactory();
        sf.setProperty(UNRECOGNIZED_NAME, "test");
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testGetNullProperty() throws SAXNotRecognizedException, SAXNotSupportedException {
        SchemaFactory sf = newSchemaFactory();
        assertNotNull(sf);
        sf.getProperty(null);

    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testSetNullProperty() throws SAXNotRecognizedException, SAXNotSupportedException {
        SchemaFactory sf = newSchemaFactory();
        assertNotNull(sf);
        sf.setProperty(null, "test");
    }

    @Test(expectedExceptions = SAXNotRecognizedException.class)
    public void testGetUnrecognizedFeature() throws SAXNotRecognizedException, SAXNotSupportedException {
        SchemaFactory sf = newSchemaFactory();
        sf.getFeature(UNRECOGNIZED_NAME);

    }

    @Test(expectedExceptions = SAXNotRecognizedException.class)
    public void testSetUnrecognizedFeature() throws SAXNotRecognizedException, SAXNotSupportedException {
        SchemaFactory sf = newSchemaFactory();
        sf.setFeature(UNRECOGNIZED_NAME, true);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testGetNullFeature() throws SAXNotRecognizedException, SAXNotSupportedException {
        SchemaFactory sf = newSchemaFactory();
        assertNotNull(sf);
        sf.getFeature(null);

    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testSetNullFeature() throws SAXNotRecognizedException, SAXNotSupportedException {
        SchemaFactory sf = newSchemaFactory();
        assertNotNull(sf);
        sf.setFeature(null, true);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testInvalidSchemaLanguage() {
        final String INVALID_SCHEMA_LANGUAGE = "http://relaxng.org/ns/structure/1.0";
        SchemaFactory.newInstance(INVALID_SCHEMA_LANGUAGE);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullSchemaLanguage() {
        SchemaFactory.newInstance(null);
    }

    private void validate(Schema schema) throws SAXException, IOException {
        schema.newValidator().validate(new StreamSource(new ByteArrayInputStream(xml)));
    }
    private InputStream newInputStream(byte[] xsd) {
        return new ByteArrayInputStream(xsd);
    }

    private Source streamSource(byte[] xsd) {
        return new StreamSource(newInputStream(xsd));
    }

    private Source nullStreamSource() {
        return new StreamSource((InputStream) null);
    }

    private Source saxSource(byte[] xsd) {
        return new SAXSource(new InputSource(newInputStream(xsd)));
    }

    private Source nullSaxSource() {
        return new SAXSource(new InputSource((InputStream) null));
    }

    private Source domSource(Document xsdDoc) {
        return new DOMSource(xsdDoc);
    }

    private SchemaFactory newSchemaFactory() {
        return SchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI);
    }

    private static final String UNRECOGNIZED_NAME = "http://xml.org/sax/features/namespace-prefixes";

    private SchemaFactory sf;
    private byte[] xsd1;
    private byte[] xsd2;
    private Document xsdDoc1;
    private Document xsdDoc2;
    private byte[] xml;
}
