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

## Prefix in de chat

FamilyCraft registreert placeholders in KingdomCraft: `{family_tag}`, `{family_prefix}`, `{family_name}`.
Zet ze in `plugins/KingdomCraft/chat.yml`, bv.:

```yaml
format: '&7[&aP&7]&f{kingdom_prefix}{rank_prefix} {family_prefix} &f{prefix}{username}&7: &f{message}'
```

Resultaat: `[Gast] [RD] doomerix: Gegroet!`. Alle teksten en het prefixformaat staan in `config.yml`.
Families worden opgeslagen in `plugins/FamilyCraft/families.yml`.
