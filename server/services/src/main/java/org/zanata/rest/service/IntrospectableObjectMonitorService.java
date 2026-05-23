package org.zanata.rest.service;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.GenericEntity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.transaction.Transactional;
import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;
import org.zanata.common.Namespaces;
import org.zanata.rest.dto.Link;
import org.zanata.security.annotations.CheckRole;
import org.zanata.util.Introspectable;
import com.google.common.annotations.Beta;
import com.google.common.collect.Lists;

/**
 * This API is experimental only and subject to change or even removal.
 *
 * @author Patrick Huang
 *         <a href="mailto:pahuang@redhat.com">pahuang@redhat.com</a>
 */
@RequestScoped
@Path("/monitor")
@Produces({ "application/json" })
@Consumes({ "application/xml" })
@Transactional
@CheckRole("admin")
@Beta
public class IntrospectableObjectMonitorService {
    @Inject
    private Instance<Introspectable> introspectables;

    /**
     * Return all Introspectable objects link.
     *
     * @return The following response status codes will be returned from this
     *         operation:<br>
     *         OK(200) - all available introspectable objects with hypermedia
     *         link.<br>
     *         UNAUTHORIZED(401) - if not admin role.<br>
     *         INTERNAL SERVER ERROR(500) - If there is an unexpected error in
     *         the server while performing this operation.
     */
    @GET
    @Wrapped(element = "introspectable", namespace = Namespaces.ZANATA_API)
    public Response get() {
        List<LinkRoot> all = Lists.newArrayList(introspectables.iterator())
                .stream()
                .map(introspectable -> new LinkRoot(
                        URI.create("/" + introspectable.getIntrospectableId()),
                        "self", MediaType.APPLICATION_JSON))
                .collect(Collectors.toList());
        Object entity = new GenericEntity<List<LinkRoot>>(all){};
        return Response.ok().entity(entity).build();
    }

    /**
     * Return a single introspectable fields as String.
     *
     * @param id
     *            introspectable id
     * @return The following response status codes will be returned from this
     *         operation:<br>
     *         OK(200) - Response containing a string of all fields and
     *         values.<br>
     *         NOT_FOUND(404) - given id does not represent an
     *         introspectable.<br>
     *         INTERNAL SERVER ERROR(500) - If there is an unexpected error in
     *         the server while performing this operation.
     */
    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") final String id) {
        Optional<Introspectable> optional =
                Lists.newArrayList(introspectables.iterator()).stream()
                        .filter(input -> input.getIntrospectableId().equals(id))
                        .findFirst();
        if (!optional.isPresent()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        final Introspectable introspectable = optional.get();
        String json = introspectable.getFieldValuesAsJSON();
        return Response.ok(json).build();
    }

    @XmlRootElement(name = "link")
    public static class LinkRoot extends Link {

        public LinkRoot() {
        }

        public LinkRoot(URI href, String rel, String type) {
            super(href, rel, type);
        }
    }
}
