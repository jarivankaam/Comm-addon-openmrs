package com.azaricomm.provider;

import com.azaricomm.model.DeliveryResult;
import com.azaricomm.model.NotificationMessage;

/**
 * Interface for messaging provider adapters.
 *
 * To add a new provider:
 * 1. Create a class that implements this interface
 * 2. Annotate it with @Component
 * 3. Return a unique name from getName()
 *
 * The ProviderRouter will automatically discover it via Spring's component scanning.
 */
public interface MessagingProvider {

    /**
     * @return unique lowercase identifier for this provider (e.g. "swiftsend")
     */
    String getName();

    /**
     * Send a notification through this provider's API.
     *
     * @param message the notification to deliver
     * @return result indicating success or failure
     */
    DeliveryResult send(NotificationMessage message);
}
