package net.es.nsi.dds.notification;

import java.io.IOException;
import java.io.InputStream;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBException;
import net.es.nsi.dds.api.jaxb.NotificationListType;
import net.es.nsi.dds.api.jaxb.NotificationType;
import net.es.nsi.dds.schema.JaxbParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple JAX-RS endpoint for receiving notification messages and listing
 * their content.
 *
 * @author hacksaw
 */
@Path("/dds/")
public class NotificationCallback {
    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Simple get to see if things are up and running.
     *
     * @return
     */
    @GET
    @Path("/ping")
    @Produces({ MediaType.APPLICATION_XML })
    @Consumes({ MediaType.APPLICATION_XML })
    public Response ping() {
        log.debug("ping...");
        return Response.ok().build();
    }

    /**
     * This will work with the default Jersey connection provider but not with
     * the Apache provider using chunked POST. The unmarshalling from XML to
     * JAXB fails due to characters at the start of the input stream (length
     * encoding of the first chunk).
     *
     * @param notify
     * @return
     */
    @POST
    @Path("/badEndpoint")
    @Produces({ MediaType.APPLICATION_XML })
    @Consumes({ MediaType.APPLICATION_XML })
    public Response notification(NotificationListType notify) {
        log.debug("badEndpoint: id=" + notify.getId() + ", href=" + notify.getHref() + ", providerId=" + notify.getProviderId());
        for (NotificationType notification : notify.getNotification()) {
            log.debug("badEndpoint: event=" + notification.getEvent().value() + ", documentId=" + notification.getDocument().getId());
        }

        return Response.accepted().build();
    }

    /**
     * This endpoint will parse any characters out of the stream in front of
     * the XML and then unmarshal to JABX.
     * 
     * @param request
     * @return
     * @throws IOException
     * @throws JAXBException
     */
    @POST
    @Path("/goodEndpoint")
    @Produces({ MediaType.APPLICATION_XML })
    @Consumes({ MediaType.APPLICATION_XML })
    public Response notification(InputStream request) throws IOException, JAXBException {
        NotificationListType notify;
        try {
            notify = JaxbParser.getInstance().xml2Jaxb(NotificationListType.class, request);
        } catch (IOException | JAXBException ex) {
            log.error("goodEndpoint: Unable to process XML ", ex);
            throw ex;
        }

        log.debug("goodEndpoint: id=" + notify.getId() + ", href=" + notify.getHref() + ", providerId=" + notify.getProviderId());
        for (NotificationType notification : notify.getNotification()) {
            log.debug("goodEndpoint: event=" + notification.getEvent().value() + ", documentId=" + notification.getDocument().getId());
        }

        return Response.accepted().build();
    }
}
