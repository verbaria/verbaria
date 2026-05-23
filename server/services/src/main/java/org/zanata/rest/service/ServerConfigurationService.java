package org.zanata.rest.service;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.GenericEntity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.enterprise.inject.spi.CDI;
import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;
import org.zanata.security.annotations.CheckRole;
import org.zanata.common.Namespaces;
import org.zanata.dao.ApplicationConfigurationDAO;
import org.zanata.events.ConfigurationChanged;
import org.zanata.model.HApplicationConfiguration;
import org.zanata.rest.MediaTypes;
import org.zanata.rest.dto.Configuration;
import org.zanata.rest.dto.Link;
import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.collect.Lists;

/**
 * This API is experimental only and subject to change or even removal.
 *
 * @author Patrick Huang
 *         <a href="mailto:pahuang@redhat.com">pahuang@redhat.com</a>
 */
@RequestScoped
@Named("serverConfigurationResource")
@Path("/configurations")
@Produces({ "application/xml", "application/json" })
@Consumes({ "application/xml", "application/json" })
@Transactional
@CheckRole("admin")
@Beta
public class ServerConfigurationService {
    /**
     * Type of media requested.
     */
    @HeaderParam("Accept")
    @DefaultValue(MediaType.APPLICATION_JSON)
    @Context
    private MediaType accept;
    @Inject
    private ApplicationConfigurationDAO applicationConfigurationDAO;
    /**
     * Retrieves all existing server configurations.
     *
     * @return The following response status codes will be returned from this
     *         operation:<br>
     *         OK(200) - Response containing value for the config key.<br>
     *         INTERNAL SERVER ERROR(500) - If there is an unexpected error in
     *         the server while performing this operation.
     */
    @GET
    @Wrapped(element = "configurations", namespace = Namespaces.ZANATA_API)
    public Response get() {
        List<HApplicationConfiguration> all =
                applicationConfigurationDAO.findAll();

        List<Configuration> allConfig = all.stream()
                .map(new ToConfigurationFunction(accept))
                .collect(Collectors.toList());
        Object entity =
                new GenericEntity<List<Configuration>>(allConfig){};
        return Response.ok().entity(entity).build();
    }

    /**
     * Retrieves a specific server configuration.
     *
     * @return The following response status codes will be returned from this
     *         operation:<br>
     *         OK(200) - Response containing value for the config key.<br>
     *         NOT_FOUND(404) - If server does not have given configuration
     *         set.<br>
     *         INTERNAL SERVER ERROR(500) - If there is an unexpected error in
     *         the server while performing this operation.
     */
    @GET
    @Path("c/{configKey}")
    public Response get(@PathParam("configKey") @Nonnull String configKey) {
        HApplicationConfiguration config =
                applicationConfigurationDAO.findByKey(configKey);
        if (config == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Configuration configuration =
                new ToConfigurationFunction(accept).apply(config);
        return Response.ok().entity(configuration).build();
    }

    /**
     * Creates or updates a server configuration. If a configuration with the
     * given key already exists, the value will be overwritten with the provided
     * data. Otherwise, a new config will be created.
     *
     * @param configKey
     *            The configuration item to be created/updated.
     * @return The following response status codes will be returned from this
     *         operation:<br>
     *         OK(200) - If an existing configuration was modified.<br>
     *         CREATED(201) - If a new configuration was created.<br>
     *         UNAUTHORIZED(401) - If the user does not have the proper
     *         permissions to perform this operation.<br>
     *         INTERNAL SERVER ERROR(500) - If there is an unexpected error in
     *         the server while performing this operation.
     */
    @PUT
    @Path("/c/{configKey}")
    public Response put(@PathParam("configKey") @Nonnull String configKey,
           @QueryParam("configValue") String configValue) {
        if (!isConfigKeyValid(configKey)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("config key not supported: " + configKey).build();
        }
        HApplicationConfiguration configuration =
                applicationConfigurationDAO.findByKey(configKey);
        boolean created = configuration == null;
        persistApplicationConfig(configKey, configuration, configValue,
                applicationConfigurationDAO);
        if (created) {
            return Response.created(URI.create("c/" + configKey)).build();
        } else {
            return Response.ok().build();
        }
    }

    /**
     * This method is shared by REST service and JSF backend bean. It will raise
     * event to notify configuration change.
     *
     * @see org.zanata.action.ServerConfigurationBean
     * @param key
     *            config key
     * @param appConfig
     *            configuration entity
     * @param newValue
     *            new config value (if null or empty will remove the config
     *            entity)
     * @param applicationConfigurationDAO
     *            dao
     */
    public static void persistApplicationConfig(String key,
            @Nullable HApplicationConfiguration appConfig, String newValue,
            ApplicationConfigurationDAO applicationConfigurationDAO) {
        if (appConfig != null) {
            if (newValue == null || newValue.isEmpty()) {
                applicationConfigurationDAO.makeTransient(appConfig);
            } else {
                appConfig.setValue(newValue);
            }
        } else if (newValue != null && !newValue.isEmpty()) {
            appConfig = new HApplicationConfiguration(key, newValue);
            applicationConfigurationDAO.makePersistent(appConfig);
        }
        // TODO make method non-static, use configurationChangedEvent.fire()
        CDI.current().getBeanManager()
                .getEvent().fire(new ConfigurationChanged(key));
    }

    private boolean isConfigKeyValid(String configKey) {
        return HApplicationConfiguration.getAvailableKeys().contains(configKey);
    }

    /**
     * Converts HApplicationConfiguration to dto Configuration. It also contains
     * a link to the configuration itself.
     */
    private static class ToConfigurationFunction
            implements Function<HApplicationConfiguration, Configuration> {
        private final MediaType accept;

        @Override
        public Configuration apply(HApplicationConfiguration input) {
            Configuration config =
                    new Configuration(input.getKey(), input.getValue());
            config.getLinks(true).add(
                    // a link to get this specific configuration details
                    new Link(URI.create("c/" + input.getKey()), "self",
                            MediaTypes.createFormatSpecificType(
                                    MediaType.APPLICATION_XML, accept)));
            return config;
        }

        @java.beans.ConstructorProperties({ "accept" })
        public ToConfigurationFunction(final MediaType accept) {
            this.accept = accept;
        }
    }
}
