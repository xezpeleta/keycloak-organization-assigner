// Keep the factory class as provided previously
package eus.tknika.keycloak.eventlistener;

import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class AutoOrgAssignerListenerProviderFactory implements EventListenerProviderFactory {
    public static final String PROVIDER_ID = "auto-org-assigner";
    
    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new AutoOrgAssignerListenerProvider(session);
    }
    
   @Override
    public void init(Config.Scope config) {
        // Initialize any configuration if needed from spi-<providerId>-* properties
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // Called after the factory is created
    }

    @Override
    public void close() {
        // Called when Keycloak server shuts down
    }

    @Override
    public String getId() {
        // Return the unique ID used to enable this listener in Keycloak config
        return PROVIDER_ID;
    }
}
