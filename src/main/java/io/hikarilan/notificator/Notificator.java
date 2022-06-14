package io.hikarilan.notificator;

import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Notificator extends JavaPlugin implements Listener {

    private List<Catalog> catalogList;
    private Map<UUID, PlayerInfo> playerInfoMap = new HashMap<>();

    private final Map<OfflinePlayer, List<PlayerInfo.NotificationInfo>> pending = new HashMap<>();

    private PlayerInfo getInfoFor(OfflinePlayer player) {
        return playerInfoMap.getOrDefault(player.getUniqueId(), new PlayerInfo(player));
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        saveDefaultConfig();
        saveResource("players.json", false);

        this.catalogList = getConfig().getMapList("catalogs").parallelStream().map(it -> new Catalog((String) it.get("name"), Type.valueOf((String) it.get("type")))).toList();
        try {
            this.playerInfoMap = new Gson().fromJson(Files.newReader(getDataFolder().toPath().resolve("players.json").toFile(), StandardCharsets.UTF_8), new TypeToken<Map<UUID, PlayerInfo>>() {
            }.getType());
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        Bukkit.getPluginManager().registerEvents(this, this);

        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        try {
            Files.write(new GsonBuilder().setPrettyPrinting().create().toJson(playerInfoMap).getBytes(StandardCharsets.UTF_8), getDataFolder().toPath().resolve("players.json").toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    @EventHandler
    private void onJoin(PlayerJoinEvent e) throws IOException {
        nextNotification(e.getPlayer());
    }

    private void nextNotification(Player player) throws IOException {
        var info = getInfoFor(player);
        var newest = catalogList.stream().map(it -> new CatalogFilePair(it, Arrays.stream(Objects.requireNonNull(getDataFolder().toPath().resolve(it.name()).toFile().listFiles())).max(Comparator.comparingLong(File::lastModified)).orElse(null))).toList();
        var find = newest.stream()
                .filter(it -> it.file() != null)
                .filter(it -> info.notifications().stream().max(Comparator.comparingLong(PlayerInfo.NotificationInfo::createTime)).orElseGet(() -> new PlayerInfo.NotificationInfo(it.catalog().name(), Long.MIN_VALUE)).createTime() < it.file().lastModified())
                .toList();
        if (find.isEmpty()) {
            afterAccept(player);
            return;
        }
        Book.Builder book = Book.builder();
        if (find.size() != 1) {
            book.title(Component.text("多个待阅读的许可条款或通知"));
        } else {
            book.title(Component.text(find.get(0).file().getName()));
        }
        book.author(Component.text("StarLightMinecraft"));
        for (CatalogFilePair pair : find) {
            var page = Component.text("正阅读：").append(Component.text(pair.file().getName())).append(Component.newline());
            for (String line : Files.readLines(Objects.requireNonNull(pair.file()), StandardCharsets.UTF_8)) {
                book.addPage(page.append(MiniMessage.miniMessage().deserialize(line)));
            }
        }
        if (find.stream().anyMatch(it -> it.catalog().type() == Type.MUST_ACCEPT)) {
            player.sendMessage(Component.text("您正在阅读的文本中包含必须手动接受的文本，请详细阅读这些文本，然后跳转到书末选择是否接受").color(NamedTextColor.RED));
            pending.put(player, find.stream().filter(it -> it.catalog().type() == Type.MUST_ACCEPT).map(it -> new PlayerInfo.NotificationInfo(it.catalog().name(), it.file().lastModified())).toList());
            book.addPage(
                    Component.text()
                            .append(Component.text("点击 [接受] 则代表您同意并接受以下许可条款："))
                            .append(Component.newline())
                            .append(Component.join(JoinConfiguration.commas(true), find.stream().filter(it -> it.catalog().type() == Type.MUST_ACCEPT).map(it -> Component.text(it.file().getName())).toList()))
                            .append(Component.newline())
                            .append(Component.text("[接受]").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD).clickEvent(ClickEvent.runCommand("/accept")))
                            .append(Component.text(" "))
                            .append(Component.text("[拒绝]").color(NamedTextColor.RED).decorate(TextDecoration.BOLD).clickEvent(ClickEvent.runCommand("/deny")))
                            .build()
            );
        } else {
            info.notifications().addAll(find.stream().map(it -> new PlayerInfo.NotificationInfo(it.catalog().name(), it.file().lastModified())).toList());
            playerInfoMap.put(player.getUniqueId(), info);
            player.sendMessage(Component.text("您将会在 20 秒后被传送到服务器中...").color(NamedTextColor.GOLD));
            Bukkit.getScheduler().runTaskLater(this, () -> afterAccept(player), 20 * 20L);
        }
        player.openBook(book.build());
    }

    @EventHandler
    private void onMove(PlayerMoveEvent e) {
        if (!pending.containsKey(e.getPlayer())) return;
        e.setCancelled(true);
    }

    @EventHandler
    private void onCommand(PlayerCommandPreprocessEvent e) {
        if (e.getMessage().equals("/accept")) {
            var find = Optional.ofNullable(pending.get(e.getPlayer()));
            if (find.isEmpty()) return;
            var info = getInfoFor(e.getPlayer());
            info.notifications().addAll(find.get());
            playerInfoMap.put(e.getPlayer().getUniqueId(), info);
            pending.remove(e.getPlayer());
            e.getPlayer().sendMessage(Component.text("已接受全部许可条款，玩的开心！").color(NamedTextColor.GREEN));
            afterAccept(e.getPlayer());
        } else if (e.getMessage().equals("/deny")) {
            var find = Optional.ofNullable(pending.get(e.getPlayer()));
            if (find.isEmpty()) return;
            pending.remove(e.getPlayer());
            e.getPlayer().kick(Component.text("已拒绝条款").color(NamedTextColor.RED));
        } else return;

        e.setCancelled(true);
    }

    private void afterAccept(Player player) {
        player.sendMessage(Component.text("正在传送至目标服务器，请等待至多 3 秒...").color(NamedTextColor.GOLD));
        Bukkit.getScheduler().runTaskLater(this, () -> {
            player.sendMessage(Component.text("已将您传送至目标服务器，如果传送仍未开始，请尝试退出服务器重进，或联系管理员").color(NamedTextColor.LIGHT_PURPLE));
            var out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(getConfig().getString("general.teleport", "server"));
            player.sendPluginMessage(this, "BungeeCord", out.toByteArray());
        }, 3 * 20L);
    }

    record CatalogFilePair(@NotNull Catalog catalog, @Nullable File file) {
    }
}
