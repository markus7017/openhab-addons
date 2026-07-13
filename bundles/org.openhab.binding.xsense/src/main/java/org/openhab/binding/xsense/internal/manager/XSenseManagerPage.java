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

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.xsense.internal.api.XSenseDtoUtil;

/**
 * The {@link XSenseManagerPage} renders the XSense Manager HTML page from a prepared row model.
 * Pure string rendering without framework dependencies so it can be unit tested.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class XSenseManagerPage {

    /**
     * Inline SVG data URI so the header icon always renders without depending on x-sense.com being
     * reachable from the browser (the servlet page never loads any other external resource).
     */
    static final String LOGO_URL = "data:image/svg+xml,"
            + "%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E"
            + "%3Ccircle cx='12' cy='12' r='11' fill='%23e64a19'/%3E"
            + "%3Cpath d='M12 5c-2.5 3-4 5.2-4 7.5A4 4 0 0 0 12 16.5a4 4 0 0 0 4-4C16 10.2 14.5 8 12 5z' "
            + "fill='%23fff'/%3E%3C/svg%3E";

    /**
     * One row of the manager table; level controls the tree indentation (0 = account).
     */
    public static class Row {
        /** Tree indentation depth: 0 = account, 1 = home, 2 = station, 3 = sensor. */
        public final int level;
        public final String uid;
        /** Thing type id, e.g. "account" or "smoke". */
        public final String type;
        public final String label;
        /** Thing status name, e.g. "ONLINE"; drives the status badge CSS class. */
        public final String status;
        /** Status detail text, e.g. an offline reason; empty when not applicable. */
        public final String statusDetail;
        /** Pre-formatted, credential-masked configuration summary. */
        public final String config;
        public final String uniqueId;
        /** Whether to render the rescan/reconnect action buttons (account rows only). */
        public final boolean accountActions;

        public Row(int level, String uid, String type, String label, String status, String statusDetail, String config,
                String uniqueId, boolean accountActions) {
            this.level = level;
            this.uid = uid;
            this.type = type;
            this.label = label;
            this.status = status;
            this.statusDetail = statusDetail;
            this.config = config;
            this.uniqueId = uniqueId;
            this.accountActions = accountActions;
        }
    }

    /**
     * Masks an email address for display: first character + *** + domain.
     */
    public static String maskEmail(String email) {
        return XSenseDtoUtil.maskEmail(email);
    }

    /**
     * Escapes HTML special characters.
     */
    public static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /**
     * Renders the complete manager page.
     */
    public static String render(List<Row> rows, String servletUri) {
        StringBuilder html = new StringBuilder();
        html.append(
                """
                        <!DOCTYPE html>
                        <html lang="en">
                        <head>
                        <meta charset="utf-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1">
                        <title>X-Sense Manager</title>
                        <style>
                        body { font-family: 'Roboto', 'Helvetica Neue', Arial, sans-serif; margin: 0; background: #f5f5f5; color: #212121; }
                        header { background: #e64a19; color: #fff; padding: 12px 24px; display: flex; align-items: center; gap: 12px; }
                        header img { height: 28px; width: 28px; background: #fff; border-radius: 4px; padding: 2px; }
                        header h1 { font-size: 20px; font-weight: 500; margin: 0; }
                        main { padding: 24px; }
                        table { border-collapse: collapse; width: 100%; background: #fff; box-shadow: 0 1px 3px rgba(0,0,0,.2); border-radius: 4px; overflow: hidden; }
                        th { background: #fafafa; text-align: left; padding: 10px 12px; font-weight: 500; border-bottom: 2px solid #e0e0e0; }
                        td { padding: 8px 12px; border-bottom: 1px solid #eee; vertical-align: top; }
                        tr:hover td { background: #fff8f5; }
                        .lvl1 { padding-left: 32px; } .lvl2 { padding-left: 56px; } .lvl3 { padding-left: 80px; }
                        .status { font-weight: 500; padding: 2px 8px; border-radius: 10px; font-size: 12px; display: inline-block; }
                        .ONLINE { background: #e8f5e9; color: #2e7d32; }
                        .OFFLINE { background: #ffebee; color: #c62828; }
                        .UNKNOWN, .INITIALIZING, .UNINITIALIZED { background: #fff3e0; color: #ef6c00; }
                        .uid { color: #757575; font-size: 12px; }
                        .cfg { font-size: 13px; color: #424242; }
                        button { background: #e64a19; color: #fff; border: 0; border-radius: 4px; padding: 5px 10px; cursor: pointer; font-size: 12px; }
                        button:hover { background: #bf360c; }
                        form { display: inline; margin-right: 6px; }
                        footer { padding: 12px 24px; color: #757575; font-size: 12px; }
                        footer a { color: #e64a19; }
                        </style>
                        </head>
                        <body>
                        <header><img src="%LOGO%" alt="X-Sense"><h1>X-Sense Manager</h1></header>
                        <main>
                        <table>
                        <tr><th>Thing</th><th>Type</th><th>Status</th><th>Configuration</th><th>Unique ID</th><th>Actions</th></tr>
                        """
                        .replace("%LOGO%", LOGO_URL));
        for (Row row : rows) {
            html.append("<tr><td class=\"lvl").append(row.level).append("\"><b>").append(escape(row.label))
                    .append("</b><br><span class=\"uid\">").append(escape(row.uid)).append("</span></td><td>")
                    .append(escape(row.type)).append("</td><td><span class=\"status ").append(escape(row.status))
                    .append("\">").append(escape(row.status)).append("</span>");
            if (!row.statusDetail.isBlank()) {
                html.append("<br><span class=\"cfg\">").append(escape(row.statusDetail)).append("</span>");
            }
            html.append("</td><td class=\"cfg\">").append(escape(row.config)).append("</td><td class=\"cfg\">")
                    .append(escape(row.uniqueId)).append("</td><td>");
            if (row.accountActions) {
                html.append("<form method=\"post\" action=\"").append(escape(servletUri))
                        .append("\"><input type=\"hidden\" name=\"action\" value=\"rescan\">")
                        .append("<input type=\"hidden\" name=\"uid\" value=\"").append(escape(row.uid))
                        .append("\"><button>Rescan</button></form>");
                html.append("<form method=\"post\" action=\"").append(escape(servletUri))
                        .append("\"><input type=\"hidden\" name=\"action\" value=\"reconnect\">")
                        .append("<input type=\"hidden\" name=\"uid\" value=\"").append(escape(row.uid))
                        .append("\"><button>Reconnect</button></form>");
            }
            html.append("</td></tr>\n");
        }
        html.append(
                """
                        </table>
                        </main>
                        <footer>openHAB X-Sense Binding &mdash; read-only overview with account actions. Live states follow with MQTT support. <a href="/settings/things/">Open Things in Main UI</a></footer>
                        </body>
                        </html>
                        """);
        return html.toString();
    }
}
