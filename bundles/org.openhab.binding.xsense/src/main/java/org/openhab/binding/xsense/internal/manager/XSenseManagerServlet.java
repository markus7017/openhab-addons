/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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
package org.openhab.binding.xsense.internal.manager;

import static org.openhab.binding.xsense.internal.XSenseBindingConstants.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.xsense.internal.handler.XSenseAccountHandler;
import org.openhab.binding.xsense.internal.manager.XSenseManagerPage.Row;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.whiteboard.propertytypes.HttpWhiteboardServletName;
import org.osgi.service.http.whiteboard.propertytypes.HttpWhiteboardServletPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link XSenseManagerServlet} serves the XSense Manager at /xsense/manager: an overview of
 * all X-Sense things (account → home → station → device tree) with status and core configuration
 * plus rescan/reconnect actions per account. Served on HTTP and HTTPS through the framework.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
@Component(service = Servlet.class)
@HttpWhiteboardServletName(XSenseManagerServlet.SERVLET_URI)
@HttpWhiteboardServletPattern(XSenseManagerServlet.SERVLET_URI + "/*")
public class XSenseManagerServlet extends HttpServlet {

    public static final String SERVLET_URI = "/xsense/manager";
    private static final long serialVersionUID = 1L;

    private final Logger logger = LoggerFactory.getLogger(XSenseManagerServlet.class);
    private final ThingRegistry thingRegistry;

    @Activate
    public XSenseManagerServlet(@Reference ThingRegistry thingRegistry) {
        this.thingRegistry = thingRegistry;
    }

    @Override
    protected void doGet(@Nullable HttpServletRequest request, @Nullable HttpServletResponse response)
            throws IOException {
        if (response == null) {
            return;
        }
        response.setContentType("text/html; charset=utf-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(XSenseManagerPage.render(buildRows(), SERVLET_URI));
    }

    @Override
    protected void doPost(@Nullable HttpServletRequest request, @Nullable HttpServletResponse response)
            throws IOException {
        if (request == null || response == null) {
            return;
        }
        String action = request.getParameter("action");
        String uid = request.getParameter("uid");
        if (action != null && uid != null) {
            Thing thing = thingRegistry.get(new ThingUID(uid));
            if (thing != null && thing.getHandler() instanceof XSenseAccountHandler accountHandler) {
                switch (action) {
                    case "rescan" -> accountHandler.requestRefresh();
                    case "reconnect" -> accountHandler.reconnect();
                    default -> logger.debug("Unknown manager action {}", action);
                }
            }
        }
        // Redirect so a browser refresh never repeats the action
        response.setStatus(HttpServletResponse.SC_SEE_OTHER);
        response.setHeader("Location", SERVLET_URI);
    }

    private List<Row> buildRows() {
        List<Row> rows = new ArrayList<>();
        List<Thing> accounts = new ArrayList<>();
        for (Thing thing : thingRegistry.getAll()) {
            if (THING_TYPE_ACCOUNT.equals(thing.getThingTypeUID())) {
                accounts.add(thing);
            }
        }
        accounts.sort(Comparator.comparing(thing -> thing.getUID().getAsString()));
        for (Thing account : accounts) {
            rows.add(toRow(account, 0, true));
            addChildren(rows, account, 1);
        }
        return rows;
    }

    private void addChildren(List<Row> rows, Thing parent, int level) {
        if (!(parent instanceof Bridge bridge)) {
            return;
        }
        List<Thing> children = new ArrayList<>(bridge.getThings());
        children.sort(Comparator.comparing(thing -> thing.getUID().getAsString()));
        for (Thing child : children) {
            rows.add(toRow(child, level, false));
            addChildren(rows, child, level + 1);
        }
    }

    private Row toRow(Thing thing, int level, boolean accountActions) {
        ThingStatusInfo statusInfo = thing.getStatusInfo();
        String detail = statusInfo.getDescription();
        if (detail == null) {
            detail = "NONE".equals(statusInfo.getStatusDetail().name()) ? "" : statusInfo.getStatusDetail().name();
        }
        String label = thing.getLabel();
        String uniqueId = thing.getProperties().getOrDefault(PROPERTY_UNIQUE_ID, "");
        return new Row(level, thing.getUID().getAsString(), thing.getThingTypeUID().getId(),
                label != null ? label : thing.getUID().getId(), thing.getStatus().name(), detail, configSummary(thing),
                uniqueId, accountActions);
    }

    /**
     * Builds a compact configuration summary; credentials are masked, passwords never shown.
     */
    private String configSummary(Thing thing) {
        StringBuilder summary = new StringBuilder();
        for (Map.Entry<String, Object> entry : thing.getConfiguration().getProperties().entrySet()) {
            String key = entry.getKey();
            if ("password".equals(key)) {
                continue;
            }
            String value = entry.getValue().toString();
            if ("email".equals(key)) {
                value = XSenseManagerPage.maskEmail(value);
            }
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append(key).append("=").append(value);
        }
        return summary.toString();
    }
}
