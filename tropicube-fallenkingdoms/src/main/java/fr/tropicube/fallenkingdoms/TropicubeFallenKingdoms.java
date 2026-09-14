package fr.tropicube.fallenkingdoms;

import fr.tropicube.fallenkingdoms.config.FallenKingdomsSettings;
import fr.tropicube.fallenkingdoms.game.GameState;
import fr.tropicube.fallenkingdoms.game.GameStateMachine;
import fr.tropicube.fallenkingdoms.game.CactusSession;
import fr.tropicube.fallenkingdoms.listener.GameListener;
import fr.tropicube.core.TropicubeCore;
import fr.tropicube.language.PlaceholderValues;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/** Boots one ephemeral Fallen Kingdoms Paper instance. */
public final class TropicubeFallenKingdoms extends JavaPlugin {
    private final GameStateMachine stateMachine = new GameStateMachine();
    private FallenKingdomsSettings settings;
    private CactusSession session;
    @Override public void onEnable() {
        saveDefaultConfig();
        try { settings = FallenKingdomsSettings.load(getConfig()); session = new CactusSession(this, stateMachine); getServer().getPluginManager().registerEvents(new GameListener(session), this); }
        catch (IllegalArgumentException exception) { getLogger().severe("Configuration Fallen Kingdoms invalide: " + exception.getMessage()); getServer().getPluginManager().disablePlugin(this); }
    }
    @Override public void onDisable() { if (session != null) session.shutdown(); if (stateMachine.state().active()) stateMachine.transitionTo(GameState.ENDING); }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("fallenkingdoms.admin")) { message(sender, "fk.permission-denied"); return true; }
        String action = args.length == 0 ? "status" : args[0].toLowerCase(java.util.Locale.ROOT);
        if (action.equals("status")) status(sender);
        else if (action.equals("start")) message(sender, session.startCountdown() ? "fk.countdown-started" : "fk.transition-refused");
        else if (action.equals("cancel")) message(sender, session.cancelCountdown() ? "fk.countdown-cancelled" : "fk.transition-refused");
        else if (action.equals("stop")) { session.end(); message(sender, "fk.stopped"); }
        else if (action.equals("reload") && stateMachine.state() == GameState.WAITING) { reloadConfig(); settings = FallenKingdomsSettings.load(getConfig()); message(sender, "fk.reloaded"); }
        else message(sender, "fk.usage");
        return true;
    }
    public FallenKingdomsSettings settings() { return settings; }
    public GameStateMachine stateMachine() { return stateMachine; }
    private void message(CommandSender sender, String key, Object... arguments) {
        TropicubeCore core = (TropicubeCore) getServer().getPluginManager().getPlugin("TropicubeCore");
        if (core != null) sender.sendMessage(core.getLanguageManager().getComponent(
                sender instanceof org.bukkit.entity.Player player ? player.getUniqueId() : null, key, arguments));
    }
    private void status(CommandSender sender) {
        TropicubeCore core = (TropicubeCore) getServer().getPluginManager().getPlugin("TropicubeCore");
        if (core != null) sender.sendMessage(core.getLanguageManager().getComponent(
                sender instanceof org.bukkit.entity.Player player ? player.getUniqueId() : null, "fk.status",
                PlaceholderValues.of("state", stateMachine.state().name())));
    }
}
