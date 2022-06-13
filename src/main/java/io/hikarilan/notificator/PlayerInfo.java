package io.hikarilan.notificator;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class PlayerInfo {
    private final @NotNull UUID uniqueId;
    private final @NotNull List<NotificationInfo> notifications;

    public PlayerInfo(@NotNull UUID uniqueId, @NotNull List<NotificationInfo> notifications) {
        this.uniqueId = uniqueId;
        this.notifications = notifications;
    }

    public PlayerInfo(OfflinePlayer player) {
        this(player.getUniqueId(), new ArrayList<>());
    }

    public @NotNull UUID uniqueId() {
        return uniqueId;
    }

    public @NotNull List<NotificationInfo> notifications() {
        return notifications;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (PlayerInfo) obj;
        return Objects.equals(this.uniqueId, that.uniqueId) &&
                Objects.equals(this.notifications, that.notifications);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uniqueId, notifications);
    }

    @Override
    public String toString() {
        return "PlayerInfo[" +
                "uniqueId=" + uniqueId + ", " +
                "notifications=" + notifications + ']';
    }


    public static final class NotificationInfo {
        private final @NotNull String catalog;
        private final @NotNull Long createTime;

        public NotificationInfo(@NotNull String catalog, @NotNull Long createTime) {
            this.catalog = catalog;
            this.createTime = createTime;
        }

        public @NotNull String catalog() {
            return catalog;
        }

        public @NotNull Long createTime() {
            return createTime;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (NotificationInfo) obj;
            return Objects.equals(this.catalog, that.catalog) &&
                    Objects.equals(this.createTime, that.createTime);
        }

        @Override
        public int hashCode() {
            return Objects.hash(catalog, createTime);
        }

        @Override
        public String toString() {
            return "NotificationInfo[" +
                    "catalog=" + catalog + ", " +
                    "createTime=" + createTime + ']';
        }

    }

}
