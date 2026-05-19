package com.azaricomm.provider;

import com.azaricomm.model.DeliveryResult;
import com.azaricomm.model.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Routes notifications to the correct messaging provider.
 *
 * All MessagingProvider implementations are auto-discovered by Spring.
 * To add a new provider, just create a new @Component that implements MessagingProvider.
 * No changes to this class needed.
 */
@Component
public class ProviderRouter {

    private static final Logger log = LoggerFactory.getLogger(ProviderRouter.class);

    private final Map<String, MessagingProvider> providers;

    /**
     * Spring injects all MessagingProvider beans automatically.
     */
    public ProviderRouter(List<MessagingProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(
                        p -> p.getName().toLowerCase(),
                        Function.identity()
                ));
        log.info("Registered messaging providers: {}", providers.keySet());
    }

    /**
     * Route a notification to the provider specified in the message.
     */
    public DeliveryResult route(NotificationMessage message) {
        String providerName = message.getProvider();

        if (providerName == null || providerName.isBlank()) {
            log.error("No provider specified in notification for patient {}", message.getPatientId());
            return DeliveryResult.failure("unknown", "No provider specified in notification");
        }

        MessagingProvider provider = providers.get(providerName.toLowerCase());

        if (provider == null) {
            log.error("Unknown provider '{}' requested by org {}", providerName, message.getOrganizationId());
            return DeliveryResult.failure(providerName, "Provider not found: " + providerName);
        }

        log.info("Routing notification to provider '{}' for org '{}', patient '{}'",
                providerName, message.getOrganizationId(), message.getPatientId());

        return provider.send(message);
    }

    /**
     * @return set of available provider names
     */
    public java.util.Set<String> getAvailableProviders() {
        return providers.keySet();
    }
}
