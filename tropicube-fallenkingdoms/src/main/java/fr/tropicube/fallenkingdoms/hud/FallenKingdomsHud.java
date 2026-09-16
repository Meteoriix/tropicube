package fr.tropicube.fallenkingdoms.hud;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.ui.ScoreboardTemplate;
import fr.tropicube.core.ui.TablistTemplate;
import fr.tropicube.core.ui.UiReloadParticipant;
import fr.tropicube.fallenkingdoms.TropicubeFallenKingdoms;
import fr.tropicube.fallenkingdoms.game.GameSession;
import fr.tropicube.fallenkingdoms.game.GameState;
import fr.tropicube.fallenkingdoms.game.Heart;
import fr.tropicube.fallenkingdoms.game.KingdomId;
import fr.tropicube.language.PlaceholderValues;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Scoreboard;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;
import java.util.Set;
import org.bukkit.scheduler.BukkitTask;

/** Localized scoreboard, tablist and heart bossbar for every FK viewer. */
public final class FallenKingdomsHud implements UiReloadParticipant {
    private final TropicubeFallenKingdoms plugin; private final GameSession session;
    private final Map<UUID, Scoreboard> boards=new HashMap<>(); private final Map<UUID, HeartBar> bars=new HashMap<>();
    private volatile ScoreboardTemplate scoreboard; private volatile TablistTemplate tablist;
    public FallenKingdomsHud(TropicubeFallenKingdoms plugin,GameSession session){this.plugin=plugin;this.session=session;prepareReload().run();Bukkit.getServicesManager().register(UiReloadParticipant.class,this,plugin,ServicePriority.Normal);}
    public void updateAll(int elapsed){for(Player player:Bukkit.getOnlinePlayers()){applyPlayerListName(player);update(player,elapsed);}}
    public void update(Player player,int elapsed){
        TropicubeCore core=(TropicubeCore)Bukkit.getPluginManager().getPlugin("TropicubeCore");if(core==null)return;
        var language=core.getLanguageManager(); KingdomId team=session.kingdomOf(player);
        PlaceholderValues values=values(player,elapsed,language,team);
        Scoreboard board=boards.computeIfAbsent(player.getUniqueId(),_ -> Bukkit.getScoreboardManager().getNewScoreboard());
        var old=board.getObjective("fallenkingdoms");if(old!=null)old.unregister();
        var objective=board.registerNewObjective("fallenkingdoms",Criteria.DUMMY,language.getComponent(player.getUniqueId(),scoreboard.titleKey()));objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        String variant=switch(session.state()){case WAITING->"waiting";case COUNTDOWN->"countdown";case ENDING,ENDED->"ending";default->"active";};
        int score=15;for(var line:scoreboard.lines(variant)){
            if(!line.blank()&&!showLine(line.key(),session.activeKingdoms()))continue;
            var entry=objective.getScore("fk_"+score);entry.setScore(score--);entry.customName(line.blank()?Component.empty():language.getComponent(player.getUniqueId(),line.key(),values));
        }
        player.setScoreboard(board);var tabVariant=tablist.variant(variant);player.sendPlayerListHeaderAndFooter(language.getComponent(player.getUniqueId(),tabVariant.headerKey(),values),language.getComponent(player.getUniqueId(),tabVariant.footerKey(),values));
        applyPlayerListName(player);
    }
    private PlaceholderValues values(Player player,int elapsed,fr.tropicube.core.managers.LanguageManager language,KingdomId team){
        Map<KingdomId,Integer> survivors=session.survivorCounts();
        boolean waiting=session.state()==GameState.WAITING||session.state()==GameState.COUNTDOWN;
        KingdomId displayedTeam=team==null&&waiting?session.preferredKingdom(player.getUniqueId()):team;
        String teamKey=displayedTeam==null?(waiting?"fk.choice-none":"fk.team-spectator"):"fk.team-"+displayedTeam.name().toLowerCase(java.util.Locale.ROOT);
        String mapKey=waiting?session.waitingMapDisplayNameKey(player.getUniqueId()):session.mapDisplayNameKey();
        return PlaceholderValues.builder().putComponent("phase",language.getComponent(player.getUniqueId(),"fk.phase-"+session.state().name().toLowerCase(Locale.ROOT))).putComponent("next_phase",language.getComponent(player.getUniqueId(),"fk.phase-"+session.nextPhase().name().toLowerCase(Locale.ROOT))).putComponent("team",language.getComponent(player.getUniqueId(),teamKey)).putComponent("kit",language.getComponent(player.getUniqueId(),"fk.kit-"+session.preferredKit(player.getUniqueId()))).put("time",session.remainingTime(elapsed)).putComponent("map",language.getComponent(player.getUniqueId(),mapKey)).put("players",session.participantCount()).put("blue_players",survivors.getOrDefault(KingdomId.BLUE,0)).put("red_players",survivors.getOrDefault(KingdomId.RED,0)).put("green_players",survivors.getOrDefault(KingdomId.GREEN,0)).put("yellow_players",survivors.getOrDefault(KingdomId.YELLOW,0)).put("orange_players",survivors.getOrDefault(KingdomId.ORANGE,0)).build();
    }
    public void showEnemyHeart(Player player,Heart heart){
        TropicubeCore core=(TropicubeCore)Bukkit.getPluginManager().getPlugin("TropicubeCore");if(core==null)return;
        removeBar(player);var language=core.getLanguageManager();String teamKey="fk.team-"+heart.owner().name().toLowerCase(Locale.ROOT);
        PlaceholderValues values=PlaceholderValues.builder().putComponent("team",language.getComponent(player.getUniqueId(),teamKey)).put("heart_health",(int)Math.ceil(heart.health())).build();
        float progress=(float)Math.max(0,Math.min(1,heart.health()/heart.maximumHealth()));
        BossBar bar=BossBar.bossBar(language.getComponent(player.getUniqueId(),"fk.bossbar-enemy-heart",values),progress,BossBar.Color.RED,BossBar.Overlay.PROGRESS);
        BukkitTask task=Bukkit.getScheduler().runTaskLater(plugin,()->{HeartBar current=bars.get(player.getUniqueId());if(current!=null&&current.bar()==bar)removeBar(player);},100L);
        bars.put(player.getUniqueId(),new HeartBar(bar,task));player.showBossBar(bar);
    }
    public void remove(Player player){removeBar(player);boards.remove(player.getUniqueId());player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());player.sendPlayerListHeaderAndFooter(Component.empty(),Component.empty());}
    public void clear(){Bukkit.getServicesManager().unregister(UiReloadParticipant.class,this);for(Player player:Bukkit.getOnlinePlayers()){remove(player);player.playerListName(Component.text(visibleName(player)));}boards.clear();}
    private void removeBar(Player player){HeartBar previous=bars.remove(player.getUniqueId());if(previous!=null){previous.task().cancel();player.hideBossBar(previous.bar());}}
    private void applyPlayerListName(Player player){KingdomId team=session.state()==GameState.WAITING||session.state()==GameState.COUNTDOWN?session.preferredKingdom(player.getUniqueId()):session.kingdomOf(player);player.playerListName(Component.text(visibleName(player),color(team)));}
    private static String visibleName(Player player){String value=PlainTextComponentSerializer.plainText().serialize(player.displayName());return value.isBlank()?player.getName():value;}
    private static NamedTextColor color(KingdomId team){if(team==null)return NamedTextColor.GRAY;return switch(team){case BLUE->NamedTextColor.BLUE;case RED->NamedTextColor.RED;case GREEN->NamedTextColor.GREEN;case YELLOW->NamedTextColor.YELLOW;case ORANGE->NamedTextColor.GOLD;};}
    static boolean showLine(String key,Set<KingdomId> active){return switch(key){case "fk.sb-blue"->active.contains(KingdomId.BLUE);case "fk.sb-red"->active.contains(KingdomId.RED);case "fk.sb-green"->active.contains(KingdomId.GREEN);case "fk.sb-yellow"->active.contains(KingdomId.YELLOW);case "fk.sb-orange"->active.contains(KingdomId.ORANGE);default->true;};}
    @Override public Runnable prepareReload(){ScoreboardTemplate next=ScoreboardTemplate.load(plugin,"scoreboards.yml","fallenkingdoms");TablistTemplate nextTab=TablistTemplate.load(plugin,"tablists.yml","fallenkingdoms");return()->{scoreboard=next;tablist=nextTab;};}
    @Override public void refreshViewers(){updateAll(session.elapsedSeconds());}
    private record HeartBar(BossBar bar,BukkitTask task){}
}
