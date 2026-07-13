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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;

@SuppressWarnings({ "null" })
@NonNullByDefault
public class XSenseChannelLabelerTest {

    private final ThingUID thingUID = new ThingUID("xsense", "smoke", "test");

    @Test
    public void channelLabelPrefixesName() {
        assertEquals("Kitchen: Smoke Alarm", XSenseChannelLabeler.channelLabel("Kitchen", "Smoke Alarm"));
        assertEquals("Smoke Alarm", XSenseChannelLabeler.channelLabel(null, "Smoke Alarm"));
        assertEquals("Smoke Alarm", XSenseChannelLabeler.channelLabel("", "Smoke Alarm"));
    }

    @Test
    public void relabelAppliesNamePrefix() {
        XSenseChannelLabeler labeler = new XSenseChannelLabeler();
        List<Channel> channels = labeler.relabel(thingWithChannel("Smoke Alarm"), "Kitchen");
        assertNotNull(channels);
        assertEquals(1, channels.size());
        assertEquals("Kitchen: Smoke Alarm", channels.get(0).getLabel());
    }

    @Test
    public void relabelWithSameNameReturnsNull() {
        XSenseChannelLabeler labeler = new XSenseChannelLabeler();
        assertNotNull(labeler.relabel(thingWithChannel("Smoke Alarm"), "Kitchen"));
        assertNull(labeler.relabel(thingWithChannel("Kitchen: Smoke Alarm"), "Kitchen"));
    }

    @Test
    public void renameNeverStacksPrefixes() {
        XSenseChannelLabeler labeler = new XSenseChannelLabeler();
        List<Channel> first = labeler.relabel(thingWithChannel("Smoke Alarm"), "Kitchen");
        assertNotNull(first);
        // Simulate rename: the thing now carries the previously applied label
        List<Channel> second = labeler.relabel(thingWithChannel("Kitchen: Smoke Alarm"), "Living Room");
        assertNotNull(second);
        assertEquals("Living Room: Smoke Alarm", second.get(0).getLabel());
    }

    @Test
    public void blankNameReturnsNull() {
        XSenseChannelLabeler labeler = new XSenseChannelLabeler();
        assertNull(labeler.relabel(thingWithChannel("Smoke Alarm"), null));
        assertNull(labeler.relabel(thingWithChannel("Smoke Alarm"), " "));
    }

    @Test
    public void channelLabelReplacesColonInName() {
        // X-Sense device names are user-authored in the app and may contain a colon themselves
        // (e.g. "Keller: Rauch Flur"); it is turned into " -" so the composed label has a single
        // unambiguous separator.
        assertEquals("Keller - Rauch Flur: Signal Strength",
                XSenseChannelLabeler.channelLabel("Keller: Rauch Flur", "Signal Strength"));
    }

    @Test
    public void relabelSelfHealsLabelStackedByPriorHandlerInstance() {
        // A fresh labeler (e.g. after a handler restart) starts with an empty cache, but the
        // thing's channel may already carry a label stacked by a previous handler instance.
        XSenseChannelLabeler labeler = new XSenseChannelLabeler();
        List<Channel> channels = labeler.relabel(
                thingWithChannel("Keller: Rauch Flur: Keller: Rauch Flur: Keller: Rauch Flur: Signal Strength"),
                "Keller: Rauch Flur");
        assertNotNull(channels);
        assertEquals("Keller - Rauch Flur: Signal Strength", channels.get(0).getLabel());
    }

    private Thing thingWithChannel(String label) {
        Channel channel = ChannelBuilder.create(new ChannelUID(thingUID, "alarm", "smoke"), "Switch").withLabel(label)
                .build();
        Thing thing = mock(Thing.class);
        when(thing.getChannels()).thenReturn(List.of(channel));
        return thing;
    }
}
