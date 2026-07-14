package com.limelight.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class HostGatewayStoreTest {
    @Test
    public void connectionKeysAreScopedByMoonlightHostUuid() {
        assertEquals("living-room-host.token", HostGatewayStore.key("living-room-host", "token"));
        assertEquals("office-host.token", HostGatewayStore.key("office-host", "token"));
        assertNotEquals(
                HostGatewayStore.key("living-room-host", "token"),
                HostGatewayStore.key("office-host", "token"));
    }

    @Test
    public void discordSettingsAreScopedByHostAndProfile() {
        assertEquals("living-room-host.discord.default.auto_connect",
                HostGatewayStore.discordKey("living-room-host", "default", "auto_connect"));
        assertNotEquals(
                HostGatewayStore.discordKey("living-room-host", "gry", "auto_connect"),
                HostGatewayStore.discordKey("living-room-host", "basia", "auto_connect"));
        assertNotEquals(
                HostGatewayStore.discordKey("living-room-host", "gry", "auto_connect"),
                HostGatewayStore.discordKey("office-host", "gry", "auto_connect"));
        assertEquals("living-room-host.discord.gry.auto_join_last",
                HostGatewayStore.discordKey("living-room-host", "gry", "auto_join_last"));
        assertEquals("living-room-host.discord.gry.last_channel_id",
                HostGatewayStore.discordKey("living-room-host", "gry", "last_channel_id"));
    }
}
