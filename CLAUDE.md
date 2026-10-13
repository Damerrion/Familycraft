# FenixCore — FenixKingdom-addon voor KingdomCraft

Paper 1.21.4 plugin (Java 21, Maven Wrapper). Taal van de gebruiker: Nederlands. Server: FenixKingdom,
KingdomCraft 7.1.6 + KingdomCraft Premium 7.5.1, Minecraft 1.21.4/1.21.5 (Paper), permissies via LuckPerms.

## Opbouw
Samengevoegd uit twee losse plugins (gebruiker, 2026-10-13: "samen voegen ... 1 groote addon ... rename naar FenixCore"):
- `nl.fenix.core.FenixCore` (JavaPlugin): neemt bij de eerste start de oude bestanden over
  (`plugins/KingdomWandsAddon/config.yml` -> `wands.yml`, `plugins/FamilyCraft/{config,families}.yml` ->
  `family.yml`/`families.yml`, kopie), zet modules aan/uit (`config.yml` -> `modules.*`) en start een module niet
  als de oude plugin nog geïnstalleerd is (anders dubbele afhandeling).
- `nl.fenix.core.wands` — wands-module, voorheen repo `Damerrion/Kingdom-Wands-Addon` (`nl.kwa.addon`).
  `WandsModule` (ex `KwaPlugin`) heeft dezelfde methodes als een JavaPlugin (`getConfig`, `saveConfig`,
  `reloadConfig`, `getLogger`, `getServer`, `getPluginMeta`) maar met een eigen `wands.yml`; `plugin()` geeft de
  echte Plugin (scheduler, events).
- `nl.fenix.core.family` — family-module, voorheen FamilyCraft (`com.familycraft`). `FamilyModule` met `family.yml`.
- Commando's en permissies **ongewijzigd** (`/kwa`, `/bind`, `/unbind`, `/family`; `kwa.*`, `family.*`):
  LuckPerms op de server is daarop ingericht.
- **Wand-PDC-namespace blijft `kingdomwandsaddon`** (`WandItem.NAMESPACE`), niet de pluginnaam: anders worden
  bestaande wands niet meer herkend. Nooit veranderen.

## Bouwen / controleren
- GitHub Actions (`.github/workflows/build.yml`) bouwt met tests tegen de echte Paper-, KingdomCraft- (repo.jorisg.com)
  en Vault- (jitpack) repo's; dat is de officiële controle. In de cloud-sessie zijn papermc/jorisg/jitpack/spigot
  geblokkeerd en Maven Central soms rate-limited.
- KingdomCraft-API: `com.gufli.kingdomcraft.starter:api:7.1.1`; de gebruikte klassen (PlatformPlayer,
  PlayerChatEvent, PlaceholderManager, events, KingdomCraft) zijn identiek aan 7.1.6 (bron: github.com/guflimc/KingdomCraft,
  tags v7.1.1/v7.1.6). Belangrijk omdat `HoverReceiver` `PlatformPlayer` implementeert.
- KingdomCraft Premium-jar is geobfusceerd en heeft DRM: niet decompileren of meeleveren.

# Family-module

## Functies (afgesproken met de gebruiker)
- Familie = naam (geen cijfers, max 30, `_` = spatie) + tag (geen cijfers, max 3). `/family accept|cancel|create|disband|
  check|invite|kick|leave|members|info`, meeste acties alleen door de eigenaar. Maken: permissie `family.create`
  (gekocht), optioneel eenmalige prijs via Vault (`economy.create-cost`).
- Placeholders in KingdomCraft: `{family_tag}`, `{family_prefix}` (= `prefix-format` met tag, **incl. haakjes en spatie**,
  helemaal leeg zonder familie), `{family_name}`. Op de server staat in `chat.yml`:
  `{kingdom_prefix} {family_prefix}&r{rank_prefix}&f%player_name%`.
- Display name wordt herbouwd als `{kingdom_prefix} {family_prefix} %player%{kingdom_suffix}` (KingdomCraft hardcodet
  kingdom-prefix + naam).
- Chat-hover: KingdomCraft stuurt chat als platte tekst naar `PlayerChatEvent#getReceivers()`. `FamilyHover`
  (listener als laatste geregistreerd, 1 tick na start) vervangt de ontvangers door `HoverReceiver`-wrappers in een
  `ReceiverList` (contains op UUID, want KingdomCraft doet `receivers.contains(sender)` en social spy). De wrapper
  stuurt dezelfde regel als BungeeCord-chatcomponenten met hover/klik op het `{family_prefix}`-stuk; KingdomCraft
  doet verder alles zelf (cooldown, social spy, log, DiscordSRV).
- "Familie afstamming" = kingdom van het hoofd **bij de oprichting**, vastgelegd in families.yml (`kingdom`).
  Blijft staan als het hoofd van kingdom wisselt (gebruiker, 2026-10-13). Wordt alleen ingevuld als hij nog leeg is.
- GUI's (`family.gui`, Paper-inventories met `Gui`-holder + `GuiListener`): `/family list` (overzicht met banners),
  `/family editor` (crest, leden, `MemberGui`, `PickMemberGui`, `TreeGui` per generatie), `RenameGui` (popup).
  Gedeelde acties (rang, stamboom, crest, huwelijk, hernoemen) in `FamilyService`, zodat commando en GUI hetzelfde doen.
- Rangen (`FamilyRank`), **Nederlands** (gebruiker): Familiehoofd, Opvolger, Reserve-erfgenaam, Gemaal, Gemaal van de
  Erfgenaam, Kind van het Huis (standaard voor nieuwe leden). Ids in yml blijven Engels (family-head, successor, ...).
  `/family rank give <speler|UUID> <rang>` (ook NL-ids als opvolger); Familiehoofd geven = hoofd overdragen.
- `family-old-defaults.yml` (in de jar): oude standaardwaarden; staan die nog ongewijzigd in family.yml, dan worden
  ze bij start/reload vervangen door de nieuwe (bv. Engelse rangnamen). Nieuwe keys gaan via copyDefaults erin.
  Let op: Bukkit `getString(pad, def)` slaat de jar-defaults over.
- `/fenixcore reload [all|family|kwa]` (`fenixcore.reload`). Family-reload leest family.yml en past instellingen toe
  (niet families.yml; display-name/chat-hover aan/uit vraagt herstart).
- `/family list` = families van het kingdom waar je NU in zit (afstamming = vastgelegd kingdom van de familie);
  `/family list <kingdom>`, `/family list all`; een familietag opent die familie.
- Stamboom-regel: ouder/kind (ook voorouder/afstammeling) kan geen broer/zus zijn en andersom (`tree-sibling-parent`).
- Ontkoppelen kan ook met iemand in een andere familie (relaties horen bij de speler): "Ontkoppelen" in het lid-menu
  toont alle relaties (`Genealogy.relativesOf`), `/family tree unlink <lid> <speler>` (hoofd), en
  `/family tree unlink <speler>` = je eigen relatie verbreken (binnen dezelfde familie alleen via het hoofd).
- TreeGui: rij per generatie met gekleurde markering in kolom 0, koppels met roze ❤-ruit ertussen, gesorteerd op de
  plek van de ouders, naamkleur per rang (`gui.rank-colors`), scrollen omhoog/omlaag en links/rechts.
- Stamboom (`Genealogy`): ouder/kind (max 2 ouders, geen lussen), partner (1), broer/zus; per speler opgeslagen
  (`people:` in families.yml), alleen leden van dezelfde familie koppelen.
- Huwelijk (gebruiker, 2026-10-13): `/enlope <speler>` = verloving (ander klikt [Ja]); trouwmenu (`WeddingGui`,
  `/family marriage [speler]`): paar wisselt bruidegom/bruid en wie kiest (A); A kiest de optie; het **hoofd van de
  familie van de bruid** start. Uitkomst (`FamilyManager.typeOf`): hoofd+hoofd = automatisch fuseren tot "A-B"
  (bruid Gemaal, Opvolger/Reserve van B -> Kind); hoofd+Opvolger = Opvolger gaat over (Gemaal), Reserve schuift door;
  zelfde familie = alleen partners; anders kiest A uit ABSORB_LINE / ABSORB_PARTNER_LINE (andersom: B in A) /
  JOIN_PERSON / JOIN_PARTNER (andersom: partner naar A) / NEW_LINE (needs-rename) / BRANCH ("A-B" aftakking, alleen
  het paar). ABSORB_LINE en NEW_LINE alleen als A hoofd/Opvolger van lijn A is; ABSORB_PARTNER_LINE alleen als de
  partner hoofd/Opvolger van lijn B is.
  Afkomst per speler (`Genealogy.originsOf`, `origins:` in people) wordt bij elke overgang aangevuld en getoond
  ("Afkomst: A → B"). Verlovingen worden bewaard (`engagements:` in families.yml). Naam samenvoegen: gedeeld
  achtervoegsel ("Dokker Lijn" + "Adamantie Lijn" -> "Dokker-Adamantie Lijn", tag DAL), anders "A-B"; vanaf 4 families
  in de naam (`marriage.rename-at-families`) of bezette naam/tag: `needs-rename` + popup, `/family rename`.
- Dev mode family (gebruiker: "alle functies testen"): `/family dev` (`family.dev`, per speler, in geheugen) = admin-
  bypass overal (`FamilyService.isAdmin`) + nep-spelers (`FamilyManager.addFake/createFakeFamily/forceEngagement/
  clearFakes`, UUID = nameUUID "FenixCoreFake:<naam>", bewaard als `fakes:` in families.yml).
- `family-old-defaults.yml`: secties v1, v2, ... met oude standaardwaarden per versie (nieuwe versie onderaan erbij).
- Opslaan van families.yml gebeurt op de main thread (een eerdere versie riep de scheduler aan met een verkeerde
  methode-signatuur en gaf "An internal error occurred").

# Wands-module

### Context: wat we al weten
- KingdomWands (kingdomplugins.nl, free jar 7.5 "ExortisMagic") is **geobfusceerd (Paramorphism)**, heeft **geen API en geen eigen events**.
  Dus de wand moet in DEZE addon zelf zitten, niet in KingdomWands. De free jar is eens geanalyseerd om de
  KingdomWands-spells na te bouwen, maar dat is **gestopt** (zie beslissingen); gedecompileerde code komt **nooit** in
  de repo. Let op: de free jar heeft een **backdoor** (hardcoded developer-UUIDs die whitelist/permissies omzeilen, remote
  kill switch, telemetrie): niet op de server gebruiken.
- KingdomWands gebruikt `/kw`, `/bind`, `/unbind` en spell-permissies `kingdomwands.spells.<Spell>` (Premium 1.3 readme).
  Ons systeem moet **hetzelfde aanvoelen**: `/kwa <wand>`, `/kwa bind <slot> <spell>`, `/kwa unbind <slot>`, `/kwa bind all`, `/kwa spells`.
  Spell selecteren zoals bij KingdomWands (zie hieronder, controleer in-game hoe KingdomWands het doet en kopieer het gedrag).
- Server: **KingdomCraft Premium**, Minecraft **1.21.4**, Paper (versie onbekend). Permissies via **LuckPerms**.
- KingdomCraft Premium API: repo `https://repo.jorisg.com/releases`, `com.gufli.kingdomcraft.premium:api:7.1.2` (staat in pom.xml).
  - Instantie: `KingdomCraftPremium kdcp = KingdomCraftPremiumProvider.get();`
  - Javadoc: https://guflimc.github.io/KingdomCraft/premium/ (pakket `com.gufli.kingdomcraft.premium.api`)
  - Starter-API (ter referentie): `KingdomCraft kdc = KingdomCraftProvider.get(); kdc.getOnlineUser(name).getKingdom(); kdc.getRelations(kingdom)` -> `rel.getType()`
  - User/kingdom/rank/relation zitten NIET in de Premium-API maar in de kern-API `com.gufli.kingdomcraft.api`
    (artifact `com.gufli.kingdomcraft.starter:api`, ook in pom.xml). **Geverifieerd** tegen de broncode
    (raw.githubusercontent.com/guflimc/KingdomCraft, map `api/`):
    `KingdomCraftProvider.get()`, `kdc.getOnlineUser(UUID)` -> `User.getKingdom()`,
    `kdc.getKingdom(String)`, `Kingdom.getId()/getName()`, `kdc.getRelation(Kingdom, Kingdom)` -> `Relation.getType()`
    (`RelationType`: NEUTRAL, ENEMY, ALLY, TRUCE; null = neutraal).
  - Premium-javadoc staat op de `gh-pages`-branch van dezelfde repo (alleen extra modules: regions, events, power, ...).

### Beslissingen van de gebruiker
- Commando: `/kwa [wand]` en daarna `/kwa bind ...` etc., zelfde als KingdomWands.
- **Meerdere wands mogelijk** (later meer custom wands in deze addon): wands staan in `wands.yml` onder `wands.<id>`
  (`WandType`), spells in code (`Spell`). `/kwa <id> [speler]` geeft een wand, `/kwa create <id> [naam]` maakt de
  gekozen wand van het item in je hand (dus niet altijd de Deathwhistle). Per wand worden `kwa.wand.<id>` (krijgen)
  en `kwa.use.<id>` (gebruiken) automatisch geregistreerd (default op, kind van `kwa.*`; `kwa.use` = alle wands).
  Het `kingdom` per wand bepaalt wie gespaard wordt.
- Config met YAML-fout: nooit opslaan/omzetten (Bukkit geeft dan een lege config); standaard-wands uit de jar gebruiken.
- Oude config (`kingdom-name`, `wand.*`) wordt bij opstarten automatisch omgezet naar `wands.tygron`.
- Premade wands krijgen: **`/kwa get <wand>`** (bv. `/kwa get Tygron`; `/kwa get` zonder wand = menu). Oud `/kwa tygron` werkt ook nog.
- **Alleen de Tygron-wand** (gebruiker, 2026-10-11: "stop met de rest van de wands overzetten ... het wordt echt maar
  alleen een Addon"). De KingdomWands-wands en -spells (Empire, God, Blood, ...) zijn weer verwijderd: **niet opnieuw
  toevoegen** tenzij de gebruiker erom vraagt. Wel gebleven: `/kwa get`, wand-menu, teksten in `messages.*`.
- Tygron-wand: Itemnaam: **"Tygron Deathwhistle"**, lore alleen: "Gebruikt alleen door de Stamleider van Tygron" (config `wand.name` / `wand.lore`). Geen spells/besturing in de lore.
- Spell selecteren: melding **in de chat** (`[KWA] Scream Geselecteerd`), niet in de actionbar; geen melding bij het vastpakken van de wand.
- Gebruik **alleen via LuckPerms** (permissie `kwa.use`, die de admin aan de Tygron-leider geeft). **Geen** KingdomCraft-kingdom- of rangcontrole (onnodig werk).
- **Doelwitten**: iedereen, behalve het kingdom waar de wand aan gegeven is (`kingdom-name`) en de **bondgenoten (ALLY)** daarvan. Vijand/neutraal/truce/geen kingdom maakt niet uit: die worden allemaal geraakt.
- Wand kan **droppen als de leider doodgaat**. Wie hem oppakt kan hem alleen gebruiken met de LuckPerms-tag/permissie.
- **Geen kosten** (geen mana/hunger/durability), **alleen cooldowns**.
- Alleen deze twee spells:

#### Scream
- Hoorbaar tot **100 blokken** (per speler geluid afspelen binnen 100 blokken, hoog volume).
- Eigen effect **"Fear"**: Slowness + Weakness, met schud-gevoel (korte Nausea-ticks of camera-schud via ProtocolLib; ProtocolLib is optioneel).
- Visueel: **zwarte deeltjes in een cirkel die omhoog draaien en spinnen en in de lucht ontploffen**, daarna verspreidt het effect zich als golf door de lucht over ~**30 blokken**.
- Raakt iedereen behalve Tygron en bondgenoten van Tygron.
- **Cooldown** (config).
- Geluid zonder resourcepack: mix van `ENTITY_WARDEN_ROAR`/`ENTITY_GHAST_SCREAM`/`ENTITY_PHANTOM_AMBIENT` met lage pitch. Eigen geluid kan later via resourcepack.

#### Mark
- Binnen **50 blokken** kiest hij **willekeurig 5** spelers (geen Tygron/bondgenoten) en geeft ze **Glowing + Blindness** (volledig blind).
- Duur en cooldown instelbaar.
- Glowing is voor iedereen zichtbaar (ook de eigen kingdom). Wil de gebruiker dat alleen Tygron het ziet: scoreboard-teams of ProtocolLib (optioneel, later).

### Balans (voorstel, in wands.yml, door gebruiker akkoord op "mark is goed" + cooldowns)
Scream cooldown 90s, **Slowness + Weakness 10s, Nausea 5s** (gebruiker). Mark cooldown 150s, 5 doelen, **Glowing 30s, Blindness 1s** (gebruiker). Pas aan na testen.

### Te bouwen (in volgorde)
1. `./mvnw -q -DskipTests package` (Maven Wrapper; Windows: `mvnw.cmd`) laten slagen, of de GitHub Actions-build (`.github/workflows/build.yml`); KingdomCraft Premium API-klassen bekijken.
2. `WandsModule` (JavaPlugin): config laden, `/kwa` registreren, listeners.
3. `KingdomHook`: `getKingdomOf(Player)`, `isFriendly(Player, kingdomName)` = zelfde kingdom of ALLY (null-veilig).
4. `WandItem`: item maken en herkennen via PersistentDataContainer (niet via naam).
5. `/kwa tygron [speler]`, `/kwa bind all|<slot> <spell>`, `/kwa unbind <slot>`, `/kwa spells`, `/kwa info`, `/kwa reload`; tab-completion; permissies zie plugin.yml.
6. Spell selecteren: gedrag van KingdomWands nabootsen (kijk in-game; bij "hand" is het rechtermuisknop). Voorstel: scrollen (PlayerItemHeldEvent) of shift+rechtermuis om te wisselen, rechtermuis om te casten.
7. `Scream` en `Mark` met cooldowns per speler (Map<UUID, Long>), melding in actionbar.
8. ~~`PlayerDeathEvent`~~: niet nodig, de wand is een gewoon item en dropt vanzelf bij doodgaan (vanilla).
9. Testen op lokale Paper 1.21.4 server met KingdomCraft Premium + LuckPerms.

### Status (gebouwd)
- Stappen 2-8 zijn gebouwd: `WandsModule`, `KingdomHook`, `WandItem` (PDC: wand-id, bindingen, geselecteerd slot),
  `KwaCommand` (+ tab-completion), `WandListener`, `spell/ScreamSpell`, `spell/MarkSpell`.
- Besturing zoals KingdomWands ("spell selecten door rechtermuisknop"): **rechtermuis = volgende spell,
  shift + rechtermuis = vorige, linkermuis (ook op een speler) = casten**. Select-effect per wand via `/kwa effect`.
- Zonder `kwa.use.<wand>` (of `kwa.use`) is de wand een gewoon item: klikken doet niets en toont **geen** melding.
- Commando's zoals KingdomWands Premium 1.3: `/kwa tygron [speler]`, `/bind all|<slot> <spell>`, `/unbind <slot>`
  (werkt zonder /kwa zolang je de Deathwhistle vasthoudt; anders blijft /bind van KingdomWands zelf werken),
  `/kwa list|spell|spells|spelllist`, `/kwa effect <effect>`, `/kwa create <wand> [naam]`, `/kwa info`, `/kwa reload`.
- Casten op een speler via `PrePlayerAttackEntityEvent` (niet via damage-events: Thorns zou anders casten en
  KingdomCraft blokkeert slaan op bondgenoten). Wands zijn niet stapelbaar (`max_stack_size` 1).
- Permissies (alles **default: op**, niet-ops via LuckPerms): `kwa.*`, `kwa.use`, `kwa.wand.tygron`, `kwa.bind`,
  `kwa.unbind`, `kwa.spells`, `kwa.effect`, `kwa.create`, `kwa.admin`, `kwa.spells.*`, `kwa.spells.Scream`, `kwa.spells.Mark`.

- Elke spell heeft een eigen switch-toon (`<spell>.select-sound/-pitch/-volume`, alleen voor de speler; Bukkit-naam,
  Minecraft-key of resourcepack-geluid). Zonder toon speelt het geluid van het select-effect. Nieuwe spells: zelfde keys.
- Scream-deeltjes stijgen tot `scream.particle-height` (20) en worden met `force=true` verstuurd (zichtbaar tot 512
  blokken i.p.v. 32), zodat ze boven een slagveld te zien zijn.
- Scream: **geen schud-effect** (gebruiker), alleen Nausea van 5s (`scream.nausea-seconds`). Nausea-level maakt
  visueel niet uit; de client bouwt het effect langzaam op.
- `config-version` in wands.yml: `migrateConfig()` werkt oude configs bij (alleen oude standaardwaarden, nooit na YAML-fout).
- Wand kan niet gecraft worden (bot -> beenmeel geblokkeerd).
- **KingdomCraft is optioneel** (`softdepend`). De koppeling wordt 1 tick na het opstarten gezocht (laadvolgorde/pluginnaam
  maakt niet uit) en opnieuw bij `/kwa reload`. Zonder KingdomCraft raken spells iedereen. `/kwa info` toont de status.
- Build: GitHub Actions (`.github/workflows/build.yml`) bouwt tegen de echte Paper- en KingdomCraft-repo's: **geslaagd**.
- Getest: plugin laadt op Paper 1.21.5-114. Stap 9 (in-game testen van de spells) nog niet gedaan.

- **Zoals KingdomWands**: `/kwa get [wand] [speler]` + wand-menu (`WandMenu`, zoals /kw getwand), info-blok,
  hulppagina's `/kwa <n>`, alle teksten onder `messages.*` (KingdomWands-teksten voor bind/unbind), goud/grijze prefix,
  eerst effect dan melding bij selecteren, blokken breken geblokkeerd met wand, `/bind` afgevangen als geen andere
  plugin het heeft. config-version 6 haalt de KingdomWands-wands (met standaardnaam) en `kw-spells` die versie 5
  toevoegde weer uit bestaande configs, en zet `slots: 100` terug naar 9.
- Lokaal compileren tegen de echte Paper 1.21.4-API kan (scratchpad: paper-api-broncode via git + jars van Maven
  Central); de GitHub Actions-build blijft de officiele controle.

- **Dev mode** (`/kwa dev [on|off]`, permissie `kwa.dev`, default op): per speler, alleen in het geheugen; vervalt
  als `kwa.dev` weg is, `off` mag altijd. Mob-namen worden ge-escaped (MiniMessage-injectie via naamkaartjes). Opties onder
  `dev:` in wands.yml: `no-cooldown`, `hit-self`, `hit-mobs`, `ignore-kingdom` (ook creative; standaard **false**
  sinds config-version 4), `debug` (chat). `/kwa check <speler>`: kingdom, relatie (ook openstaand verzoek),
  gamemode en of de speler geraakt wordt. Bondgenoot-check ook op kingdom-id via `getRelations` (terugval).
  Spells nemen dan `LivingEntity`-doelen (mobs via `World#getNearbyLivingEntities`, geen armor stands).

### Open vragen voor de gebruiker
- Pluginnaam van KingdomCraft Premium op de echte server (`/plugins`); staat er niet `KingdomCraft`, dan die naam aan `softdepend` toevoegen.
- Of ProtocolLib geinstalleerd mag worden.
- Eigen resourcepack voor geluid/model, of eerst vanilla-geluiden.
