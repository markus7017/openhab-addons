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
package org.openhab.binding.xsense.internal.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.binding.builder.ChannelBuilder;

/**
 * The {@link XSenseChannelLabeler} builds custom channel labels of the form "&lt;Name&gt;:
 * &lt;Function&gt;" (e.g. "Kitchen: Smoke Alarm") so that item auto-naming on channel linking
 * produces unique, meaningful item names without user editing.
 *
 * The generic labels from the thing type XML are cached on first use as the base, therefore
 * re-labeling after a rename never stacks prefixes.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class XSenseChannelLabeler {

    private static final String SEPARATOR = ": ";

    // Base labels from the thing type XML, cached before the first relabeling
    private final Map<ChannelUID, String> baseLabels = new ConcurrentHashMap<>();
    private volatile @Nullable String appliedName;

    /**
     * Builds the label for a channel: "&lt;name&gt;: &lt;baseLabel&gt;" or the base label when no
     * name is available. Any colon within the name itself (X-Sense device names such as "Keller:
     * Rauch Flur" are user-authored in the app and may contain one) is turned into " -" first, so
     * the composed label has a single unambiguous separator, e.g. "Keller - Rauch Flur: Signal
     * Strength" instead of "Keller: Rauch Flur: Signal Strength".
     */
    public static String channelLabel(@Nullable String name, String baseLabel) {
        if (name == null || name.isBlank()) {
            return baseLabel;
        }
        return name.replace(":", " -") + SEPARATOR + baseLabel;
    }

    /**
     * Returns the relabeled channels of the thing or null when nothing changed (name unchanged
     * or blank). The caller applies the result via editThing().withChannels(...).
     */
    public @Nullable List<Channel> relabel(Thing thing, @Nullable String name) {
        if (name == null || name.isBlank() || name.equals(appliedName)) {
            return null;
        }
        appliedName = name;
        List<Channel> channels = new ArrayList<>();
        for (Channel channel : thing.getChannels()) {
            String base = baseLabels.computeIfAbsent(channel.getUID(), uid -> {
                String label = channel.getLabel();
                if (label == null) {
                    return uid.getIdWithoutGroup();
                }
                // The channel's current label may already be a previously composed "<name>:
                // <base>" (e.g. after a handler restart, which loses this in-memory cache but not
                // the persisted thing). The true base label never contains the separator, so
                // taking everything after its last occurrence self-heals any already-stacked
                // label instead of compounding it further.
                int lastSeparator = label.lastIndexOf(SEPARATOR);
                return lastSeparator >= 0 ? label.substring(lastSeparator + SEPARATOR.length()) : label;
            });
            // computeIfAbsent is typed @Nullable although the mapping function never returns null
            if (base == null) {
                base = channel.getUID().getIdWithoutGroup();
            }
            channels.add(ChannelBuilder.create(channel).withLabel(channelLabel(name, base)).build());
        }
        return channels;
    }
}
