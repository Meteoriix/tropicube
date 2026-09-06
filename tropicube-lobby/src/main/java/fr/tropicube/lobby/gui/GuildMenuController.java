package fr.tropicube.lobby.gui;

import fr.tropicube.core.guild.GuildPresentation;
import fr.tropicube.core.guild.GuildNames;
import fr.tropicube.core.guild.GuildService;
import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.lobby.utils.LangHelper;
import fr.tropicube.lobby.utils.PlayerHeadProfileCache;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Lobby adapter for guild screens and private input. SQL and profile resolution run asynchronously;
 * an exact holder identity prevents late responses from reopening closed or superseded screens.
 */
public final class GuildMenuController implements Listener, AutoCloseable {
    private final TropicubeLobby plugin;
    private final PlayerHeadProfileCache profiles;
    private final Set<UUID> mutations = new HashSet<>();
    private final Set<UUID> inputPlayers = new HashSet<>();
    private final int inputTimeout;
    private volatile boolean closed;

    public GuildMenuController(TropicubeLobby plugin) {
        this.plugin = plugin;
        this.profiles = new PlayerHeadProfileCache(plugin.getLogger());
        inputTimeout = GuildScreen.inputTimeout(plugin.getConfig().get("guilds.input-timeout-seconds"));
    }

    public void open(Player player) { open(player, GuildScreen.home()); }

    private void open(Player player, GuildScreen screen) {
        if (closed) return;
        inputPlayers.remove(player.getUniqueId());
        var loading = GuildGUI.loading(player, screen);
        player.openInventory(loading.getInventory());
        UUID id = player.getUniqueId();
        var service = plugin.getCore().getGuildService();
        service.guild(id).thenCombine(service.invitations(id), (guild, invitations) ->
                new GuildGUI.Snapshot(guild, invitations, List.of(), java.util.Map.of(),
                        service.maximumMembers(), service.weeklyContributionCap()))
                .thenCombine(screen.view() == GuildScreen.View.RANKING ? service.currentRanking(20)
                        : CompletableFuture.completedFuture(List.<GuildService.Ranking>of()), (data, ranking) ->
                        new GuildGUI.Snapshot(data.guild(), data.invitations(), ranking, data.profiles(),
                                data.capacity(), data.contributionCap()))
                .thenCompose(data -> {
                    List<GuildService.Member> members = data.guild() == null ? List.of() : switch (screen.view()) {
                        case MEMBER -> data.guild().members().stream().filter(member -> member.playerId().equals(screen.member())).toList();
                        case MEMBERS -> {
                            int page = Math.min(screen.page(), GuildScreen.pageCount(data.guild().members().size()) - 1);
                            yield data.guild().members().stream().skip(page * 21L).limit(21).toList();
                        }
                        default -> List.of();
                    };
                    var futures = members.stream().map(member -> profiles.resolve(member.playerId())
                            .thenApply(profile -> new Profile(member.playerId(), profile))).toList();
                    return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
                        var resolved = new HashMap<UUID, io.papermc.paper.datacomponent.item.ResolvableProfile>();
                        futures.forEach(future -> { var profile = future.join(); resolved.put(profile.id(), profile.value()); });
                        return new GuildGUI.Snapshot(data.guild(), data.invitations(), data.ranking(), resolved,
                                data.capacity(), data.contributionCap());
                    });
                }).whenComplete((data, failure) -> onMain(() -> {
                    Player online = Bukkit.getPlayer(id);
                    if (!isCurrent(online, loading)) return;
                    if (failure != null) {
                        plugin.getLogger().log(Level.WARNING, "Cannot load guild menu for " + id, failure);
                        GuildGUI.error(online, loading);
                    } else {
                        try { online.openInventory(GuildGUI.build(online, screen, data).getInventory()); }
                        catch (RuntimeException error) {
                            plugin.getLogger().log(Level.WARNING, "Cannot render guild menu for " + id, error);
                            GuildGUI.error(online, loading);
                        }
                    }
                }));
    }

    private record Profile(UUID id, io.papermc.paper.datacomponent.item.ResolvableProfile value) { }

    /** Keeps navigation and prompt stage when /lang changes the active language. */
    public void refreshLanguage(Player player) {
        plugin.getCore().getPrivateChatInput().repeat(player);
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof GuildGUI.Holder holder)
            open(player, holder.screen);
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuildGUI.Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClick() != ClickType.LEFT
                || event.getRawSlot() < 0 || event.getRawSlot() >= holder.getInventory().getSize()) return;
        var action = holder.actions.get(event.getRawSlot());
        if (action == null) return;
        if (mutations.contains(player.getUniqueId()) && action.kind() != GuildScreen.Kind.CLOSE
                && action.kind() != GuildScreen.Kind.FRIENDS && action.kind() != GuildScreen.Kind.PARTY) return;
        switch (action.kind()) {
            case CLOSE -> player.closeInventory();
            case FRIENDS -> plugin.getGuiManager().openSocial(player, SocialGUI.View.FRIENDS);
            case PARTY -> plugin.getGuiManager().openSocial(player, SocialGUI.View.PARTY);
            case HOME -> open(player);
            case BACK -> {
                if (holder.screen.view() == GuildScreen.View.HOME) plugin.getGuiManager().openSocial(player);
                else if (holder.screen.view() == GuildScreen.View.MEMBER) open(player,
                        new GuildScreen(GuildScreen.View.MEMBERS, holder.screen.page(), null, null));
                else open(player);
            }
            case MEMBERS, INVITATIONS, CHALLENGES, RANKING -> open(player,
                    new GuildScreen(GuildScreen.View.valueOf(action.kind().name()), 0, null, null));
            case MEMBER -> open(player, new GuildScreen(GuildScreen.View.MEMBER, holder.screen.page(), action.player(), null));
            case PREVIOUS, NEXT -> open(player, new GuildScreen(holder.screen.view(),
                    holder.screen.page() + (action.kind() == GuildScreen.Kind.NEXT ? 1 : -1), holder.screen.member(), null));
            case REFRESH -> open(player, holder.screen);
            case CREATE -> askName(player);
            case INVITE -> askInvite(player, holder.guildId);
            case ACCEPT -> execute(player, holder, () -> plugin.getCore().getGuildService().accept(player.getUniqueId(), action.tag()));
            case LEAVE, KICK, TRANSFER -> open(player, new GuildScreen(GuildScreen.View.CONFIRM, 0, null, action));
            case PROMOTE, DEMOTE -> administer(player, holder, action);
            case CONFIRM -> {
                if (holder.screen.confirmation() != null) administer(player, holder, holder.screen.confirmation());
            }
        }
    }

    @EventHandler public void navigation(org.bukkit.event.inventory.InventoryOpenEvent event) {
        inputPlayers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof GuildGUI.Holder) event.setCancelled(true);
    }

    private void administer(Player player, GuildGUI.Holder holder, GuildScreen.Action action) {
        var service = plugin.getCore().getGuildService();
        execute(player, holder, () -> service.administer(player.getUniqueId(), action.guildId(),
                GuildService.Administration.valueOf(action.kind().name()), action.player()));
    }

    private void execute(Player player, GuildGUI.Holder holder, Supplier<CompletableFuture<GuildService.Result>> operation) {
        UUID id = player.getUniqueId();
        if (!mutations.add(id)) return;
        CompletableFuture<GuildService.Result> future;
        try { future = operation.get(); }
        catch (RuntimeException error) { future = CompletableFuture.failedFuture(error); }
        future.whenComplete((result, failure) -> onMain(() -> {
            mutations.remove(id);
            Player online = Bukkit.getPlayer(id);
            if (online == null) return;
            if (failure != null) {
                plugin.getLogger().log(Level.WARNING, "Guild operation failed for " + id, failure);
                online.sendMessage(LangHelper.component(online, "general.operation-failed"));
            } else online.sendMessage(LangHelper.component(online, GuildPresentation.resultKey(result)));
            if (isCurrent(online, holder)) open(online);
            else if (online.getOpenInventory().getTopInventory().getHolder() instanceof GuildGUI.Holder current)
                open(online, current.screen);
        }));
    }

    private void askName(Player player) {
        prompt(player, "guild-ui.input-name", name -> {
            if (!GuildNames.validName(name)) {
                player.sendMessage(LangHelper.component(player, "guild-ui.invalid-name"));
                askName(player);
            } else askTag(player, name);
        });
    }

    private void askTag(Player player, String name) {
        prompt(player, "guild-ui.input-tag", tag -> {
            if (!GuildNames.validTag(tag)) {
                player.sendMessage(LangHelper.component(player, "guild-ui.invalid-tag"));
                askTag(player, name);
                return;
            }
            var pending = GuildGUI.loading(player, GuildScreen.home());
            player.openInventory(pending.getInventory());
            execute(player, pending, () -> plugin.getCore().getGuildService().create(player.getUniqueId(), name, tag));
        });
    }

    private void askInvite(Player player, long guildId) {
        prompt(player, "guild-ui.input-player", name -> {
            if (!name.matches("[.A-Za-z0-9_]{1,32}")) {
                player.sendMessage(LangHelper.component(player, "general.player-not-found", name));
                askInvite(player, guildId);
                return;
            }
            UUID actor = player.getUniqueId();
            var pending = GuildGUI.loading(player, GuildScreen.home());
            player.openInventory(pending.getInventory());
            execute(player, pending, () -> plugin.getCore().getDatabaseManager().supplyAsync(() ->
                            plugin.getCore().getPlayerDataManager().getUuidByName(name).orElse(null))
                    .thenCompose(target -> {
                        if (target == null || target.equals(actor)) return CompletableFuture.completedFuture(GuildService.Result.INVALID);
                        return plugin.getCore().getGuildService().administer(actor, guildId, GuildService.Administration.INVITE, target)
                                .thenCompose(result -> {
                                    if (result != GuildService.Result.SUCCESS) return CompletableFuture.completedFuture(result);
                                    return plugin.getCore().getGuildInvitations().notifyInvitation(actor, target).thenApply(ignored -> result);
                                });
                    }));
        });
    }

    private void prompt(Player player, String key, java.util.function.Consumer<String> answer) {
        inputPlayers.add(player.getUniqueId());
        plugin.getCore().getPrivateChatInput().begin(player, key, inputTimeout, text -> {
            inputPlayers.remove(player.getUniqueId());
            if (!closed) answer.accept(text);
        }, () -> { if (!closed) open(player); });
    }

    private boolean isCurrent(Player player, GuildGUI.Holder holder) {
        return !closed && player != null && player.getOpenInventory().getTopInventory().getHolder() == holder;
    }

    private void onMain(Runnable action) {
        if (!closed && plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> { if (!closed) action.run(); });
    }

    public void quit(UUID player) { inputPlayers.remove(player); }

    @Override public void close() {
        closed = true;
        inputPlayers.forEach(id -> plugin.getCore().getPrivateChatInput().cancel(id));
        inputPlayers.clear();
        profiles.clear();
        mutations.clear();
    }
}
