/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.connectedcar.internal.api.mercedesme;

import static org.openhab.binding.connectedcar.internal.api.mercedesme.MMeJsonDTO.MERCEDES_ALIAS;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.connectedcar.internal.api.ApiException;
import org.openhab.binding.connectedcar.internal.handler.AccountHandler;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link MercedesAuthService} class to manage the servlets and bind authorization servlet to bridges.
 *
 * @author Andreas Stenlund - Initial contribution
 * @author Hilbrand Bouwkamp - Made this the service class instead of only interface. Added templates
 * @author Markus Michels - Adapted to ConnectedCar binding
 */
@Component(service = MercedesAuthService.class, configurationPid = "binding.connectedcar.mme.authService")
@NonNullByDefault
public class MercedesAuthService {

    private static final String TEMPLATE_PATH = "templates/";
    private static final String TEMPLATE_PLAYER = TEMPLATE_PATH + "player.html";
    private static final String TEMPLATE_INDEX = TEMPLATE_PATH + "index.html";
    private static final String ERROR_UKNOWN_BRIDGE = "Returned 'state' by doesn't match any Bridges. Has the bridge been removed?";

    private final Logger logger = LoggerFactory.getLogger(MercedesAuthService.class);

    private final List<AccountHandler> handlers = new ArrayList<>();

    private @NonNullByDefault({}) HttpService httpService;
    private @NonNullByDefault({}) BundleContext bundleContext;

    @Activate
    protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
        try {
            bundleContext = componentContext.getBundleContext();
            httpService.registerServlet(MERCEDES_ALIAS, createServlet(), new Hashtable<>(),
                    httpService.createDefaultHttpContext());
            // httpService.registerResources(MERCEDES_ALIAS + SPOTIFY_IMG_ALIAS, "web", null);
        } catch (NamespaceException | ServletException | IOException e) {
            logger.warn("Error during mercedes servlet startup", e);
        }
    }

    @Deactivate
    protected void deactivate(ComponentContext componentContext) {
        httpService.unregister(MERCEDES_ALIAS);
        // httpService.unregister(MERCEDES_ALIAS + SPOTIFY_IMG_ALIAS);
    }

    /**
     * Creates a new {@link MercedesAuthServlet}.
     *
     * @return the newly created servlet
     * @throws IOException thrown when an HTML template could not be read
     */
    private HttpServlet createServlet() throws IOException {
        return new MercedesAuthServlet(this, readTemplate(TEMPLATE_INDEX), readTemplate(TEMPLATE_PLAYER));
    }

    /**
     * Reads a template from file and returns the content as String.
     *
     * @param templateName name of the template file to read
     * @return The content of the template file
     * @throws IOException thrown when an HTML template could not be read
     */
    private String readTemplate(String templateName) throws IOException {
        final URL index = bundleContext.getBundle().getEntry(templateName);

        if (index == null) {
            throw new FileNotFoundException(
                    String.format("Cannot find '{}' - failed to initialize Mercedes servlet", templateName));
        } else {
            try (InputStream inputStream = index.openStream()) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    /**
     * Call with Mercedes redirect uri returned State and Code values to get the refresh and access tokens and persist
     * these values
     *
     * @param servletBaseURL the servlet base, which will be the Mercedes redirect url
     * @param state The Mercedes returned state value
     * @param code The Mercedes returned code value
     * @return returns the name of the Mercedes user that is authorized
     */
    public String authorize(String servletBaseURL, String state, String code) throws ApiException {
        final AccountHandler listener = getMercedesAuthListener(state);

        if (listener == null) {
            logger.debug(
                    "Mercedes redirected with state '{}' but no matching bridge was found. Possible bridge has been removed.",
                    state);
            throw new ApiException(ERROR_UKNOWN_BRIDGE);
        } else {
            // return listener.authorize(servletBaseURL, code);
            return "";
        }
    }

    /**
     * @param listener Adds the given handler
     */
    public void addMercedesAccountHandler(AccountHandler listener) {
        if (!handlers.contains(listener)) {
            handlers.add(listener);
        }
    }

    /**
     * @param handler Removes the given handler
     */
    public void removeMercedesAccountHandler(AccountHandler handler) {
        handlers.remove(handler);
    }

    /**
     * @return Returns all {@link MercedesAccountHandler}s.
     */
    public List<AccountHandler> getMercedesAccountHandlers() {
        return handlers;
    }

    /**
     * Get the {@link MercedesAccountHandler} that matches the given thing UID.
     *
     * @param thingUID UID of the thing to match the handler with
     * @return the {@link MercedesAccountHandler} matching the thing UID or null
     */
    private @Nullable AccountHandler getMercedesAuthListener(String thingUID) {
        final Optional<AccountHandler> maybeListener = handlers.stream().filter(l -> l.equalsThingUID(thingUID))
                .findFirst();
        return maybeListener.isPresent() ? maybeListener.get() : null;
    }

    @Reference
    protected void setHttpService(HttpService httpService) {
        this.httpService = httpService;
    }

    protected void unsetHttpService(HttpService httpService) {
        this.httpService = null;
    }
}