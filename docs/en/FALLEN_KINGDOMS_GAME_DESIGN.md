# Epicube Fallen Kingdoms — historical game design and Tropicube target

## Document purpose

This document preserves the historical design references used by the Tropicube adaptation. As of 14 September 2026, `tropicube-fallenkingdoms` provides a playable Paper V1 and its active Velocity template uses Cactus. Where sources are incomplete, the technical specification records explicit V1 decisions and temporary assumptions.

## Planned Tropicube adaptation

Tropicube keeps the defining loop: several teams build, gather resources, defend a heart, and organize sieges. The approved target has two to five balanced teams, four to six players per team, the Miner, Farmer, Scout, Enchanter, and Alchemist kits, and preparation, PvP, siege, sudden-death, and result phases. Every player also receives 32 steaks at match start.

Players will choose Fallen Kingdoms from **Games**, join a public `QUICK_PLAY` instance, or create a public/private custom match when their access level permits it. Team, kit, and map choices belong to localized in-instance menus; players arriving after the roster is locked spectate. At the result, Velocity returns players to a lobby and destroys the disposable game instance.

The target timeline opens PvP at 5 minutes, assault at 25 minutes, sudden death at 30 minutes, and resolves remaining survivors at 45 minutes. Players may build freely in common ground and their own base; only TNT may be placed inside an enemy base, starting with assault.

All player UI follows the current Tropicube rules: Adventure/MiniMessage, `fr`, `en`, `de`, and `es` resources, explicit click outcomes, `NetworkMenuStyle` framing, and live refresh after `/lang`. Bedrock players connected through Geyser/Floodgate use the same flow with compatible visual fallbacks.

Fallen Kingdoms ranked play is planned **after the V1 foundation**. It will use fixed formats designed for FK, and every player will have a Fallen Kingdoms Elo independent from their SheepWars Elo. Multi-kingdom rating and queue composition rules will be approved with that later ranked phase; SheepWars 4v4/8v8 rules are not defaults for FK.

## 1. General concept

Fallen Kingdoms is a long-form team strategy mode combining survival, building, resource management, territorial defense, PvP, and sieges. Several kingdoms establish fortified bases around a destructible heart. Teams develop behind staged protections before combat and heart attacks become available.

The game rewards specialization and coordination rather than isolated mechanical skill. Miners secure ore, farmers sustain the kingdom, scouts gather information, and enchanters improve equipment. A kingdom remains active while its heart and eligible players satisfy the current phase rules.

## 2. Difference from the original community mode

Epicube's interpretation automated the timeline, team assignment, kits, protected zones, hearts, scoreboards, and announcements. It converted a largely self-managed community format into a reliable public-server minigame with enforceable phases and clear victory rules.

## 3. Lobby and preparation

Before the match, players choose a kingdom and a kit from inventory menus. The lobby presents team capacity, selected kit, map, phase timings, and start conditions. A return item leaves the queue safely.

Teams must remain balanced. Missing choices receive deterministic defaults when the countdown ends. The match does not start when the selected map lacks required kingdom regions, hearts, spawns, or resource areas.

## 4. Kits

### Miner

The miner accelerates underground progression and ore acquisition. Its tools and bonuses make it the main source of iron, gold, diamond, stone, and defensive blocks.

### Farmer

The farmer establishes food production and renewable supplies. This role supports sustained defense, recovery after deaths, and long preparation phases.

### Scout

The scout favors mobility, reconnaissance, surface exploration, and early warning. It discovers enemy development and prepares routes without bypassing phase protections.

### Enchanter

The enchanter develops experience and enchantment infrastructure, then converts shared resources into stronger team equipment.

### Role complementarity

No kit should replace the others. Shared storage, negotiated priorities, and coordinated distribution of scarce resources are part of the intended experience. Kits provide direction without permanently preventing players from performing basic survival tasks.

## 5. Maps

Maps contain multiple symmetric kingdom territories around a contested central area. Each territory defines a protected base, heart location, team spawn, build boundary, and access routes. Neutral land supplies exploration and combat space.

Historical references include the Cactus, Apocalypse, and Yeti themes. A Tropicube map catalog may reproduce their strategic principles without copying proprietary assets.

## 6. Kingdom base

### Build zone

Players may freely develop their own territory within phase rules. Outside territory, restrictions prevent griefing, impassable structures, and abuse of protected blocks.

### Heart

The heart is the kingdom's strategic objective. It remains invulnerable until the siege phase. Damage, destruction, restoration, and ownership are server-controlled and announced globally.

### Heart defense

Teams may build layered defenses, routes, traps allowed by configuration, and fallback positions. Protections prevent burying the heart beyond legal limits, surrounding it with forbidden materials, or exploiting map boundaries.

## 7. Resource economy

Essential resources include food, wood, stone, iron, gold, diamonds, experience, enchanting supplies, arrows, and defensive blocks. The map and kits distribute acquisition opportunities while preserving contestable neutral areas.

Death carries a meaningful cost through dropped or lost equipment and respawn delay. Teams therefore maintain reserve equipment and protected storage. Exact item retention rules belong to configuration and the V1 technical contract.

## 8. Match phases

### Phase 1 — Start and occupation

Players reach their kingdom, inspect the heart, claim roles, and establish initial gathering routes. Direct aggression is blocked.

### Phase 2 — Kingdom development

Teams build defenses, farms, mines, storage, and equipment. Cross-border griefing and PvP remain restricted.

### Phase 3 — PvP opens

Players may fight in authorized areas, scout enemy approaches, and contest neutral resources. Hearts remain protected.

### Phase 4 — Siege preparation

Teams finalize assault equipment, rally points, diplomatic choices, and defensive rotations. Announcements make the upcoming vulnerability explicit.

### Phase 5 — Assaults open

Enemy bases and hearts become attackable under controlled rules. Teams coordinate breaches while retaining defenders.

### Phase 6 — Kingdom destruction

When a heart is destroyed, the kingdom enters a ruined or last-life condition defined by the technical specification. Eliminations become permanent according to the confirmed V1 rules.

### Phase 7 — End game

The final surviving kingdom wins. A time limit may trigger sudden death or a deterministic ranking rather than allowing an endless stalemate.

## 9. Building restrictions

Inside their own base, players receive the broadest construction freedom. Neutral land allows tactical structures within anti-grief limits. Enemy territory becomes modifiable only in authorized phases and must prevent lava, water, obsidian, bedrock, portal, height-limit, and unbreakable-containment abuse.

Tropicube V1 does not journal blocks to replay a match in the same process: every match uses an immutable map in a disposable instance, then removes its container and ephemeral volume.

## 10. Combat and assaults

Combat uses normal Minecraft mechanics enhanced by kits and phase rules. Death should matter without making early mistakes permanently remove a player before hearts become vulnerable. Successful attacks require equipment, navigation, distraction, breach work, and coordinated timing.

Defenders use terrain, fortifications, reserves, callouts, and counterattacks. Spawn protection must prevent camping without becoming an exploitable combat shield.

## 11. Diplomacy and strategy

Temporary alliances, non-aggression arrangements, shared threats, and betrayals naturally emerge in multi-kingdom games. The server does not need to enforce political agreements, but chat, team identity, and objective announcements must keep decisions understandable.

## 12. Interface and automation

The interface exposes current phase, next transition, kingdom, heart status, living or eligible players, and match time. Announcements cover phase changes, PvP opening, siege opening, heart damage, kingdom ruin, elimination, sudden death, and victory.

Menus handle team and kit selection. Administrative commands support controlled setup and diagnostics without becoming normal gameplay tools.

## 13. Key design qualities

- a readable multi-stage timeline;
- strong team roles without rigid class locks;
- visible kingdom objectives;
- meaningful preparation and reserve management;
- attacks that require coordination;
- reliable anti-abuse protections;
- deterministic cleanup and replayability.

## 14. Example match

Four kingdoms enter their bases and divide work. Miners and farmers establish the economy while scouts map routes and enchanters prepare upgrades. PvP then opens in neutral land, revealing team strength. As siege time approaches, teams reinforce hearts and assemble assault groups. One kingdom breaches a rival, destroys its heart, and hunts its remaining last-life players while another attacks the weakened victor. Sudden death prevents a fortified stalemate, and the final kingdom receives the result before all players return to the lobby.

## 15. Reliability of information

Historical details should be treated according to source confidence. Confirmed Tropicube V1 rules in the [technical specification](FALLEN_KINGDOMS_TECHNICAL_SPEC.md) override uncertain memories or third-party descriptions. Reversible balance values remain configurable; architectural or experience-changing ambiguity must be resolved before implementation.

The best-attested historical elements are the five colors, approximately six players per kingdom, Cactus/Apocalypse/Yeti, the four kits, the End Crystal heart, elimination following its destruction, and spectator handling. Exact phase lengths, kit durability, heart health, and building exceptions varied. The 500-health reconstruction and detailed kit inventories are useful references rather than surviving official documentation. Sources include a [2016 Epicube-style technical reconstruction](https://skript-mc.fr/forum/topic/8182-un-skript-fallenkindoms-style-epicube/), an [Epicube promotional video](https://www.youtube.com/watch?v=lYZv11VIid8), an [Apocalypse match](https://www.youtube.com/watch?v=zhPhpN6Nh4A), a [historical Epicube playlist](https://www.youtube.com/playlist?list=PLzHrXSFcjLGp4OiA-NKbECQmPdT1-nnsB), and [community rules for the original concept](https://www.jeuxvideo.com/forums/1-24777-2074535-1-0-1-0-principe-regles-du-fallen-kingdom.htm).
