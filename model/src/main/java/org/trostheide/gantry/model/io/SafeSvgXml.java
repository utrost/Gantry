package org.trostheide.gantry.model.io;

import java.io.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

/** Local SVG parsing with no DTD, entity expansion, XInclude, or remote resource loading. */
public final class SafeSvgXml {
    private SafeSvgXml() { }
    public static Document read(File file) throws IOException {
        // Bound input before constructing a DOM; parser limits also bound nesting/attributes.
        try (InputStream input = new FileInputStream(file)) {
            byte[] bytes = input.readNBytes(32 * 1024 * 1024 + 1);
            if (bytes.length > 32 * 1024 * 1024) throw new IOException("SVG exceeds the 32 MiB import limit");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth", "256");
            factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/elementAttributeLimit", "256");
            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> { throw new SAXException("External SVG resources are disabled"); });
            builder.setErrorHandler(new DefaultHandler() {
                @Override public void error(SAXParseException e) throws SAXException { throw e; }
                @Override public void fatalError(SAXParseException e) throws SAXException { throw e; }
            });
            // Plain DOM never resolves SVG hrefs, CSS URLs, scripts or stylesheets.
            Document document = builder.parse(new ByteArrayInputStream(bytes));
            if (!"svg".equals(document.getDocumentElement().getLocalName())) throw new IOException("Expected an SVG document");
            return document;
        } catch (IOException e) { throw e; }
        catch (Exception e) { throw new IOException("SVG rejected: unsafe or invalid XML (DTDs and external entities are disabled)", e); }
    }
}
