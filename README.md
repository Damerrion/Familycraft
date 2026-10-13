# FamilyCraft

Family-systeem als addon voor [KingdomCraft](https://kingdomcraft.be). Draait als aparte plugin die via de
publieke KingdomCraft API (`KingdomCraftProvider`) aanhaakt: de KingdomCraft jars worden niet aangepast of meegeleverd.

## Bouwen

1. Zet `KingdomCraftStarter-7.1.6-all.jar` in `libs/` (staat in `.gitignore`).
2. `mvn package` -> `target/FamilyCraft-1.0.0.jar` in `plugins/` naast KingdomCraft (Vault is optioneel).

## Een familie bestaat uit

1. **Naam** - geen cijfers, max 30 tekens (`_` wordt een spatie: `Rheanos_Decendants`).
2. **Tag** - geen cijfers, max 3 tekens, de prefix voor de spelersnaam.

## Commando's (`/family`, aliassen `/familie`, `/fam`)

| Commando | Wie | Wat |
|---|---|---|
| `create <naam> <tag>` | permissie `family.create` | maak een familie (eenmalige kosten via Vault, `economy.create-cost`) |
| `invite <speler>` | eigenaar | nodig uit (verloopt na 60s) |
| `accept` / `cancel` | genodigde | uitnodiging accepteren / weigeren (`cancel` annuleert ook een `disband` of je openstaande uitnodigingen) |
| `kick <speler>` | eigenaar | zet een lid uit de familie |
| `leave` | lid | verlaat de familie |
| `disband` | eigenaar | verwijder de familie (2x uitvoeren ter bevestiging) |
| `check <tag>` | iedereen | naam, afkorting, afstamming (koninkrijk van de eigenaar), datum opgericht |
| `members [tag]` | iedereen | ledenlijst |

Permissies: `family.use` (standaard iedereen), `family.create` (de gekochte rechten), `family.admin`
(gratis aanmaken, `disband <tag>` / `kick <tag> <speler>` voor elke familie).

## Prefix in de chat / boven het hoofd

FamilyCraft registreert placeholders in KingdomCraft: `{family_tag}`, `{family_prefix}`, `{family_name}`.

- **Display name** (standaard aan, `display-name` in `config.yml`): KingdomCraft zet de display name zelf op
  `<kingdom prefix> <naam>`. FamilyCraft overschrijft die met
  `{kingdom_prefix} {family_prefix} %player%{kingdom_suffix}`, dus de familietag staat direct na het koninkrijk.
  Chatplugins die de display name gebruiken (EssentialsChat e.d.) tonen dan `[Gast] [RD] doomerix`.
- **KingdomCraft chat** (`plugins/KingdomCraft/chat.yml`): zet `{family_prefix}` direct voor de naam, zonder
  eigen haakjes of spatie erachter: `{family_prefix}` is `[TW] ` (haakjes + spatie) en helemaal leeg zonder familie.
  `format: '&7[&aP&7]&f{kingdom_prefix}{rank_prefix} {family_prefix}&f{prefix}{username}&7: &f{message}'`
  Gebruik dus niet `[{family_tag}]`: dan blijft `[]` staan voor spelers zonder familie.
- **Premium nametags/tablist** (`plugins/KingdomCraftPremium/config.yml`):
  `prefix_format: '{kingdom_nametag_prefix}{family_prefix}{rank_nametag_prefix}'`

### Hover / klik op de prefix

In de KingdomCraft chat laat `{family_prefix}` bij hover de familie-info zien (naam, afkorting, afstamming,
datum opgericht, eigenaar, leden) en voert een klik `/family members <tag>` uit. Instellen of uitzetten via
`chat-hover` in `config.yml`. Werkt alleen met `{family_prefix}` (niet met `{family_tag}`).

Na het wijzigen van `chat.yml` / Premium config: `/k reload` (of herstart).
Alle teksten en het prefixformaat staan in `config.yml`. Families worden opgeslagen in `plugins/FamilyCraft/families.yml`.
