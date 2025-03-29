package eus.tknika.keycloak.eventlistener;

import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.OrganizationDomainModel;
import org.keycloak.models.OrganizationModel;
import org.keycloak.organization.OrganizationProvider;

import java.util.Set;
import java.util.stream.Stream;

public class AutoOrgAssignerListenerProvider implements EventListenerProvider {

    private static final Logger log = Logger.getLogger(AutoOrgAssignerListenerProvider.class);
    private final KeycloakSession session;

    public AutoOrgAssignerListenerProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public void onEvent(Event event) {
        if (event.getType() != EventType.REGISTER) {
            return;
        }

        log.infof("Received REGISTER event for user ID: %s in realm ID: %s", event.getUserId(), event.getRealmId());

        RealmModel realm = session.realms().getRealm(event.getRealmId());
        if (realm == null) {
            log.errorf("Realm %s not found.", event.getRealmId());
            return;
        }

        UserModel user = session.users().getUserById(realm, event.getUserId());
        if (user == null) {
            log.warnf("User %s not found immediately after registration event in realm %s. Cannot assign to organization.", event.getUserId(), realm.getName());
            return;
        }

        String userEmail = user.getEmail();
        if (userEmail == null || userEmail.trim().isEmpty() || !userEmail.contains("@")) {
            log.infof("User %s (username: %s) does not have a valid email address ('%s'), skipping organization assignment.", user.getId(), user.getUsername(), userEmail);
            return;
        }

        String userDomain = userEmail.substring(userEmail.indexOf('@') + 1).toLowerCase();
        log.infof("Extracted domain '%s' for user %s (email: %s)", userDomain, user.getId(), userEmail);

        // --- Use OrganizationProvider directly ---
        OrganizationProvider orgProvider = session.getProvider(OrganizationProvider.class);

        if (orgProvider == null) {
             log.errorf("OrganizationProvider is not available or enabled for realm %s.", realm.getName());
             return;
        }

        // Check if the organization feature is enabled for the realm, although getProvider might return null if disabled anyway.
        if (!orgProvider.isEnabled()) {
            log.warnf("Organization feature is disabled for realm %s.", realm.getName());
            return;
        }
        // --- End OrganizationProvider retrieval ---


        // --- Find Matching Organization using OrganizationProvider ---
        OrganizationModel matchedOrg = null;
        // The Javadoc for 26.1.4 shows getByDomainName(String domainName)
        // Let's try using that first for efficiency, though it might return only one or throw error if multiple match.
        // If that fails or isn't suitable, we fall back to iterating all.
        try {
             // NOTE: This assumes a domain maps uniquely to ONE organization.
             // If multiple orgs can have the same domain verified, this might not be sufficient.
             matchedOrg = orgProvider.getByDomainName(userDomain);
             if (matchedOrg != null) {
                 log.infof("Found potential match using getByDomainName: Org '%s' (ID: %s)", matchedOrg.getName(), matchedOrg.getId());
             }
        } catch (Exception e) {
            // Log if getByDomainName fails (e.g., if it expects only one result and finds multiple)
             log.warnf(e, "Call to orgProvider.getByDomainName('%s') failed or returned unexpected results. Falling back to iterating all organizations.", userDomain);
             matchedOrg = null; // Ensure fallback occurs
        }


        // Fallback: Iterate if getByDomainName didn't work or isn't trusted for multiple matches
        if (matchedOrg == null) {
            log.info("Attempting to find matching organization by iterating all organizations...");
            try (Stream<OrganizationModel> orgStream = orgProvider.getAllStream()) { // Use provider's stream method
                matchedOrg = orgStream
                    .filter(org -> {
                    	return org.getDomains() // Returns Stream<OrganizationDomainModel>
                       .anyMatch(domainModel -> userDomain.equalsIgnoreCase(domainModel.getName())); 
                    })
                    .findFirst()
                    .orElse(null);
            } catch (Exception e) {
                 log.errorf(e, "Error occurred while searching for organizations using getAllStream in realm %s", realm.getName());
                 return;
            }
        }
        // --- End Finding Matching Organization ---


        // --- Add Member using OrganizationProvider ---
        if (matchedOrg != null) {
            log.infof("Confirmed matching organization '%s' (ID: %s) for domain '%s' for user %s", matchedOrg.getName(), matchedOrg.getId(), userDomain, user.getId());
            try {
                // Check membership using the provider
                if (orgProvider.isMember(matchedOrg, user)) {
                     log.infof("User %s (%s) is already a member of organization '%s' (ID: %s). No action needed.", user.getId(), userEmail, matchedOrg.getName(), matchedOrg.getId());
                } else {
                    // Add the user as a non-managed member using the provider
                    boolean added = orgProvider.addMember(matchedOrg, user);
                    if (added) {
                        log.infof("Successfully added user %s (%s) to organization '%s' (ID: %s) using OrganizationProvider.", user.getId(), userEmail, matchedOrg.getName(), matchedOrg.getId());
                    } else {
                        // This might happen if the add operation failed silently or the user was added concurrently.
                        log.warnf("OrganizationProvider.addMember for user %s to org %s returned false.", user.getId(), matchedOrg.getId());
                    }
                }
            } catch (Exception e) {
                log.errorf(e, "Failed to add user %s (email: %s) to organization '%s' (ID: %s) using OrganizationProvider", user.getId(), userEmail, matchedOrg.getName(), matchedOrg.getId());
            }
        } else {
            log.infof("No matching organization found for domain '%s' for user %s (email: %s) in realm %s.", userDomain, user.getId(), userEmail, realm.getName());
        }
        // --- End Add Member ---
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
        // No action needed
    }

    @Override
    public void close() {
        // No resources to close
    }
}
