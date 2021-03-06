package net.es.nsi.dds.jersey;

import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;
import org.glassfish.jersey.moxy.json.MoxyJsonConfig;

/**
 * Configure Moxy for our specific use.
 * 
 * @author hacksaw
 */
@Provider
public class JsonMoxyConfigurationContextResolver implements ContextResolver<MoxyJsonConfig> {
        private final MoxyJsonConfig config;

        public JsonMoxyConfigurationContextResolver() {
            config = new MoxyJsonConfig();
            config.setFormattedOutput(true);
        }

        @Override
        public MoxyJsonConfig getContext(Class<?> objectType) {
            return config;
        }
}
