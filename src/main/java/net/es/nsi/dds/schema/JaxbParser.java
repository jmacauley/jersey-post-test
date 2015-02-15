package net.es.nsi.dds.schema;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

/**
 * A singleton to load the very expensive JAXBContext once.
 *
 * @author hacksaw
 */
public class JaxbParser {
    // Get a logger just in case we encounter a problem.
    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final String PACKAGES = "net.es.nsi.dds.api.jaxb";

    private static JAXBContext jc;
    private static Marshaller marshaller;
    private static Unmarshaller unmarshaller;

    /**
     * Private constructor loads the JAXB context once and prevents
     * instantiation from other classes.
     */
    private JaxbParser() {
        try {
            // Load a JAXB context.
            jc = JAXBContext.newInstance(PACKAGES);
            marshaller = jc.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            unmarshaller = jc.createUnmarshaller();
        }
        catch (JAXBException jaxb) {
            log.error("JaxbParser: Failed to load JAXB instance", jaxb);
        }
    }

    /**
     * An internal static class that invokes our private constructor on object
     * creation.
     */
    private static class JaxbParserHolder {
        public static final JaxbParser INSTANCE = new JaxbParser();
    }

    /**
     * Returns an instance of this singleton class.
     *
     * @return An NmlParser object of the NSAType.
     */
    public static JaxbParser getInstance() {
        return JaxbParserHolder.INSTANCE;
    }

    public <T extends Object> T parseFile(Class<T> xmlClass, String file) throws JAXBException, IOException {
        // Parse the specified file.
        try (FileInputStream fileInputStream = new FileInputStream(file); BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream)) {
            return xml2Jaxb(xmlClass, bufferedInputStream);
        }
    }

    public Document jaxb2Dom(JAXBElement<?> jaxbElement) throws JAXBException, ParserConfigurationException {
        // Convert JAXB representation to DOM.
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.newDocument();
        marshaller.marshal(jaxbElement, doc);
        return doc;
    }

    public JAXBElement<?> dom2Jaxb(Document doc) throws JAXBException, ParserConfigurationException {
        return (JAXBElement<?>) unmarshaller.unmarshal(doc);
    }

    public String jaxb2String(JAXBElement<?> jaxbElement) throws Exception {
        // We will write the XML encoding into a string.
        StringWriter writer = new StringWriter();
        String result;
        try {
            marshaller.marshal(jaxbElement, writer);
            result = writer.toString();
        } catch (Exception e) {
            // Something went wrong so get out of here.
            throw e;
        }
        finally {
            try { writer.close(); } catch (IOException ex) {}
        }

        // Return the XML string.
        return result;
	}

    @SuppressWarnings("unchecked")
    public <T extends Object> T xml2Jaxb(Class<T> xmlClass, String xml) throws JAXBException {
        JAXBElement<T> element;
        try (StringReader reader = new StringReader(xml)) {
            element = (JAXBElement<T>) unmarshaller.unmarshal(reader);
        }
        return element.getValue();
    }

    @SuppressWarnings("unchecked")
    public <T extends Object> T xml2Jaxb(Class<T> xmlClass, InputStream is) throws JAXBException, IOException {
        JAXBElement<T> element = (JAXBElement<T>) unmarshaller.unmarshal(getReader(is));
        if (element.getDeclaredType() == xmlClass) {
            return element.getValue();
        }

        throw new JAXBException("Expected XML for class " + xmlClass.getCanonicalName() + " but found " + element.getDeclaredType().getCanonicalName());
    }

    @SuppressWarnings("unchecked")
    public <T extends Object> T xml2Jaxb(Class<T> xmlClass, BufferedInputStream is) throws JAXBException, IOException {
        JAXBElement<T> element = (JAXBElement<T>) unmarshaller.unmarshal(is);
        if (element.getDeclaredType() == xmlClass) {
            return element.getValue();
        }

        throw new JAXBException("Expected XML for class " + xmlClass.getCanonicalName() + " but found " + element.getDeclaredType().getCanonicalName());
    }

    private final static int LOOKAHEAD = 1024;

    private Reader getReader(InputStream is) throws IOException {
        Reader reader = new BufferedReader(new InputStreamReader(is));
        char c[] = "<?".toCharArray();
        int pos = 0;
        reader.mark(LOOKAHEAD);
        while (true) {
            int value = reader.read();

            // Check to see if we hit the end of the stream.
            if (value == -1) {
                throw new IOException("Encounter end of stream before start of XML.");
            }
            else if (value == c[pos]) {
                pos++;
            }
            else {
                if (pos > 0) {
                    pos = 0;
                }
                reader.mark(LOOKAHEAD);
            }

            if (pos == c.length) {
                // We found the character set we were looking for.
                reader.reset();
                break;
            }
        }

        return reader;
    }
}