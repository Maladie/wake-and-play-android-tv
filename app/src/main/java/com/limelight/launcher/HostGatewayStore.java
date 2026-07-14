package com.limelight.launcher;

import android.content.Context;
import android.content.SharedPreferences;

final class HostGatewayStore {
    private static final String PREFS = "host_gateway_connections";
    static final String DEFAULT_DISCORD_PROFILE_ID = "default";
    private final SharedPreferences preferences;

    static final class DiscordChannelSelection {
        final String channelId;
        final String guildId;
        final String guildName;
        final String channelName;

        DiscordChannelSelection(String channelId, String guildId,
                                String guildName, String channelName) {
            this.channelId = channelId;
            this.guildId = guildId;
            this.guildName = guildName;
            this.channelName = channelName;
        }
    }

    HostGatewayStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    HostGatewayClient.Connection load(String hostUuid) {
        if (hostUuid == null || !preferences.getBoolean(key(hostUuid, "paired"), false)) return null;
        String endpoint = preferences.getString(key(hostUuid, "endpoint"), "");
        String token = preferences.getString(key(hostUuid, "token"), "");
        String certificate = preferences.getString(key(hostUuid, "certificate"), "");
        if (endpoint.isEmpty() || token.isEmpty() || certificate.isEmpty()) return null;
        return new HostGatewayClient.Connection(endpoint, token, certificate);
    }

    void save(String hostUuid, HostGatewayClient.Connection connection) {
        preferences.edit()
                .putBoolean(key(hostUuid, "paired"), true)
                .putString(key(hostUuid, "endpoint"), connection.endpoint)
                .putString(key(hostUuid, "token"), connection.token)
                .putString(key(hostUuid, "certificate"), connection.certificateSha256)
                .apply();
    }

    void remove(String hostUuid) {
        preferences.edit()
                .remove(key(hostUuid, "paired"))
                .remove(key(hostUuid, "endpoint"))
                .remove(key(hostUuid, "token"))
                .remove(key(hostUuid, "certificate"))
                .remove(key(hostUuid, "discord_auto_connect"))
                .remove(discordKey(hostUuid, DEFAULT_DISCORD_PROFILE_ID, "auto_connect"))
                .remove(discordKey(hostUuid, DEFAULT_DISCORD_PROFILE_ID, "auto_join_last"))
                .remove(discordKey(hostUuid, DEFAULT_DISCORD_PROFILE_ID, "last_channel_id"))
                .remove(discordKey(hostUuid, DEFAULT_DISCORD_PROFILE_ID, "last_guild_id"))
                .remove(discordKey(hostUuid, DEFAULT_DISCORD_PROFILE_ID, "last_guild_name"))
                .remove(discordKey(hostUuid, DEFAULT_DISCORD_PROFILE_ID, "last_channel_name"))
                .apply();
    }

    boolean isDiscordAutoConnectEnabled(String hostUuid) {
        return isDiscordAutoConnectEnabled(hostUuid, DEFAULT_DISCORD_PROFILE_ID);
    }

    void setDiscordAutoConnectEnabled(String hostUuid, boolean enabled) {
        setDiscordAutoConnectEnabled(hostUuid, DEFAULT_DISCORD_PROFILE_ID, enabled);
    }

    boolean isDiscordAutoConnectEnabled(String hostUuid, String profileId) {
        if (hostUuid == null || profileId == null) return false;
        String profileKey = discordKey(hostUuid, profileId, "auto_connect");
        if (preferences.contains(profileKey)) return preferences.getBoolean(profileKey, false);
        return DEFAULT_DISCORD_PROFILE_ID.equals(profileId) &&
                preferences.getBoolean(key(hostUuid, "discord_auto_connect"), false);
    }

    void setDiscordAutoConnectEnabled(String hostUuid, String profileId, boolean enabled) {
        if (hostUuid == null || profileId == null) return;
        SharedPreferences.Editor editor = preferences.edit()
                .putBoolean(discordKey(hostUuid, profileId, "auto_connect"), enabled);
        if (DEFAULT_DISCORD_PROFILE_ID.equals(profileId)) {
            editor.remove(key(hostUuid, "discord_auto_connect"));
        }
        editor.apply();
    }

    boolean isDiscordAutoJoinLastEnabled(String hostUuid) {
        return isDiscordAutoJoinLastEnabled(hostUuid, DEFAULT_DISCORD_PROFILE_ID);
    }

    boolean isDiscordAutoJoinLastEnabled(String hostUuid, String profileId) {
        return hostUuid != null && profileId != null && preferences.getBoolean(
                discordKey(hostUuid, profileId, "auto_join_last"), false);
    }

    void setDiscordAutoJoinLastEnabled(String hostUuid, boolean enabled) {
        if (hostUuid == null) return;
        preferences.edit().putBoolean(discordKey(hostUuid,
                DEFAULT_DISCORD_PROFILE_ID, "auto_join_last"), enabled).apply();
    }

    DiscordChannelSelection loadLastDiscordChannel(String hostUuid) {
        return loadLastDiscordChannel(hostUuid, DEFAULT_DISCORD_PROFILE_ID);
    }

    DiscordChannelSelection loadLastDiscordChannel(String hostUuid, String profileId) {
        if (hostUuid == null || profileId == null) return null;
        String channelId = preferences.getString(
                discordKey(hostUuid, profileId, "last_channel_id"), "");
        String guildId = preferences.getString(
                discordKey(hostUuid, profileId, "last_guild_id"), "");
        String channelName = preferences.getString(
                discordKey(hostUuid, profileId, "last_channel_name"), "");
        if (channelId.isEmpty() || guildId.isEmpty() || channelName.isEmpty()) return null;
        return new DiscordChannelSelection(channelId, guildId,
                preferences.getString(discordKey(hostUuid, profileId, "last_guild_name"), ""),
                channelName);
    }

    void saveLastDiscordChannel(String hostUuid, String channelId, String guildId,
                                String guildName, String channelName) {
        if (hostUuid == null || channelId == null || channelId.isEmpty() ||
                guildId == null || guildId.isEmpty() ||
                channelName == null || channelName.isEmpty()) return;
        String profileId = DEFAULT_DISCORD_PROFILE_ID;
        preferences.edit()
                .putString(discordKey(hostUuid, profileId, "last_channel_id"), channelId)
                .putString(discordKey(hostUuid, profileId, "last_guild_id"), guildId)
                .putString(discordKey(hostUuid, profileId, "last_guild_name"),
                        guildName != null ? guildName : "")
                .putString(discordKey(hostUuid, profileId, "last_channel_name"), channelName)
                .apply();
    }

    static String discordKey(String hostUuid, String profileId, String suffix) {
        return hostUuid + ".discord." + profileId + "." + suffix;
    }

    static String key(String hostUuid, String suffix) {
        return hostUuid + "." + suffix;
    }
}
