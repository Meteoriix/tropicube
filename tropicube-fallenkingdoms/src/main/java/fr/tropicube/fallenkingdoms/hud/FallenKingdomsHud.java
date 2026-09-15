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
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Scoreboard;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Localized scoreboard, tablist and heart bossbar for every FK viewer. */
public final class FallenKingdomsHud implements UiReloadParticipant {
    private final TropicubeFallenKingdoms plugin; private final GameSession session;
    private final Map<UUID, Scoreboard> boards=new HashMap<>(); private final Map<UUID, BossBar> bars=new HashMap<>();
    private volatile ScoreboardTemplate scoreboard; private volatile TablistTemplate tablist;
    public FallenKingdomsHud(TropicubeFallenKingdoms plugin,GameSession session){this.plugin=plugin;this.session=session;prepareReload().run();Bukkit.getServicesManager().register(UiReloadParticipant.class,this,plugin,ServicePriority.Normal);}
    public void updateAll(int elapsed){for(Player player:Bukkit.getOnlinePlayers())update(player,elapsed);}
    public void update(Player player,int elapsed){
        TropicubeCore core=(TropicubeCore)Bukkit.getPluginManager().getPlugin("TropicubeCore");if(core==null)return;
        var language=core.getLanguageManager(); KingdomId team=session.kingdomOf(player);
        PlaceholderValues values=values(player,elapsed,language,team);
        Scoreboard board=boards.computeIfAbsent(player.getUniqueId(),_ -> Bukkit.getScoreboardManager().getNewScoreboard());
        var old=board.getObjective("fallenkingdoms");if(old!=null)old.unregister();
        var objective=board.registerNewObjective("fallenkingdoms",Criteria.DUMMY,language.getComponent(player.getUniqueId(),scoreboard.titleKey()));objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        String variant=session.state()==GameState.WAITING||session.state()==GameState.COUNTDOWN?"waiting":session.state()==GameState.ENDING||session.state()==GameState.ENDED?"ending":"active";
        int score=15;for(var line:scoreboard.lines(variant)){var entry=objective.getScore("fk_"+score);entry.setScore(score--);entry.customName(line.blank()?Component.empty():language.getComponent(player.getUniqueId(),line.key(),values));}
        player.setScoreboard(board);var tabVariant=tablist.variant(variant);player.sendPlayerListHeaderAndFooter(language.getComponent(player.getUniqueId(),tabVariant.headerKey(),values),language.getComponent(player.getUniqueId(),tabVariant.footerKey(),values));
        Heart heart=team==null?null:session.heart(team);BossBar previous=bars.remove(player.getUniqueId());if(previous!=null)player.hideBossBar(previous);
        if(heart!=null&&heart.state()!=fr.tropicube.fallenkingdoms.game.HeartState.DESTROYED){float progress=(float)Math.max(0,Math.min(1,heart.health()/heart.maximumHealth()));BossBar bar=BossBar.bossBar(language.getComponent(player.getUniqueId(),"fk.bossbar-heart",values),progress,BossBar.Color.RED,BossBar.Overlay.PROGRESS);bars.put(player.getUniqueId(),bar);player.showBossBar(bar);}
    }
    private PlaceholderValues values(Player player,int elapsed,fr.tropicube.core.managers.LanguageManager language,KingdomId team){
        Map<KingdomId,Integer> survivors=session.survivorCounts();Map<KingdomId,Double> health=new EnumMap<>(KingdomId.class);for(KingdomId id:KingdomId.values()){Heart heart=session.heart(id);health.put(id,heart==null?0:heart.health());}
        boolean waiting=session.state()==GameState.WAITING||session.state()==GameState.COUNTDOWN;
        KingdomId displayedTeam=team==null&&waiting?session.preferredKingdom(player.getUniqueId()):team;
        String teamKey=displayedTeam==null?(waiting?"fk.choice-none":"fk.team-spectator"):"fk.team-"+displayedTeam.name().toLowerCase(java.util.Locale.ROOT);
        String mapKey=waiting?session.waitingMapDisplayNameKey(player.getUniqueId()):session.mapDisplayNameKey();
        return PlaceholderValues.builder().putComponent("phase",language.getComponent(player.getUniqueId(),"fk.phase-"+session.state().name().toLowerCase(java.util.Locale.ROOT))).putComponent("team",language.getComponent(player.getUniqueId(),teamKey)).putComponent("kit",language.getComponent(player.getUniqueId(),"fk.kit-"+session.preferredKit(player.getUniqueId()))).put("time",session.remainingTime(elapsed)).putComponent("map",language.getComponent(player.getUniqueId(),mapKey)).put("players",session.participantCount()).put("blue_players",survivors.getOrDefault(KingdomId.BLUE,0)).put("red_players",survivors.getOrDefault(KingdomId.RED,0)).put("green_players",survivors.getOrDefault(KingdomId.GREEN,0)).put("yellow_players",survivors.getOrDefault(KingdomId.YELLOW,0)).put("orange_players",survivors.getOrDefault(KingdomId.ORANGE,0)).put("blue_health",health.get(KingdomId.BLUE).intValue()).put("red_health",health.get(KingdomId.RED).intValue()).put("green_health",health.get(KingdomId.GREEN).intValue()).put("yellow_health",health.get(KingdomId.YELLOW).intValue()).put("orange_health",health.get(KingdomId.ORANGE).intValue()).put("heart_health",team==null?0:health.get(team).intValue()).build();
    }
    public void clear(){Bukkit.getServicesManager().unregister(UiReloadParticipant.class,this);for(Player player:Bukkit.getOnlinePlayers()){BossBar bar=bars.remove(player.getUniqueId());if(bar!=null)player.hideBossBar(bar);player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());player.sendPlayerListHeaderAndFooter(Component.empty(),Component.empty());}boards.clear();}
    @Override public Runnable prepareReload(){ScoreboardTemplate next=ScoreboardTemplate.load(plugin,"scoreboards.yml","fallenkingdoms");TablistTemplate nextTab=TablistTemplate.load(plugin,"tablists.yml","fallenkingdoms");return()->{scoreboard=next;tablist=nextTab;};}
    @Override public void refreshViewers(){updateAll(session.elapsedSeconds());}
}
