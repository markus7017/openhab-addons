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
package org.openhab.binding.shelly.internal.api2;

import static org.mockito.Mockito.*;
import static org.openhab.binding.shelly.internal.ShellyBindingConstants.CHANNEL_GROUP_VCOMPONENTS;
import static org.openhab.binding.shelly.internal.ShellyBindingConstants.CHANNEL_VCOMP_BUTTON;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openhab.binding.shelly.internal.api.ShellyApiException;
import org.openhab.binding.shelly.internal.api.ShellyDeviceProfile;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.ShellyVirtualComponent;
import org.openhab.binding.shelly.internal.config.ShellyApiConfiguration;
import org.openhab.binding.shelly.internal.config.ShellyBindingConfiguration;
import org.openhab.binding.shelly.internal.config.ShellyBindingRuntimeConfig;
import org.openhab.binding.shelly.internal.handler.ShellyThingInterface;
import org.openhab.binding.shelly.internal.handler.ShellyThingTable;
import org.openhab.core.net.NetworkAddressChangeListener;
import org.openhab.core.net.NetworkAddressService;
import org.openhab.core.thing.ThingTypeUID;

/**
 * Covers {@link Shelly2ApiRpc#onNotifyEvent}'s Virtual Button dispatch: a {@code Button.Trigger} event's
 * {@code component} field (e.g. {@code button:205}) routes to the vcomponent's trigger channel instead of falling
 * into the physical-input push handling, whose {@code id < profile.numInputs} guard would otherwise silently drop
 * it since a vcomponent id (200-299) is never a valid input index.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
@ExtendWith(MockitoExtension.class)
public class Shelly2ApiRpcVirtualButtonDispatchTest {

    @Mock
    private @NonNullByDefault({}) ShellyThingInterface thing;

    private ShellyApiConfiguration testConfig() {
        ShellyBindingConfiguration raw = ShellyBindingConfiguration
                .fromProperties(Map.of(ShellyBindingConfiguration.CONFIG_LOCAL_IP, "192.168.1.50"));
        ShellyBindingRuntimeConfig bindingConfig = new ShellyBindingRuntimeConfig(raw, 8080, nullNas());
        return new ShellyApiConfiguration(bindingConfig, "test-realm", "192.168.1.100");
    }

    private static NetworkAddressService nullNas() {
        return new NetworkAddressService() {
            @Override
            public @Nullable String getPrimaryIpv4HostAddress() {
                return null;
            }

            @Override
            public @Nullable String getConfiguredBroadcastAddress() {
                return null;
            }

            @Override
            public boolean isUseOnlyOneAddress() {
                return false;
            }

            @Override
            public boolean isUseIPv6() {
                return false;
            }

            @Override
            public void addNetworkAddressChangeListener(NetworkAddressChangeListener listener) {
            }

            @Override
            public void removeNetworkAddressChangeListener(NetworkAddressChangeListener listener) {
            }
        };
    }

    private static ShellyVirtualComponent button(int id) {
        ShellyVirtualComponent vc = new ShellyVirtualComponent();
        vc.type = CHANNEL_VCOMP_BUTTON;
        vc.id = id;
        return vc;
    }

    private Shelly2ApiRpc newRpc(ShellyDeviceProfile profile) {
        when(thing.getProfile()).thenReturn(profile);
        return new Shelly2ApiRpc("test", Mockito.mock(ShellyThingTable.class), thing, testConfig(),
                Mockito.mock(WebSocketClient.class), Mockito.mock(ScheduledExecutorService.class));
    }

    private static String pushEventJson(int id, String event) {
        return "{\"src\":\"shellyplus1-test\",\"params\":{\"events\":[{\"id\":" + id + ",\"event\":\"" + event
                + "\",\"component\":\"" + CHANNEL_VCOMP_BUTTON + ":" + id + "\"}]}}";
    }

    @Test
    void singlePushOnVirtualButtonTriggersItsOwnChannelNotThePhysicalInputPath() throws ShellyApiException {
        ShellyDeviceProfile profile = new ShellyDeviceProfile(new ThingTypeUID("shelly", "shellyplus1"));
        int noPhysicalInputsSoMisroutingWouldDropTheEvent = 0;
        profile.numInputs = noPhysicalInputsSoMisroutingWouldDropTheEvent;
        profile.vComponents = List.of(button(205));
        Shelly2ApiRpc rpc = newRpc(profile);

        rpc.onNotifyEvent(pushEventJson(205, "single_push"));

        verify(thing).triggerChannel(CHANNEL_GROUP_VCOMPONENTS, CHANNEL_VCOMP_BUTTON + "205", "SHORT_PRESSED");
    }

    @Test
    void doublePushOnVirtualButtonMapsToDoublePressed() throws ShellyApiException {
        ShellyDeviceProfile profile = new ShellyDeviceProfile(new ThingTypeUID("shelly", "shellyplus1"));
        profile.vComponents = List.of(button(206));
        Shelly2ApiRpc rpc = newRpc(profile);

        rpc.onNotifyEvent(pushEventJson(206, "double_push"));

        verify(thing).triggerChannel(CHANNEL_GROUP_VCOMPONENTS, CHANNEL_VCOMP_BUTTON + "206", "DOUBLE_PRESSED");
    }

    @Test
    void virtualButtonEventForUnknownComponentIdIsIgnored() throws ShellyApiException {
        int knownButtonId = 205;
        int idNotPresentInProfile = 299;
        ShellyDeviceProfile profile = new ShellyDeviceProfile(new ThingTypeUID("shelly", "shellyplus1"));
        profile.vComponents = List.of(button(knownButtonId));

        Shelly2ApiRpc rpc = newRpc(profile);
        rpc.onNotifyEvent(pushEventJson(idNotPresentInProfile, "single_push"));

        verify(thing, never()).triggerChannel(anyString(), anyString(), anyString());
    }
}
