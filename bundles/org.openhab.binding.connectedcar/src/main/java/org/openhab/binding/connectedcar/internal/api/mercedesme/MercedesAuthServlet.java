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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.util.MultiMap;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.UrlEncoded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link MercedesAuthServlet} manages the authorization with the Spotify Web API. The servlet implements the
 * Authorization Code flow and saves the resulting refreshToken with the bridge.
 *
 * @author Andreas Stenlund - Initial contribution
 * @author Matthew Bowman - Initial contribution
 * @author Hilbrand Bouwkamp - Rewrite, moved service part to service class. Uses templates, simplified calls.
 * @author Markus Michels Adapted to ConnectedCar binding
 */
@NonNullByDefault
public class MercedesAuthServlet extends HttpServlet {
    private static final long serialVersionUID = -846012221358326508L;

    private static final String CONTENT_TYPE = "text/html;charset=UTF-8";

    // Simple HTML templates for inserting messages.
    private static final String HTML_USER_AUTHORIZED = "<p class='block authorized'>Thing authorized for user %s.</p>";
    private static final String HTML_ERROR = "<p class='block error'>Call to Mercedes API failed with error: %s</p>";

    private static final Pattern MESSAGE_KEY_PATTERN = Pattern.compile("\\$\\{([^\\}]+)\\}");

    // Keys present in the index.html
    private static final String KEY_PAGE_REFRESH = "pageRefresh";
    private static final String HTML_META_REFRESH_CONTENT = "<meta http-equiv='refresh' content='10; url=%s'>";
    private static final String KEY_AUTHORIZED_USER = "authorizedUser";
    private static final String KEY_ERROR = "error";
    private static final String KEY_REDIRECT_URI = "redirectUri";

    private final Logger logger = LoggerFactory.getLogger(MercedesAuthServlet.class);
    private final MercedesAuthService mercedesAuthService;
    private final String indexTemplate;
    // private final String playerTemplate;

    public MercedesAuthServlet(MercedesAuthService mercedesAuthService, String indexTemplate, String playerTemplate) {
        this.mercedesAuthService = mercedesAuthService;
        this.indexTemplate = indexTemplate;
    }

    @Override
    protected void doGet(@Nullable HttpServletRequest req, @Nullable HttpServletResponse resp)
            throws ServletException, IOException {
        logger.debug("Mercedes auth callback servlet received GET request {}.", req.getRequestURI());
        final String servletBaseURL = req.getRequestURL().toString();
        final Map<String, String> replaceMap = new HashMap<>();

        handleMercedesRedirect(replaceMap, servletBaseURL, req.getQueryString());
        resp.setContentType(CONTENT_TYPE);
        replaceMap.put(KEY_REDIRECT_URI, servletBaseURL);
        // replaceMap.put(KEY_PLAYERS, formatPlayers(playerTemplate, servletBaseURL));
        resp.getWriter().append(replaceKeysFromMap(indexTemplate, replaceMap));
        resp.getWriter().close();
    }

    /**
     * Handles a possible call from Mercedes to the redirect_uri. If that is the case Mercedes will pass the
     * authorization
     * codes via the url and these are processed. In case of an error this is shown to the user. If the user was
     * authorized this is passed on to the handler. Based on all these different outcomes the HTML is generated to
     * inform the user.
     *
     * @param replaceMap a map with key String values that will be mapped in the HTML templates.
     * @param servletBaseURL the servlet base, which should be used as the Mercedes redirect_uri value
     * @param queryString the query part of the GET request this servlet is processing
     */
    private void handleMercedesRedirect(Map<String, String> replaceMap, String servletBaseURL,
            @Nullable String queryString) {
        replaceMap.put(KEY_AUTHORIZED_USER, "");
        replaceMap.put(KEY_ERROR, "");
        replaceMap.put(KEY_PAGE_REFRESH, "");

        if (queryString != null) {
            final MultiMap<String> params = new MultiMap<>();
            UrlEncoded.decodeTo(queryString, params, StandardCharsets.UTF_8.name());
            final String reqCode = params.getString("code");
            final String reqState = params.getString("state");
            final String reqError = params.getString("error");

            replaceMap.put(KEY_PAGE_REFRESH,
                    params.isEmpty() ? "" : String.format(HTML_META_REFRESH_CONTENT, servletBaseURL));
            if (!StringUtil.isBlank(reqError)) {
                logger.debug("Mercedes redirected with an error: {}", reqError);
                replaceMap.put(KEY_ERROR, String.format(HTML_ERROR, reqError));
            } else if (!StringUtil.isBlank(reqState)) {
                try {
                    replaceMap.put(KEY_AUTHORIZED_USER, String.format(HTML_USER_AUTHORIZED,
                            mercedesAuthService.authorize(servletBaseURL, reqState, reqCode)));
                } catch (RuntimeException e) {
                    logger.debug("Exception during authorizaton: ", e);
                    replaceMap.put(KEY_ERROR, String.format(HTML_ERROR, e.getMessage()));
                }
            }
        }
    }

    // /**
    // * Formats the HTML of all available Mercedes Vehicles and returns it as a String
    // *
    // * @param playerTemplate The player template to format the player values in
    // * @param servletBaseURL the redirect_uri to be used in the authorization url created on the authorization button.
    // * @return A String with the players formatted with the player template
    // */
    // private String formatPlayers(String playerTemplate, String servletBaseURL) {
    // final List<MercedesAccountHandler> players = mercedesAuthService.getMercedesAccountHandlers();

    // return players.isEmpty() ? HTML_EMPTY_PLAYERS
    // : players.stream().map(p -> formatPlayer(playerTemplate, p, servletBaseURL))
    // .collect(Collectors.joining());
    // }

    // /**
    // * Formats the HTML of a Spotify Bridge Player and returns it as a String
    // *
    // * @param playerTemplate The player template to format the player values in
    // * @param handler The handler for the player to format
    // * @param servletBaseURL the redirect_uri to be used in the authorization url created on the authorization button.
    // * @return A String with the player formatted with the player template
    // */
    // private String formatPlayer(String playerTemplate, MercedesAccountHandler handler, String servletBaseURL) {
    // final Map<String, String> map = new HashMap<>();

    // map.put(PLAYER_ID, handler.getUID().getAsString());
    // map.put(PLAYER_NAME, handler.getLabel());
    // final String spotifyUser = handler.getUser();

    // if (handler.isAuthorized()) {
    // map.put(PLAYER_AUTHORIZED_CLASS, " authorized");
    // map.put(PLAYER_SPOTIFY_USER_ID, String.format(" (Authorized user: %s)", spotifyUser));
    // } else if (!StringUtil.isBlank(spotifyUser)) {
    // map.put(PLAYER_AUTHORIZED_CLASS, " Unauthorized");
    // map.put(PLAYER_SPOTIFY_USER_ID, String.format(" (Unauthorized user: %s)", spotifyUser));
    // } else {
    // map.put(PLAYER_AUTHORIZED_CLASS, "");
    // map.put(PLAYER_SPOTIFY_USER_ID, "");
    // }

    // map.put(PLAYER_AUTHORIZE, handler.formatAuthorizationUrl(servletBaseURL));
    // return replaceKeysFromMap(playerTemplate, map);
    // }

    /**
     * Replaces all keys from the map found in the template with values from the map. If the key is not found the key
     * will be kept in the template.
     *
     * @param template template to replace keys with values
     * @param map map with key value pairs to replace in the template
     * @return a template with keys replaced
     */
    private String replaceKeysFromMap(String template, Map<String, String> map) {
        final Matcher m = MESSAGE_KEY_PATTERN.matcher(template);
        final StringBuffer sb = new StringBuffer();

        while (m.find()) {
            try {
                final String key = m.group(1);
                m.appendReplacement(sb, Matcher.quoteReplacement(map.getOrDefault(key, "${" + key + '}')));
            } catch (RuntimeException e) {
                logger.debug("Error occurred during template filling, cause ", e);
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
