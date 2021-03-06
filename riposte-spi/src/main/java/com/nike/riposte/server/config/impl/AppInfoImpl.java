package com.nike.riposte.server.config.impl;

import com.nike.riposte.server.config.AppInfo;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Simple basic implementation of {@link AppInfo}. Also includes a few static factory methods for attempting to infer
 * app ID / environment, and for creating "local" instances that aren't part of a datacenter.
 *
 * @author Nic Munroe
 */
@SuppressWarnings("WeakerAccess")
public class AppInfoImpl implements AppInfo {

    private static final Logger logger = LoggerFactory.getLogger(AppInfoImpl.class);
    // NOTE: Can't be final - would break the ability to do exception unit testing
    private static LocalHostnameGetter LOCAL_HOSTNAME_GETTER = new LocalHostnameGetter();

    public final @NotNull String appId;
    public final @NotNull String environment;
    public final @NotNull String dataCenter;
    public final @NotNull String instanceId;

    // Intentionally protected - this is here for deserialization support only.
    protected AppInfoImpl() {
        this(UNKNOWN_VALUE, UNKNOWN_VALUE, UNKNOWN_VALUE, UNKNOWN_VALUE);
    }

    /**
     * Creates a new instance with the given values.
     *
     * @param appId The AppId/name for this service (like {@code foo-svc}). As per the javadocs for {@link
     * AppInfo#appId()} - this should never be null, and should never be {@link #UNKNOWN_VALUE}.
     * @param environment The environment for the AppId (like {@code test} or {@code prod}). As per the javadocs for
     * {@link AppInfo#environment()} - this should never be null, and should never be {@link #UNKNOWN_VALUE}.
     * @param dataCenter The datacenter/region for the AppId (like {@code us-west-2}). You can safely pass null if you
     * can't determine this information, and this will default to {@link #UNKNOWN_VALUE}.
     * @param instanceId Te instanceId/ip/hostname of this machine/VM running the AppId service. You can safely pass
     * null if you can't determine this information, and this will default to {@link #UNKNOWN_VALUE}.
     */
    @SuppressWarnings("ConstantConditions")
    public AppInfoImpl(
        @NotNull String appId,
        @NotNull String environment,
        @Nullable String dataCenter,
        @Nullable String instanceId
    ) {
        if (appId == null) {
            appId = UNKNOWN_VALUE;
        }

        if (environment == null) {
            environment = UNKNOWN_VALUE;
        }

        if (dataCenter == null) {
            dataCenter = UNKNOWN_VALUE;
        }

        if (instanceId == null) {
            instanceId = UNKNOWN_VALUE;
        }

        this.appId = appId;
        this.environment = environment;
        this.dataCenter = dataCenter;
        this.instanceId = instanceId;
    }

    @Override
    public @NotNull String appId() {
        return appId;
    }

    @Override
    public @NotNull String environment() {
        return environment;
    }

    @Override
    public @NotNull String dataCenter() {
        return dataCenter;
    }

    @Override
    public @NotNull String instanceId() {
        return instanceId;
    }

    /**
     * @return An {@link AppInfoImpl} instance with the {@link #appId} pulled from {@link #detectAppId()}, {@link
     * #environment} set to "local", {@link #dataCenter} set to "local", and {@link #instanceId} pulled from the local
     * box hostname using {@code InetAddress.getLocalHost().getHostName()} (or {@link AppInfo#UNKNOWN_VALUE} if that
     * threw an error). If you know that {@link #detectAppId()} won't work for your app you'll want to determine the app
     * ID a different way and call {@link #createLocalInstance(String)} instead, because if {@link #detectAppId()}
     * returns null then this method will throw an {@link IllegalStateException}.
     */
    public static @NotNull AppInfoImpl createLocalInstance() {
        String appId = detectAppId();
        if (appId == null) {
            throw new IllegalStateException("Unable to autodetect app ID. Please call createLocalInstance(String) "
                                            + "instead and pass the app ID manually");
        }

        return createLocalInstance(appId);
    }

    /**
     * Uses {@link #LOCAL_HOSTNAME_GETTER} to retrieve the local hostname.
     */
    protected static String getLocalHostName() throws UnknownHostException {
        return LOCAL_HOSTNAME_GETTER.getLocalHostname();
    }

    /**
     * @param appId
     *     The app ID to use.
     *
     * @return An {@link AppInfoImpl} instance with the {@link #appId} set to the given argument, {@link #environment}
     * set to "local", {@link #dataCenter} set to "local", and {@link #instanceId} pulled from the local box hostname
     * using {@code InetAddress.getLocalHost().getHostName()} (or {@link AppInfo#UNKNOWN_VALUE} if that threw an
     * error).
     */
    public static @NotNull AppInfoImpl createLocalInstance(@NotNull String appId) {
        String environment = "local";
        String datacenter = "local";

        String instanceId = null;
        try {
            instanceId = getLocalHostName();
        }
        catch (UnknownHostException e) {
            logger.error("Unable to extract localhost address", e);
        }

        if (instanceId == null)
            instanceId = UNKNOWN_VALUE;

        return new AppInfoImpl(appId, environment, datacenter, instanceId);
    }

    /**
     * @return The appId if it was found in the known set of system properties that represent app ID, or null if it
     * could not be found. It searches for a non-null System property using {@link System#getProperty(String)} and
     * returns the first non-null one it finds. The search is done in the following order: <ol> <li>@appId</li>
     * <li>archaius.deployment.applicationId</li> <li>eureka.name</li> </ol> NOTE: A return value of null does not
     * necessarily mean there is no appId, it just means this code doesn't know how to extract it.
     */
    public static @Nullable String detectAppId() {
        // Attempt to get it from @appId or archaius.deployment.applicationId System properties first - used by Archaius
        //      or any Riposte app that follows the Archaius conventions.
        String appId = System.getProperty("@appId");
        if (appId != null)
            return appId;

        appId = System.getProperty("archaius.deployment.applicationId");
        if (appId != null)
            return appId;

        // Try eureka.name next.
        appId = System.getProperty("eureka.name");
        if (appId != null)
            return appId;

        // Unable to detect. Return null.
        return null;
    }

    /**
     * @return The environment value if it was found in the known set of system properties that represent environment
     * (local, test, prod, etc), or null if it could not be found. It searches for a non-null System property using
     * {@link System#getProperty(String)} and returns the first non-null one it finds. The search is done in the
     * following order: <ol> <li>@environment</li> <li>archaius.deployment.environment</li> </ol> NOTE: A return value
     * of null does not necessarily mean there is no environment, it just means this code doesn't know how to extract
     * it.
     */
    public static @Nullable String detectEnvironment() {
        // Attempt to get it from @environment or archaius.deployment.environment System properties first - used by
        //      Archaius or any Riposte app that follows the Archaius conventions.
        String environment = System.getProperty("@environment");
        if (environment != null)
            return environment;

        environment = System.getProperty("archaius.deployment.environment");
        if (environment != null)
            return environment;

        // Unable to detect. Return null.
        return null;
    }

    /**
     * Simple class that retrieves the local hostname using {@code InetAddress.getLocalHost().getHostName()}. This is
     * here as a separate class so that it can be tested and overridden easily in unit tests.
     */
    protected static class LocalHostnameGetter {

        public String getLocalHostname() throws UnknownHostException {
            return InetAddress.getLocalHost().getHostName();
        }
    }

}
