package run.cloudclaw.auth.service;

import run.cloudclaw.common.model.ChannelConfig;

/**
 * Lifecycle management interface for channel long-connection clients.
 *
 * <p>Defined in cloudclaw-auth so that {@link ChannelConfigService} can depend on it
 * without creating a circular module dependency. The actual implementation
 * (e.g. {@code FeishuLongConnectionManager}) lives in cloudclaw-app and implements
 * this interface. At runtime they are all in the same Spring context.</p>
 */
public interface ChannelLifecycleManager {

    /**
     * Start a long-connection client for the given channel config.
     *
     * @param config the channel configuration (must have decrypted credentials available)
     */
    void startClient(ChannelConfig config);

    /**
     * Stop the long-connection client for the given config ID.
     *
     * @param configId the channel config ID
     */
    void stopClient(String configId);

    /**
     * Restart the long-connection client (stop then start).
     * Looks up the config by ID from the service.
     *
     * @param configId the channel config ID
     */
    void restartClient(String configId);

    /**
     * Check if a client is currently active for the given config ID.
     *
     * @param configId the channel config ID
     * @return true if a WS client is running
     */
    boolean isHandledByWs(String configId);
}
