package net.es.nsi.dds.notification;

import net.es.nsi.dds.jersey.JsonMoxyConfigurationContextResolver;
import java.net.URI;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBElement;
import net.es.nsi.dds.api.jaxb.NotificationListType;
import net.es.nsi.dds.api.jaxb.ObjectFactory;
import net.es.nsi.dds.schema.JaxbParser;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.junit.Test;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.log4j.xml.DOMConfigurator;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.apache.connector.ApacheClientProperties;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.RequestEntityProcessing;
import org.glassfish.jersey.filter.LoggingFilter;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.moxy.xml.MoxyXmlFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test cases demonstrating current compatibility issue with Grizzly and chunked
 * POSTs using the Apache provider.
 *
 * @author hacksaw
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class NotificationTest {

    private final static String SERVER_URL = "http://localhost:8402/";
    private final static String DOCUMENT = "src/test/resources/documents/notifications.xml";
    private final static String LOG4J = "src/test/resources/config/log4j.xml";

    private static Logger log;
    private static HttpServer server;
    private static Client client;
    private static WebTarget target;
    private final static ObjectFactory factory = new ObjectFactory();

    /**
     * Set up everything needed for these tests to run.
     */
    @BeforeClass
    public static void oneTimeSetUp() {
        // Configure Log4J.
        String log4jConfig = System.getProperty("log4j.configuration");
        if (log4jConfig == null) {
            log4jConfig = LOG4J;
        }
        DOMConfigurator.configureAndWatch(log4jConfig, 45 * 1000);
        log = LoggerFactory.getLogger(NotificationTest.class);

        // Start the http server and register our interfaces.
        ResourceConfig serverConfig = new ResourceConfig()
                .packages("net.es.nsi.dds.notification")
                .register(NotificationCallback.class)
                .register(new MoxyXmlFeature())
                .register(new LoggingFilter(java.util.logging.Logger.getGlobal(), true))
                .registerInstances(new JsonMoxyConfigurationContextResolver());
        server = GrizzlyHttpServerFactory.createHttpServer(URI.create(SERVER_URL), serverConfig);

        // Create a jersey client using the Apache connector.
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.connectorProvider(new ApacheConnectorProvider());

        // We want to use the Apache pooling connection manager for our client.
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setDefaultMaxPerRoute(10);
        connectionManager.setMaxTotal(80);
        clientConfig.property(ApacheClientProperties.CONNECTION_MANAGER, connectionManager);

        // Configure standard Jersey features.
        clientConfig.register(new MoxyXmlFeature());
        clientConfig.register(new LoggingFilter(java.util.logging.Logger.getGlobal(), true));
        clientConfig.property(ClientProperties.REQUEST_ENTITY_PROCESSING, RequestEntityProcessing.CHUNKED);

        // Apache specific configuration for 100-Continue handling.
        RequestConfig.Builder custom = RequestConfig.custom();
        custom.setExpectContinueEnabled(true);
        clientConfig.property(ApacheClientProperties.REQUEST_CONFIG, custom.build());

        // Build our client for use in tests.
        client = ClientBuilder.newClient(clientConfig);
        target = client.target(SERVER_URL).path("dds");
    }

    /**
     * Clean up after our tests are complete.
     */
    @AfterClass
    public static void oneTimeTearDown() {
        server.shutdownNow();
        client.close();
    }

    /**
     * A simple get on the ping URL to make sure things are working.
     */
    @Test
    public void aPing() {
        // Simple ping to determine if interface is available.
        Response response = target.path("ping").request(MediaType.APPLICATION_XML).get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        response.close();
    }

    /**
     * This test sends a notification to the endpoint using the JAXB class in
     * the message signature.  This will fail due to the chunked encoding
     * characters at the start of the stream.
     *
     * @throws Exception
     */
    @Test
    public void bBadNotificationTest() throws Exception {
        // Load the notification document we want to POST.
        NotificationListType notification = JaxbParser.getInstance().parseFile(NotificationListType.class, DOCUMENT);
        JAXBElement<NotificationListType> jaxbRequest = factory.createNotifications(notification);

        // Sent to bad endpoint using JAXB in method signature.
        Response response = target.path("badEndpoint").request(MediaType.APPLICATION_XML).post(Entity.entity(new GenericEntity<JAXBElement<NotificationListType>>(jaxbRequest) {}, MediaType.APPLICATION_XML));
        if (Response.Status.ACCEPTED.getStatusCode() != response.getStatus()) {
            fail("Post request not accepted - " + response.getStatus());
        }

        log.debug("Post request accepted - " + response.getStatus());
        response.close();
    }

    /**
     * This test sends a notification to the endpoint that parses input
     * stream to remove any characters before the start of XML prolog.
     *
     * @throws Exception
     */
    @Test
    public void cGoodNotificationTest() throws Exception {
        // Load the notification document we want to POST.
        NotificationListType notification = JaxbParser.getInstance().parseFile(NotificationListType.class, DOCUMENT);
        JAXBElement<NotificationListType> jaxbRequest = factory.createNotifications(notification);

        //  Sent notification to the good endpoint that will parse out content
        // before the prolog.
        Response response = target.path("goodEndpoint").request(MediaType.APPLICATION_XML).post(Entity.entity(new GenericEntity<JAXBElement<NotificationListType>>(jaxbRequest) {}, MediaType.APPLICATION_XML));
        if (Response.Status.ACCEPTED.getStatusCode() != response.getStatus()) {
            fail("Post request not accepted - " + response.getStatus());
        }

        log.debug("Post request accepted - " + response.getStatus());
        response.close();
    }
}
