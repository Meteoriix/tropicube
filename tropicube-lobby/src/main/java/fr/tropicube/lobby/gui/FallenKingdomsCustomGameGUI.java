package fr.tropicube.lobby.gui;

import fr.tropicube.core.menu.NetworkMenuStyle;
import fr.tropicube.lobby.utils.ItemBuilder;
import fr.tropicube.lobby.utils.LangHelper;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

/** Host-only setup screen whose output is an allowlisted set of FK container options. */
public final class FallenKingdomsCustomGameGUI {
    public static final int CREATE_SLOT = 49;
    public static final int BACK_SLOT = 45;
    public static final int CLOSE_SLOT = 53;
    private static final Map<Integer, Option> SLOTS = Map.ofEntries(
            Map.entry(10, Option.COUNTDOWN), Map.entry(11, Option.MAX_TEAM),
            Map.entry(12, Option.MAX_KINGDOMS), Map.entry(13, Option.PVP),
            Map.entry(14, Option.ASSAULT), Map.entry(15, Option.SUDDEN_DEATH),
            Map.entry(16, Option.FORCE_END), Map.entry(19, Option.HEART),
            Map.entry(20, Option.RESPAWN), Map.entry(21, Option.COMBAT),
            Map.entry(22, Option.RUINS), Map.entry(28, Option.MINER),
            Map.entry(29, Option.FARMER), Map.entry(30, Option.SCOUT),
            Map.entry(31, Option.ENCHANTER));

    private FallenKingdomsCustomGameGUI() { }

    public static final class Holder implements InventoryHolder {
        private final String templateId;
        private final boolean whitelisted;
        private final Map<Option, Integer> indexes = new java.util.EnumMap<>(Option.class);
        private Inventory inventory;

        public Holder(String templateId, boolean whitelisted) {
            this.templateId = templateId;
            this.whitelisted = whitelisted;
            for (Option option : Option.values()) indexes.put(option, 0);
            indexes.put(Option.COUNTDOWN, 1);
            indexes.put(Option.MAX_TEAM, 2);
            indexes.put(Option.MAX_KINGDOMS, 3);
            indexes.put(Option.PVP, 2);
            indexes.put(Option.ASSAULT, 2);
            indexes.put(Option.SUDDEN_DEATH, 2);
            indexes.put(Option.FORCE_END, 2);
            indexes.put(Option.HEART, 1);
            indexes.put(Option.RESPAWN, 1);
            indexes.put(Option.RUINS, 1);
        }

        public String templateId() { return templateId; }
        public boolean whitelisted() { return whitelisted; }

        public void cycle(int slot, boolean backwards) {
            Option option = SLOTS.get(slot);
            if (option == null) return;
            if (option.kitId != null && current(option).equals("true") && enabledKitCount() == 1) return;
            int size = option.values.length;
            indexes.compute(option, (_, value) -> Math.floorMod(value + (backwards ? -1 : 1), size));
            normalizeTimeline();
        }

        public String encodedOptions() {
            Map<String, String> values = new LinkedHashMap<>();
            for (Option option : Option.values()) {
                if (option.environment != null) values.put(option.environment, current(option));
            }
            String kits = java.util.stream.Stream.of(Option.MINER, Option.FARMER, Option.SCOUT, Option.ENCHANTER)
                    .filter(option -> current(option).equals("true"))
                    .map(option -> option.kitId)
                    .collect(java.util.stream.Collectors.joining(","));
            values.put("FK_ENABLED_KITS", kits.isBlank() ? "miner" : kits);
            String[] ruins = current(Option.RUINS).split(":");
            values.put("FK_RUIN_WAVES", ruins[0]);
            values.put("FK_RUIN_RADIUS", ruins[1]);
            values.put("FK_RUIN_DESTRUCTION_RATIO", ruins[2]);
            values.put("FK_AUTO_START", "false");
            return values.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(java.util.stream.Collectors.joining(";"));
        }

        private String current(Option option) { return option.values[indexes.get(option)]; }
        private int number(Option option) { return Integer.parseInt(current(option)); }
        private long enabledKitCount() {
            return java.util.stream.Stream.of(Option.MINER, Option.FARMER, Option.SCOUT, Option.ENCHANTER)
                    .filter(option -> current(option).equals("true")).count();
        }

        private void normalizeTimeline() {
            int pvp = number(Option.PVP);
            int assault = Math.max(number(Option.ASSAULT), pvp + 300);
            int suddenDeath = Math.max(number(Option.SUDDEN_DEATH), assault + 600);
            int forceEnd = Math.max(number(Option.FORCE_END), suddenDeath + 600);
            selectAtLeast(Option.ASSAULT, assault);
            selectAtLeast(Option.SUDDEN_DEATH, suddenDeath);
            selectAtLeast(Option.FORCE_END, forceEnd);
        }

        private void selectAtLeast(Option option, int minimum) {
            for (int index = 0; index < option.values.length; index++) {
                if (Integer.parseInt(option.values[index]) >= minimum) {
                    indexes.put(option, index);
                    return;
                }
            }
            indexes.put(option, option.values.length - 1);
        }

        @Override public @NonNull Inventory getInventory() { return inventory; }
    }

    public static Inventory build(Player player, Holder holder) {
        Inventory inventory = Bukkit.createInventory(holder, 54,
                LangHelper.component(player, "lobby.fk-custom-title"));
        holder.inventory = inventory;
        NetworkMenuStyle.applyFrame(inventory, player, "network");
        SLOTS.forEach((slot, option) -> inventory.setItem(slot, new ItemBuilder(option.material)
                .name(LangHelper.get(player, option.labelKey))
                .lore("<gray>" + display(player, holder.current(option)), "",
                        LangHelper.get(player, "lobby.fk-custom-cycle"))
                .build()));
        inventory.setItem(CREATE_SLOT, new ItemBuilder(Material.EMERALD_BLOCK)
                .name(LangHelper.get(player, "lobby.fk-custom-create"))
                .lore(LangHelper.get(player, "lobby.fk-custom-create-lore"))
                .glow(player).build());
        inventory.setItem(BACK_SLOT, ItemBuilder.backButton(player));
        inventory.setItem(CLOSE_SLOT, ItemBuilder.closeButton(player));
        return inventory;
    }

    private static String display(Player player, String value) {
        return switch (value) {
            case "true" -> LangHelper.get(player, "lobby.fk-custom-enabled");
            case "false" -> LangHelper.get(player, "lobby.fk-custom-disabled");
            case "PAPER_26_2" -> LangHelper.get(player, "lobby.fk-custom-paper");
            case "LEGACY_1_8" -> LangHelper.get(player, "lobby.fk-custom-legacy");
            case "3:8:0.2" -> LangHelper.get(player, "lobby.fk-custom-ruins-light");
            case "3:8:0.35" -> LangHelper.get(player, "lobby.fk-custom-ruins-standard");
            case "5:12:0.5" -> LangHelper.get(player, "lobby.fk-custom-ruins-heavy");
            default -> value;
        };
    }

    public enum Option {
        COUNTDOWN("FK_COUNTDOWN_SECONDS", Material.CLOCK, "lobby.fk-custom-countdown", new String[]{"10", "30", "60"}, null),
        MAX_TEAM("FK_MAX_PLAYERS_PER_KINGDOM", Material.PLAYER_HEAD, "lobby.fk-custom-max-team", new String[]{"4", "5", "6"}, null),
        MAX_KINGDOMS("FK_MAX_KINGDOMS", Material.WHITE_BANNER, "lobby.fk-custom-max-kingdoms", new String[]{"2", "3", "4", "5"}, null),
        PVP("FK_PVP_AT_SECONDS", Material.IRON_SWORD, "lobby.fk-custom-pvp", new String[]{"300", "600", "900"}, null),
        ASSAULT("FK_ASSAULT_AT_SECONDS", Material.TNT, "lobby.fk-custom-assault", new String[]{"900", "1200", "1500"}, null),
        SUDDEN_DEATH("FK_SUDDEN_DEATH_AT_SECONDS", Material.ENDER_EYE, "lobby.fk-custom-sudden", new String[]{"2700", "3600", "4500"}, null),
        FORCE_END("FK_FORCE_END_AT_SECONDS", Material.BARRIER, "lobby.fk-custom-end", new String[]{"3600", "4500", "5400"}, null),
        HEART("FK_HEART_HEALTH", Material.END_CRYSTAL, "lobby.fk-custom-heart", new String[]{"250", "500", "1000"}, null),
        RESPAWN("FK_RESPAWN_DELAY_SECONDS", Material.TOTEM_OF_UNDYING, "lobby.fk-custom-respawn", new String[]{"5", "10", "15"}, null),
        COMBAT("FK_COMBAT_PROFILE", Material.DIAMOND_SWORD, "lobby.fk-custom-combat", new String[]{"PAPER_26_2", "LEGACY_1_8"}, null),
        RUINS(null, Material.CRACKED_STONE_BRICKS, "lobby.fk-custom-ruins", new String[]{"3:8:0.2", "3:8:0.35", "5:12:0.5"}, null),
        MINER(null, Material.IRON_PICKAXE, "fk.kit-miner", new String[]{"true", "false"}, "miner"),
        FARMER(null, Material.WHEAT, "fk.kit-farmer", new String[]{"true", "false"}, "farmer"),
        SCOUT(null, Material.FEATHER, "fk.kit-scout", new String[]{"true", "false"}, "scout"),
        ENCHANTER(null, Material.ENCHANTING_TABLE, "fk.kit-enchanter", new String[]{"true", "false"}, "enchanter");

        private final String environment;
        private final Material material;
        private final String labelKey;
        private final String[] values;
        private final String kitId;

        Option(String environment, Material material, String labelKey, String[] values, String kitId) {
            this.environment = environment;
            this.material = material;
            this.labelKey = labelKey;
            this.values = values;
            this.kitId = kitId;
        }
    }
}
