# FenixCore

De FenixKingdom-addon voor KingdomCraft (Paper 1.21.4). Eén plugin met twee modules:

| Module | Voorheen | Commando's | Instellingen |
|---|---|---|---|
| **wands** | KingdomWandsAddon | `/kwa`, `/bind`, `/unbind` | `plugins/FenixCore/wands.yml` |
| **family** | FamilyCraft | `/family` (`/familie`, `/fam`) | `plugins/FenixCore/family.yml` (+ data in `families.yml`) |

Modules aan/uit in `plugins/FenixCore/config.yml` (herstart). Configs herladen zonder herstart:
`/fenixcore reload [all|family|kwa]` (permissie `fenixcore.reload`, standaard op; `/kwa reload` werkt ook nog). Commando's en permissies (`kwa.*`, `family.*`) zijn hetzelfde
gebleven, dus LuckPerms hoeft niet aangepast te worden.

## Overstappen van KingdomWandsAddon / FamilyCraft
1. Zet `FenixCore-x.y.z.jar` in `plugins/` en **verwijder** `KingdomWandsAddon` en `FamilyCraft` (jars).
2. Start de server. FenixCore neemt bij de eerste start automatisch over:
   `plugins/KingdomWandsAddon/config.yml` -> `wands.yml`, `plugins/FamilyCraft/config.yml` -> `family.yml`,
   `plugins/FamilyCraft/families.yml` -> `families.yml` (gekopieerd; de oude mappen blijven als backup staan).
3. Wands die al in het spel zijn blijven gewoon werken.

Staat een oude plugin nog in `plugins/`, dan start die module van FenixCore niet (melding in de console),
zodat wands en families niet dubbel afgehandeld worden.

## Bouwen
Nodig: Java 21 (JDK). Maven hoef je niet te installeren, de wrapper haalt hem zelf op.

Windows: `mvnw.cmd package` - Linux/macOS: `./mvnw package` -> `target/FenixCore-1.0.0.jar`.

Zonder iets te installeren: elke push bouwt de jar op GitHub (tab **Actions** -> workflow **Build** ->
onderaan **Artifacts** -> `FenixCore`).

KingdomCraft is nodig voor de family-module; de wands werken ook zonder (dan raken spells iedereen). Vault is optioneel.

# Wands-module

### Wands
Alle wands staan in `wands.yml` onder `wands:`. Een nieuwe wand = een nieuw blok daar (naam, lore, material,
kingdom, effect en welke spells), daarna `/kwa reload`. Alleen een nieuwe spell heeft een nieuwe build nodig.
Elke wand krijgt automatisch de permissies `kwa.wand.<wand>` (krijgen) en `kwa.use.<wand>` (gebruiken), standaard op.

Nu: `tygron` = **Tygron Deathwhistle** (Scream, Mark). Krijgen met `/kwa get tygron` (hoofdletters maken niet uit),
of kies hem uit het menu met `/kwa get`.

### Commando's (zoals KingdomWands Premium)
Alles staat standaard aan voor operators; niet-ops krijgen permissies via LuckPerms. `kwa.*` geeft alles.

| Commando | Permissie |
|---|---|
| `/kwa` of `/kwa info` | - |
| `/kwa <pagina>` of `/kwa help [pagina]` | - |
| `/kwa get` (menu met alle wands) | `kwa.getwand` + `kwa.wand.<wand>` |
| `/kwa get <wand> [speler]` (bv. `/kwa get tygron`) | `kwa.wand.<wand>` |
| `/kwa create <wand> [naam]` | `kwa.create` + `kwa.wand.<wand>` |
| `/bind all`, `/bind <slot> <spell>` (of `/kwa bind ...`) | `kwa.bind` of de wand mogen gebruiken, + `kwa.spells.<Spell>` |
| `/unbind <slot\|all>` (of `/kwa unbind ...`) | `kwa.unbind` of de wand mogen gebruiken |
| `/kwa spells [spell]` (ook `list`, `spell`, `spelllist`) | `kwa.spells` of een wand mogen gebruiken |
| `/kwa effect <effect>` | `kwa.effect` |
| `/kwa dev [on\|off]` | `kwa.dev` |
| `/kwa check <speler>` | `kwa.dev` of `kwa.admin` |
| `/kwa reload` | `kwa.admin` |

Wand gebruiken: `kwa.use.<wand>` (bv. `kwa.use.tygron` voor de Stamleider; `kwa.use` = alle wands) + `kwa.spells.<Spell>`.
Zonder die permissie is de wand een gewoon item: klikken doet niets.
`/bind` en `/unbind` worden afgevangen als je een wand van deze addon vasthoudt (of altijd, als geen andere plugin
zoals KingdomWands `/bind` heeft). Alle teksten staan onder `messages:` in `wands.yml`.

Besturing: rechtermuis = volgende spell, shift + rechtermuis = vorige, linkermuis (ook op een speler) = casten.
Elke spell heeft een eigen switch-toon bij het selecteren (`select-sound` per spell in `wands.yml`; ook resourcepack-geluiden).
Spells raken iedereen behalve het `kingdom` van de wand en de bondgenoten daarvan. De caster raakt zichzelf nooit
(behalve in dev mode).

### Dev mode (alleen testen)
`/kwa dev` zet dev mode aan of uit voor **jouw** spells (na een herstart, of als je `kwa.dev` kwijtraakt, staat het uit).
Met `ignore-kingdom: true` raken je spells dan ook spelers van je eigen kingdom, bondgenoten en creative (standaard uit).
`/kwa check <speler>` laat zien of een speler geraakt wordt en waarom (kingdom, relatie, openstaand ally-verzoek, dev mode).
In dev mode (elk onderdeel aan/uit te zetten onder `dev:` in `wands.yml`):
- geen cooldowns
- spells raken jezelf ook
- spells raken ook mobs (zo kun je Scream en Mark alleen testen)
- je eigen kingdom, bondgenoten en spelers in creative worden ook geraakt
- debug in de chat: wie er geraakt is

# Family-module

### Een familie bestaat uit

1. **Naam** - geen cijfers, max 30 tekens (`_` wordt een spatie: `Rheanos_Decendants`).
2. **Tag** - geen cijfers, max 3 tekens, de prefix voor de spelersnaam.

### Commando's (`/family`, aliassen `/familie`, `/fam`)

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

### Overzicht, editor, rangen en stamboom
| Commando | Wat |
|---|---|
| `/family list` | GUI met de families van **jouw kingdom** (familiewapen, tag, hoofd, leden, afstamming). Klik = leden en stamboom |
| `/family list <kingdom>` / `/family list all` | Families van een ander kingdom / van alle kingdoms (`/family list <tag>` opent die familie) |
| `/family editor [tag]` | Familiewapen (klik met een banner), info, leden met rang en relaties, stamboom. Alleen het hoofd (of `family.admin`) kan bewerken |
| `/family rank give <speler\|UUID> <rang>` | Rangen: Familiehoofd, Opvolger, Reserve-erfgenaam, Gemaal, Gemaal van de Erfgenaam, Kind van het Huis (`/family rank list`). Familiehoofd geven = het hoofd overdragen |
| `/family tree link <parent\|child\|partner\|sibling> <a> <b>` / `unlink <a> <b>` | Stamboom koppelen (ook via de editor) |
| `/family crest` | Banner in je hand wordt het familiewapen |

Nieuwe leden krijgen de rang Kind van het Huis. Hoeveel leden een rang mogen hebben staat onder `ranks` in
`family.yml` (standaard 1 Opvolger en 1 Reserve-erfgenaam). Een ouder/kind (of grootouder/kleinkind) kan niet ook
broer/zus zijn. In de stamboom is elke rij een generatie (gekleurde markering links), staan koppels naast elkaar
met een ❤ ertussen en kinderen onder hun ouders; naamkleur = rang. De stamboom hoort bij de speler, dus relaties blijven bestaan
als iemand naar een aftakking gaat.

### Verloving en huwelijk
1. **Verloven:** `/enlope <speler>`; de ander klikt **[Ja]** (of `/enlope accept|deny`). Verbreken: `/enlope cancel`.
2. **Trouwmenu** (`/family marriage`, of `/enlope` zonder speler): het paar wisselt wie **bruidegom** en wie **bruid**
   is, en wie de **keuze** (A) heeft. A kiest daar ook hoe de lijnen verder gaan.
3. Het **hoofd van de familie van de bruid** opent het menu (klikbare melding of `/family marriage <bruid>`) en klikt
   **Start de trouwerij**.

Wat er gebeurt:
| Bruidspaar | Uitkomst |
|---|---|
| Hoofd + hoofd | De families **fuseren**: `Dokker Lijn` + `Adamantie Lijn` -> **`Dokker-Adamantie Lijn` [DAL]**. Bruidegom blijft hoofd, bruid wordt Gemaal; Opvolger/Reserve-erfgenaam van de bruid worden Kind van het Huis |
| Hoofd + Opvolger | De **Opvolger gaat over** naar de familie van het hoofd (als Gemaal); de Reserve-erfgenaam wordt de nieuwe Opvolger |
| Zelfde familie | Ze worden partners, verder verandert er niets |
| Anders: A kiest | **Lijn A gaat op in lijn B** of andersom **lijn B in lijn A** · **alleen A gaat over naar lijn B** of andersom **alleen de partner naar lijn A** (beide lijnen gaan verder) · **samen een nieuwe lijn C** (alleen het bruidspaar, nieuwe naam kiezen; A en B gaan verder) · **aftakking** (`A-B`, alleen het paar). Een hele lijn meenemen kan alleen als het hoofd of de Opvolger van die lijn trouwt |

De aangetrouwde partner wordt Gemaal (bij een hoofd), Gemaal van de Erfgenaam (bij een Opvolger/Reserve-erfgenaam)
of Kind van het Huis. Gaat een hoofd weg, dan neemt de Opvolger (anders Reserve-erfgenaam) het over.
Komen er 4 families in de naam (`A-B-C-D`) of kies je een nieuwe lijn, dan krijgt het hoofd een popup om een nieuwe
naam en tag te kiezen (`/family rename <naam> <tag>`).

**Afkomst:** bij elke overgang wordt per speler bewaard uit welke lijn(en) hij of zij officieel stamt. Dat staat bij
elk lid in de editor en de stamboom (bv. `Afkomst: Dokker Lijn → Adamantie Lijn`), en bij een familie onder Afkomst.

Admins: `/family marriage force <bruidegom> <bruid>` (direct een aftakking, zonder verloving/akkoord).

### Dev mode (alleen testen)
`/family dev` (permissie `family.dev`, standaard op) zet dev mode aan/uit voor **jou** (na een herstart uit). In dev mode
mag je alles wat een familiehoofd/admin mag (editor van elke familie, rangen, stamboom, rollen en keuze in het
trouwmenu, trouwerij starten), en kun je alles alleen testen met nep-spelers:
| Commando | Wat |
|---|---|
| `/family dev fake <naam> [tag]` | Nep-lid in je eigen familie (of de familie met die tag) |
| `/family dev fakefamily <hoofd> <naam> <tag>` | Nep-familie met een nep-hoofd |
| `/family dev enlope <a> <b>` (of `fake enlope`) | Direct verloven (ook nep-spelers) en het trouwmenu openen. `/enlope <nep-speler>` in dev mode: de nep-speler zegt meteen ja |
| `/family dev list` / `clear` | Nep-spelers bekijken / alles opruimen (families met alleen nep-leden worden opgeheven) |

Nep-spelers worden bewaard in families.yml (`fakes:`) tot `/family dev clear`.

De **familie afstamming** (kingdom van het hoofd) wordt bij de oprichting vastgelegd en blijft staan als het hoofd
later van kingdom wisselt.

### Prefix in de chat / boven het hoofd

FenixCore registreert placeholders in KingdomCraft: `{family_tag}`, `{family_prefix}`, `{family_name}`.

- **Display name** (standaard aan, `display-name` in `family.yml`): KingdomCraft zet de display name zelf op
  `<kingdom prefix> <naam>`. FenixCore overschrijft die met
  `{kingdom_prefix} {family_prefix} %player%{kingdom_suffix}`, dus de familietag staat direct na het koninkrijk.
  Chatplugins die de display name gebruiken (EssentialsChat e.d.) tonen dan `[Gast] [RD] doomerix`.
- **KingdomCraft chat** (`plugins/KingdomCraft/chat.yml`): zet `{family_prefix}` direct voor de naam, zonder
  eigen haakjes of spatie erachter: `{family_prefix}` is `[TW] ` (haakjes + spatie) en helemaal leeg zonder familie.
  `format: '&7[&aP&7]&f{kingdom_prefix}{rank_prefix} {family_prefix}&f{prefix}{username}&7: &f{message}'`
  Gebruik dus niet `[{family_tag}]`: dan blijft `[]` staan voor spelers zonder familie.
- **Premium nametags/tablist** (`plugins/KingdomCraftPremium/config.yml`):
  `prefix_format: '{kingdom_nametag_prefix}{family_prefix}{rank_nametag_prefix}'`

#### Hover / klik op de prefix

In de KingdomCraft chat laat `{family_prefix}` bij hover de familie-info zien (naam, afkorting, afstamming,
datum opgericht, eigenaar, leden) en voert een klik `/family members <tag>` uit. Instellen of uitzetten via
`chat-hover` in `family.yml`. Werkt alleen met `{family_prefix}` (niet met `{family_tag}`).

Na het wijzigen van `chat.yml` / Premium config: `/k reload` (of herstart).
Alle teksten en het prefixformaat staan in `family.yml`. Families worden opgeslagen in `plugins/FenixCore/families.yml`.
