package fr.tropicube.lobby.gui;

import fr.tropicube.core.clan.ClanPresentation;
import fr.tropicube.core.clan.ClanNames;
import fr.tropicube.core.clan.ClanService;
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
 * Lobby adapter for clan screens and private input. SQL and profile resolution run asynchronously;
 * an exact holder identity prevents late responses from reopening closed or superseded screens.
 */
public final class ClanMenuController implements Listener, AutoCloseable {
    private final TropicubeLobby plugin;
    private final PlayerHeadProfileCache profiles;
    private final Set<UUID> mutations = new HashSet<>();
    private final Set<UUID> inputPlayers = new HashSet<>();
    private final int inputTimeout;
    private volatile boolean closed;

    public ClanMenuController(TropicubeLobby plugin) {
        this.plugin = plugin;
        this.profiles = new PlayerHeadProfileCache(plugin.getLogger());
        inputTimeout = ClanScreen.inputTimeout(plugin.getConfig().get("clans.input-timeout-seconds"));
    }

    public void open(Player player) { open(player, ClanScreen.home()); }

    private void open(Player player, ClanScreen screen) {
        if (closed) return;
        inputPlayers.remove(player.getUniqueId());
        var loading = ClanGUI.loading(player, screen);
        player.openInventory(loading.getInventory());
        UUID id = player.getUniqueId();
        var service = plugin.getCore().getClanService();
        service.clan(id).thenCombine(service.invitations(id), (clan, invitations) ->
                new ClanGUI.Snapshot(clan, invitations, List.of(), java.util.Map.of(),
                        service.maximumMembers(), service.weeklyContributionCap()))
                .thenCombine(screen.view() == ClanScreen.View.RANKING ? service.currentRanking(20)
                        : CompletableFuture.completedFuture(List.<ClanService.Ranking>of()), (data, ranking) ->
                        new ClanGUI.Snapshot(data.clan(), data.invitations(), ranking, data.profiles(),
                                data.capacity(), data.contributionCap()))
                .thenCompose(data -> {
                    List<ClanService.Member> members = data.clan() == null ? List.of() : switch (screen.view()) {
                        case MEMBER -> data.clan().members().stream().filter(member -> member.playerId().equals(screen.member())).toList();
                        case MEMBERS -> {
                            int page = Math.min(screen.page(), ClanScreen.pageCount(data.clan().members().size()) - 1);
                            yield data.clan().members().stream().skip(page * 21L).limit(21).toList();
                        }
                        default -> List.of();
                    };
                    var futures = members.stream().map(member -> profiles.resolve(member.playerId())
                            .thenApply(profile -> new Profile(member.playerId(), profile))).toList();
                    return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
                        var resolved = new HashMap<UUID, io.papermc.paper.datacomponent.item.ResolvableProfile>();
                        futures.forEach(future -> { var profile = future.join(); resolved.put(profile.id(), profile.value()); });
                        return new ClanGUI.Snapshot(data.clan(), data.invitations(), data.ranking(), resolved,
                                data.capacity(), data.contributionCap());
                    });
                }).whenComplete((data, failure) -> onMain(() -> {
                    Player online = Bukkit.getPlayer(id);
                    if (!isCurrent(online, loading)) return;
                    if (failure != null) {
                        plugin.getLogger().log(Level.WARNING, "Cannot load clan menu for " + id, failure);
                        ClanGUI.error(online, loading);
                    } else {
                        try { online.openInventory(ClanGUI.build(online, screen, data).getInventory()); }
                        catch (RuntimeException error) {
                            plugin.getLogger().log(Level.WARNING, "Cannot render clan menu for " + id, error);
                            ClanGUI.error(online, loading);
                        }
                    }
                }));
    }

    private record Profile(UUID id, io.papermc.paper.datacomponent.item.ResolvableProfile value) { }

    /** Keeps navigation and prompt stage when /lang changes the active language. */
    public void refreshLanguage(Player player) {
        plugin.getCore().getPrivateChatInput().repeat(player);
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof ClanGUI.Holder holder)
            open(player, holder.screen);
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ClanGUI.Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClick() != ClickType.LEFT
                || event.getRawSlot() < 0 || event.getRawSlot() >= holder.getInventory().getSize()) return;
        var action = holder.actions.get(event.getRawSlot());
        if (action == null) return;
        if (mutations.contains(player.getUniqueId()) && action.kind() != ClanScreen.Kind.CLOSE
                && action.kind() != ClanScreen.Kind.FRIENDS && action.kind() != ClanScreen.Kind.PARTY) return;
        switch (action.kind()) {
            case CLOSE -> player.closeInventory();
            case FRIENDS -> plugin.getGuiManager().openSocial(player, SocialGUI.View.FRIENDS);
            case PARTY -> plugin.getGuiManager().openSocial(player, SocialGUI.View.PARTY);
            case HOME -> open(player);
            case BACK -> {
                if (holder.screen.view() == ClanScreen.View.HOME) plugin.getGuiManager().openSocial(player);
                else if (holder.screen.view() == ClanScreen.View.MEMBER) open(player,
                        new ClanScreen(ClanScreen.View.MEMBERS, holder.screen.page(), null, null));
                else open(player);
            }
            case MEMBERS, INVITATIONS, CHALLENGES, RANKING -> open(player,
                    new ClanScreen(ClanScreen.View.valueOf(action.kind().name()), 0, null, null));
            case MEMBER -> open(player, new ClanScreen(ClanScreen.View.MEMBER, holder.screen.page(), action.player(), null));
            case PREVIOUS, NEXT -> open(player, new ClanScreen(holder.screen.view(),
                    holder.screen.page() + (action.kind() == ClanScreen.Kind.NEXT ? 1 : -1), holder.screen.member(), null));
            case RETRY -> open(player, holder.screen);
            case CREATE -> askName(player);
            case INVITE -> askInvite(player, holder.clanId);
            case ACCEPT -> execute(player, holder, () -> plugin.getCore().getClanService().accept(player.getUniqueId(), action.tag()));
            case LEAVE, KICK, TRANSFER -> open(player, new ClanScreen(ClanScreen.View.CONFIRM, 0, null, action));
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
        if (event.getInventory().getHolder() instanceof ClanGUI.Holder) event.setCancelled(true);
    }

    private void administer(Player player, ClanGUI.Holder holder, ClanScreen.Action action) {
        var service = plugin.getCore().getClanService();
        execute(player, holder, () -> service.administer(player.getUniqueId(), action.clanId(),
                ClanService.Administration.valueOf(action.kind().name()), action.player()));
    }

    private void execute(Player player, ClanGUI.Holder holder, Supplier<CompletableFuture<ClanService.Result>> operation) {
        UUID id = player.getUniqueId();
        if (!mutations.add(id)) return;
        CompletableFuture<ClanService.Result> future;
        try { future = operation.get(); }
        catch (RuntimeException error) { future = CompletableFuture.failedFuture(error); }
        future.whenComplete((result, failure) -> onMain(() -> {
            mutations.remove(id);
            Player online = Bukkit.getPlayer(id);
            if (online == null) return;
            if (failure != null) {
                plugin.getLogger().log(Level.WARNING, "Clan operation failed for " + id, failure);
                online.sendMessage(LangHelper.component(online, "general.operation-failed"));
            } else online.sendMessage(LangHelper.component(online, ClanPresentation.resultKey(result)));
            if (isCurrent(online, holder)) open(online);
            else if (online.getOpenInventory().getTopInventory().getHolder() instanceof ClanGUI.Holder current)
                open(online, current.screen);
        }));
    }

    private void askName(Player player) {
        prompt(player, "clan-ui.input-name", name -> {
            if (!ClanNames.validName(name)) {
                player.sendMessage(LangHelper.component(player, "clan-ui.invalid-name"));
                askName(player);
            } else askTag(player, name);
        });
    }

    private void askTag(Player player, String name) {
        prompt(player, "clan-ui.input-tag", tag -> {
            if (!ClanNames.validTag(tag)) {
                player.sendMessage(LangHelper.component(player, "clan-ui.invalid-tag"));
                askTag(player, name);
                return;
            }
            var pending = ClanGUI.loading(player, ClanScreen.home());
            player.openInventory(pending.getInventory());
            execute(player, pending, () -> plugin.getCore().getClanService().create(player.getUniqueId(), name, tag));
        });
    }

    private void askInvite(Player player, long clanId) {
        prompt(player, "clan-ui.input-player", name -> {
            if (!name.matches("[.A-Za-z0-9_]{1,32}")) {
                player.sendMessage(LangHelper.component(player, "general.player-not-found", name));
                askInvite(player, clanId);
                return;
            }
            UUID actor = player.getUniqueId();
            var pending = ClanGUI.loading(player, ClanScreen.home());
            player.openInventory(pending.getInventory());
            execute(player, pending, () -> plugin.getCore().getDatabaseManager().supplyAsync(() ->
                            plugin.getCore().getPlayerDataManager().getUuidByName(name).orElse(null))
                    .thenCompose(target -> {
                        if (target == null || target.equals(actor)) return CompletableFuture.completedFuture(ClanService.Result.INVALID);
                        return plugin.getCore().getClanService().administer(actor, clanId, ClanService.Administration.INVITE, target)
                                .thenCompose(result -> {
                                    if (result != ClanService.Result.SUCCESS) return CompletableFuture.completedFuture(result);
                                    return plugin.getCore().getClanInvitations().notifyInvitation(actor, target).thenApply(ignored -> result);
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

    private boolean isCurrent(Player player, ClanGUI.Holder holder) {
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
