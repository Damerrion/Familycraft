package nl.fenix.core.web;

import nl.fenix.core.FenixCore;
import nl.fenix.core.family.FamilyService;
import nl.fenix.core.family.model.ArchivedFamily;
import nl.fenix.core.family.model.Family;
import nl.fenix.core.family.model.FamilyManager;
import nl.fenix.core.family.model.FamilyRank;
import nl.fenix.core.family.model.Genealogy;
import nl.fenix.core.wands.WandsModule;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static nl.fenix.core.web.AdminWeb.enc;
import static nl.fenix.core.web.AdminWeb.esc;

/** De HTML van de admin-website. Alleen op de main thread gebruiken. */
final class WebPages {

    /** Admin- en dev-commando's: commando, permissie, uitleg (lege permissie = kopje). */
    private static final String[][] COMMANDS = {
            {"Family - admin mode", "", ""},
            {"/family force [on|off]", "family.admin", "Admin mode aan/uit: elke familie bewerken (alleen in het geheugen)"},
            {"/family archive [list|restore <id>|delete <id>]", "family.admin", "Archief: opgeheven/opgegane/gefuseerde families terugzetten"},
            {"/family crest ban | unban", "family.admin", "Banner in je hand verbieden (ook in andere kleuren) of weer toestaan"},
            {"/family crest reset <tag>", "family.admin", "Familiewapen terug naar de standaard"},
            {"/family admin getcrest [tag]", "family.admin", "Kopie van het wapen van een familie (eigen familie kan altijd)"},
            {"/family editor <tag>", "family.admin", "Editor van elke familie: rangen, stamboom, wapen, leden"},
            {"/family rank give <speler> <rang>", "family.admin", "Rang geven in elke familie (Familiehoofd = hoofd overdragen)"},
            {"/family disband <tag>", "family.admin", "Familie opheffen (komt in het archief)"},
            {"/family kick <tag> <speler>", "family.admin", "Iemand uit een familie zetten"},
            {"/family deceased <lid> [karakter]", "family.admin", "Roleplay-karakter overleden verklaren (ook revive/remove)"},
            {"/family marriage force <bruidegom> <bruid>", "family.admin", "Direct trouwen als aftakking, zonder verloving"},
            {"/divorce force <speler>", "family.admin", "Scheiden zonder akkoord van de partner"},
            {"Family - dev mode (testen)", "", ""},
            {"/family dev [on|off]", "family.dev", "Dev mode: alles mogen wat een hoofd/admin mag"},
            {"/family dev fake <naam> [tag]", "family.dev", "Nep-lid in je familie (of in de familie met die tag)"},
            {"/family dev fakefamily <hoofd> <naam> <tag>", "family.dev", "Nep-familie met nep-hoofd"},
            {"/family dev enlope <a> <b>", "family.dev", "Direct verloven en het trouwmenu openen"},
            {"/family dev list | clear", "family.dev", "Nep-spelers bekijken / alles opruimen"},
            {"Wands", "", ""},
            {"/kwa get [wand] [speler]", "kwa.getwand + kwa.wand.<wand>", "Wand-menu of een wand geven"},
            {"/kwa create <wand> [naam]", "kwa.create", "Wand maken van het item in je hand"},
            {"/kwa check <speler>", "kwa.admin / kwa.dev", "Kingdom, relatie en of de speler geraakt wordt"},
            {"/kwa dev [on|off]", "kwa.dev", "Dev mode voor spells (geen cooldown, opties in wands.yml)"},
            {"/kwa reload", "kwa.admin", "wands.yml herladen"},
            {"FenixCore", "", ""},
            {"/fenixcore reload [all|family|kwa]", "fenixcore.reload", "Configs herladen"},
            {"/fenixcore web [newtoken]", "fenixcore.web", "Link naar deze website / nieuwe geheime link maken"},
    };

    /** Rechten die in de tabel met online staff staan. */
    private static final String[] WATCHED = {"family.admin", "family.dev", "kwa.admin", "kwa.dev", "fenixcore.reload",
            "fenixcore.web"};

    private final FenixCore core;
    private final FamilyService service;
    private final List<AdminWeb.LogEntry> log;

    WebPages(FenixCore core, FamilyService service, List<AdminWeb.LogEntry> log) {
        this.core = core;
        this.service = service;
        this.log = log;
    }

    // ------------------------------------------------------------------ overzicht

    String overview(AdminWeb.Ctx ctx) {
        StringBuilder b = new StringBuilder();
        WandsModule wands = core.wands();
        header(b, ctx, null);

        // online staff
        b.append("<section id=\"staff\"><h2>Online staff</h2>");
        List<Player> staff = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean any = p.isOp();
            for (String perm : WATCHED) {
                any |= p.hasPermission(perm);
            }
            if (any || adminMode(p) || familyDev(p) || (wands != null && wands.isDev(p))) {
                staff.add(p);
            }
        }
        if (staff.isEmpty()) {
            b.append("<p class=\"muted\">Niemand online met admin- of dev-rechten.</p>");
        } else {
            b.append("<div class=\"scroll\"><table><tr><th>Speler</th><th>op</th>");
            for (String perm : WATCHED) {
                b.append("<th>").append(esc(perm)).append("</th>");
            }
            b.append("<th>Admin mode</th><th>Family dev</th><th>Kwa dev</th><th></th></tr>");
            staff.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
            for (Player p : staff) {
                b.append("<tr><td>").append(head(p.getUniqueId())).append(esc(p.getName())).append("</td>").append(check(p.isOp()));
                for (String perm : WATCHED) {
                    b.append(check(p.hasPermission(perm)));
                }
                b.append(mode(adminMode(p))).append(mode(familyDev(p))).append(mode(wands != null && wands.isDev(p))).append("<td>");
                if (adminMode(p) || familyDev(p)) {
                    b.append(form(ctx, "staff.modeoff", "home", hidden("player", p.getUniqueId().toString()),
                            "Admin/dev mode uit", "small"));
                }
                b.append("</td></tr>");
            }
            b.append("</table></div>");
        }
        b.append("<p class=\"muted\">Rechten van offline spelers staan in LuckPerms (<code>/lp user &lt;naam&gt; permission info</code>).</p></section>");

        if (service != null) {
            families(b, ctx);
            deceased(b, ctx);
            archive(b, ctx);
            banned(b, ctx);
        } else {
            b.append("<section><h2>Families</h2><p class=\"muted\">De family-module staat uit.</p></section>");
        }
        logbook(b);
        commands(b);
        permissions(b);
        return page("FenixCore admin", b.toString());
    }

    private void families(StringBuilder b, AdminWeb.Ctx ctx) {
        FamilyManager manager = service.manager();
        List<Family> families = new ArrayList<>(manager.getFamilies());
        families.sort(Comparator.comparing(f -> f.getName().toLowerCase()));
        b.append("<section id=\"families\"><h2>Families <span class=\"count\">").append(families.size()).append("</span></h2>");
        if (families.isEmpty()) {
            b.append("<p class=\"muted\">Nog geen families.</p>");
        } else {
            b.append("<div class=\"scroll\"><table><tr><th>Tag</th><th>Naam</th><th>Hoofd</th><th>Leden</th>"
                    + "<th>Overleden</th><th>Afstamming</th><th>Opgericht</th><th></th></tr>");
            for (Family f : families) {
                int dead = f.getMembers().size() - manager.livingCount(f);
                b.append("<tr><td><b>").append(esc(f.getTag())).append("</b></td><td>").append(esc(f.getName()))
                        .append(f.needsRename() ? " <span class=\"tag\">moet hernoemen</span>" : "")
                        .append("</td><td>").append(head(f.getOwner())).append(esc(f.getOwnerName())).append("</td><td>")
                        .append(manager.livingCount(f)).append("</td><td>").append(dead == 0 ? "" : "✝ " + dead)
                        .append("</td><td>").append(esc(f.getKingdomName() == null ? "-" : f.getKingdomName()))
                        .append("</td><td>").append(esc(date(f.getCreatedAt()))).append("</td><td>")
                        .append("<a class=\"button\" href=\"").append(familyUrl(ctx, f.getTag())).append("\">Bewerken</a></td></tr>");
            }
            b.append("</table></div>");
        }
        b.append("</section>");
    }

    private void deceased(StringBuilder b, AdminWeb.Ctx ctx) {
        FamilyManager manager = service.manager();
        b.append("<section id=\"deceased\"><h2>Overleden karakters <span class=\"count\">")
                .append(manager.getAllDeceased().size()).append("</span></h2>");
        if (manager.getAllDeceased().isEmpty()) {
            b.append("<p class=\"muted\">Nog niemand overleden.</p></section>");
            return;
        }
        b.append("<div class=\"scroll\"><table><tr><th>Karakter</th><th>Gespeeld door</th><th>Familie</th><th>Was</th><th>Overleden</th></tr>");
        for (Map.Entry<UUID, FamilyManager.Deceased> e : manager.getAllDeceased().entrySet()) {
            FamilyManager.Deceased d = e.getValue();
            Family f = manager.getFamily(e.getKey());
            b.append("<tr><td>✝ ").append(esc(d.getName())).append("</td><td>").append(head(d.getPlayer()))
                    .append(esc(manager.getGenealogy().name(d.getPlayer()))).append("</td><td>")
                    .append(f == null ? "(archief)" : "<a href=\"" + familyUrl(ctx, f.getTag()) + "\">" + esc(f.getName()) + "</a>")
                    .append("</td><td>").append(esc(service.rankName(d.getFormerRank()))).append("</td><td>")
                    .append(esc(date(d.getDiedAt()))).append("</td></tr>");
        }
        b.append("</table></div><p class=\"muted\">Terugzetten of weghalen kan op de pagina van de familie.</p></section>");
    }

    private void archive(StringBuilder b, AdminWeb.Ctx ctx) {
        List<ArchivedFamily> archive = service.manager().getArchive();
        b.append("<section id=\"archive\"><h2>Archief <span class=\"count\">").append(archive.size()).append("</span></h2>");
        if (archive.isEmpty()) {
            b.append("<p class=\"muted\">Het archief is leeg.</p></section>");
            return;
        }
        b.append("<div class=\"scroll\"><table><tr><th>Naam</th><th>Reden</th><th>Leden</th><th>Gearchiveerd</th><th></th></tr>");
        for (ArchivedFamily a : archive) {
            List<String> names = new ArrayList<>(a.getMembers().values());
            b.append("<tr><td><b>").append(esc(a.getName())).append("</b> [").append(esc(a.getTag()))
                    .append("]<div class=\"muted\">").append(esc(String.join(", ", names.subList(0, Math.min(8, names.size())))))
                    .append(names.size() > 8 ? ", ..." : "").append("</div></td><td>")
                    .append(esc(service.archiveReason(a.getReason()))).append("</td><td>").append(a.getMembers().size())
                    .append("</td><td>").append(esc(date(a.getArchivedAt()))).append("</td><td class=\"actions\">")
                    .append(form(ctx, "archive.restore", "home", hidden("id", a.getId()), "Terugzetten", ""))
                    .append(form(ctx, "archive.delete", "home", hidden("id", a.getId()) + confirm(), "Verwijderen", "danger"))
                    .append("</td></tr>");
        }
        b.append("</table></div></section>");
    }

    private void banned(StringBuilder b, AdminWeb.Ctx ctx) {
        b.append("<section id=\"banned\"><h2>Verboden namen</h2><p class=\"muted\">Woorden die niet in een familienaam of "
                + "tag mogen (ook midden in de naam; hoofdletters, spaties, _ en - tellen niet).</p><div class=\"chips\">");
        for (String word : service.config().getStringList("banned.names")) {
            b.append("<span class=\"chip\">").append(esc(word))
                    .append(form(ctx, "banned.remove", "home", hidden("word", word), "✕", "chipx")).append("</span>");
        }
        b.append("</div>").append(form(ctx, "banned.add", "home",
                "<input name=\"word\" placeholder=\"nieuw woord\" maxlength=\"30\" required>", "Toevoegen", ""));

        List<String> crests = service.crestBans().list();
        b.append("<h2 class=\"sub\">Verboden wapens <span class=\"count\">").append(crests.size()).append("</span></h2>")
                .append("<p class=\"muted\">Toevoegen in-game: banner vasthouden en <code>/family crest ban</code> (admin mode).</p>");
        if (!crests.isEmpty()) {
            b.append("<div class=\"scroll\"><table><tr><th>#</th><th>Patronen (op volgorde)</th><th></th></tr>");
            int n = 1;
            for (String fp : crests) {
                b.append("<tr><td>").append(n++).append("</td><td><code>").append(esc(describeCrest(fp)))
                        .append("</code></td><td>").append(form(ctx, "crestban.remove", "home", hidden("fingerprint", fp),
                                "Weer toestaan", "small")).append("</td></tr>");
            }
            b.append("</table></div>");
        }
        b.append("</section>");
    }

    /** "0|1/cross|0/border" -> "cross (kleur 2), border (kleur 1)" */
    private static String describeCrest(String fingerprint) {
        String[] parts = fingerprint.split("\\|");
        List<String> layers = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            String[] cp = parts[i].split("/", 2);
            layers.add((cp.length > 1 ? cp[1] : parts[i]) + " (kleur " + (parseInt(cp[0]) + 1) + ")");
        }
        return layers.isEmpty() ? fingerprint : String.join(", ", layers);
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void logbook(StringBuilder b) {
        b.append("<section id=\"log\"><h2>Logboek</h2>");
        if (log.isEmpty()) {
            b.append("<p class=\"muted\">Nog niets aangepast via de website (sinds de laatste herstart).</p></section>");
            return;
        }
        b.append("<div class=\"scroll\"><table><tr><th>Tijd</th><th>Wie</th><th>Wat</th></tr>");
        for (AdminWeb.LogEntry e : log) {
            b.append("<tr").append(e.ok() ? "" : " class=\"fail\"").append("><td>").append(esc(date(e.time())))
                    .append("</td><td>").append(esc(e.who())).append("</td><td>").append(esc(e.text())).append("</td></tr>");
        }
        b.append("</table></div><p class=\"muted\">Alles staat ook in de console ([Web-admin]).</p></section>");
    }

    private static void commands(StringBuilder b) {
        b.append("<section id=\"commands\"><h2>Admin- en dev-commando's</h2><div class=\"scroll\"><table><tr><th>Commando</th><th>Permissie</th><th>Wat</th></tr>");
        for (String[] row : COMMANDS) {
            if (row[1].isEmpty()) {
                b.append("<tr class=\"group\"><td colspan=\"3\">").append(esc(row[0])).append("</td></tr>");
            } else {
                b.append("<tr><td><code>").append(esc(row[0])).append("</code></td><td><code>").append(esc(row[1]))
                        .append("</code></td><td>").append(esc(row[2])).append("</td></tr>");
            }
        }
        b.append("</table></div><p class=\"muted\">Family-admincommando's werken alleen met admin mode aan "
                + "(<code>/family force</code>) of dev mode; zonder is ook een op een gewone speler.</p></section>");
    }

    private void permissions(StringBuilder b) {
        List<Permission> perms = new ArrayList<>(core.getPluginMeta().getPermissions());
        perms.sort(Comparator.comparing(Permission::getName));
        b.append("<section id=\"perms\"><h2>Permissies</h2><div class=\"scroll\"><table><tr><th>Permissie</th><th>Standaard</th><th>Wat</th></tr>");
        for (Permission perm : perms) {
            boolean staffPerm = isStaffPermission(perm);
            b.append("<tr").append(staffPerm ? " class=\"staff\"" : "").append("><td><code>").append(esc(perm.getName()))
                    .append("</code>").append(staffPerm ? " <span class=\"tag\">admin/dev</span>" : "")
                    .append("</td><td>").append(esc(defaultName(perm.getDefault()))).append("</td><td>")
                    .append(esc(perm.getDescription())).append("</td></tr>");
        }
        b.append("</table></div><p class=\"muted\">Per wand komen er automatisch <code>kwa.use.&lt;wand&gt;</code> en "
                + "<code>kwa.wand.&lt;wand&gt;</code> bij.</p></section>");
    }

    // ------------------------------------------------------------------ familie bewerken

    String family(AdminWeb.Ctx ctx, String tag) {
        StringBuilder b = new StringBuilder();
        if (service == null) {
            return message("Family-module uit", "De family-module staat uit.");
        }
        FamilyManager manager = service.manager();
        Family f = tag == null ? null : manager.getFamilyByTag(tag);
        header(b, ctx, f);
        if (f == null) {
            b.append("<section><p>Deze familie bestaat niet (meer).</p></section>");
            return page("Familie", b.toString());
        }
        String back = "family:" + f.getTag();
        Genealogy tree = manager.getGenealogy();

        // familie
        b.append("<section><h2>⚜ ").append(esc(f.getName())).append(" <span class=\"count\">").append(esc(f.getTag()))
                .append("</span></h2><p class=\"muted\">Hoofd: ").append(esc(f.getOwnerName())).append(" · ")
                .append(manager.livingCount(f)).append(" leden · afstamming ")
                .append(esc(f.getKingdomName() == null ? "-" : f.getKingdomName())).append(" · opgericht ")
                .append(esc(date(f.getCreatedAt()))).append("</p>");
        if (f.isBranch()) {
            b.append("<p class=\"muted\">Afkomst: ").append(esc(String.join(" → ", f.getLineage()))).append("</p>");
        }
        b.append("<div class=\"grid\">")
                .append(card("Hernoemen", form(ctx, "family.rename", back, hidden("tag", f.getTag())
                        + "<input name=\"name\" value=\"" + esc(f.getName()) + "\" maxlength=\"40\" required>"
                        + "<input name=\"newtag\" value=\"" + esc(f.getTag()) + "\" maxlength=\"6\" class=\"short\" required>",
                        "Opslaan", "")))
                .append(card("Afstamming (kingdom)", form(ctx, "family.kingdom", back, hidden("tag", f.getTag())
                        + "<input name=\"kingdom\" value=\"" + esc(f.getKingdomName() == null ? "" : f.getKingdomName())
                        + "\" maxlength=\"40\" placeholder=\"leeg = geen\">", "Opslaan", "")))
                .append(card("Familiewapen", form(ctx, "family.crestreset", back, hidden("tag", f.getTag()),
                        "Terug naar standaard", "")))
                .append(card("Opheffen", "<p class=\"muted\">Komt in het archief (terug te zetten).</p>"
                        + form(ctx, "family.disband", back, hidden("tag", f.getTag()) + confirm(), "Familie opheffen", "danger")))
                .append("</div></section>");

        // leden
        List<UUID> members = new ArrayList<>(f.getMembers().keySet());
        members.sort(Comparator.comparing((UUID u) -> manager.isDeceased(u) ? 99 : f.getRank(u).ordinal())
                .thenComparing(u -> service.nameOf(u).toLowerCase()));
        b.append("<section><h2>Leden</h2><div class=\"scroll\"><table><tr><th>Lid</th><th>Rang</th><th>Relaties</th><th>Karakter overleden</th><th></th></tr>");
        for (UUID m : members) {
            String hidden = hidden("tag", f.getTag()) + hidden("member", m.toString());
            FamilyManager.Deceased dead = manager.getDeceased(m);
            if (dead != null) {
                b.append("<tr class=\"dead\"><td>✝ ").append(esc(service.nameOf(m))).append("<div class=\"muted\">gespeeld door ")
                        .append(esc(tree.name(dead.getPlayer()))).append(", ").append(esc(date(dead.getDiedAt())))
                        .append("</div></td><td>was ").append(esc(service.rankName(dead.getFormerRank()))).append("</td><td>")
                        .append(esc(relations(tree, m))).append("</td><td class=\"actions\">")
                        .append(form(ctx, "deceased.revive", back, hidden, "Terug tot leven", ""))
                        .append("</td><td>").append(form(ctx, "deceased.remove", back, hidden + confirm(), "Weghalen", "danger"))
                        .append("</td></tr>");
                continue;
            }
            b.append("<tr><td>").append(head(m)).append(esc(service.nameOf(m))).append("</td><td>")
                    .append(form(ctx, "member.rank", back, hidden + rankSelect(f.getRank(m)), "Opslaan", "small"))
                    .append("</td><td>").append(esc(relations(tree, m))).append("</td><td>")
                    .append(form(ctx, "member.deceased", back, hidden
                            + "<input name=\"character\" placeholder=\"karakternaam\" maxlength=\"40\" class=\"mid\">",
                            "✝ Overleden", "small"))
                    .append("</td><td>");
            if (!f.isOwner(m)) {
                b.append(form(ctx, "member.kick", back, hidden + confirm(), "Kicken", "danger small"));
            }
            b.append("</td></tr>");
        }
        b.append("</table></div><p class=\"muted\">Familiehoofd geven = het hoofd overdragen. Overlijdt het hoofd, dan neemt "
                + "de Opvolger (of Reserve-erfgenaam) het over.</p></section>");

        // stamboom
        b.append("<section><h2>Stamboom</h2>");
        List<String[]> pairs = relationPairs(tree, f, members);
        if (pairs.isEmpty()) {
            b.append("<p class=\"muted\">Nog geen relaties.</p>");
        } else {
            b.append("<div class=\"scroll\"><table>");
            for (String[] pair : pairs) {
                b.append("<tr><td>").append(esc(pair[2])).append("</td><td>")
                        .append(form(ctx, "tree.unlink", back, hidden("tag", f.getTag()) + hidden("a", pair[0])
                                + hidden("b", pair[1]), "Ontkoppelen", "small")).append("</td></tr>");
            }
            b.append("</table></div>");
        }
        StringBuilder options = new StringBuilder();
        for (UUID m : members) {
            options.append("<option value=\"").append(m).append("\">").append(manager.isDeceased(m) ? "✝ " : "")
                    .append(esc(service.nameOf(m))).append("</option>");
        }
        b.append("<h2 class=\"sub\">Koppelen</h2>").append(form(ctx, "tree.link", back, hidden("tag", f.getTag())
                + "<select name=\"a\">" + options + "</select><select name=\"relation\">"
                + "<option value=\"parent\">is ouder van</option><option value=\"partner\">is partner van</option>"
                + "<option value=\"sibling\">is broer/zus van</option></select><select name=\"b\">" + options + "</select>",
                "Koppelen", "")).append("</section>");
        return page(f.getName(), b.toString());
    }

    private String rankSelect(FamilyRank current) {
        StringBuilder s = new StringBuilder("<select name=\"rank\">");
        for (FamilyRank rank : FamilyRank.values()) {
            s.append("<option value=\"").append(rank.id()).append('"').append(rank == current ? " selected" : "").append('>')
                    .append(esc(service.rankName(rank))).append(rank == FamilyRank.HEAD && rank != current ? " (overdragen)" : "")
                    .append("</option>");
        }
        return s.append("</select>").toString();
    }

    private String relations(Genealogy tree, UUID m) {
        List<String> parts = new ArrayList<>();
        UUID partner = tree.partnerOf(m);
        if (partner != null) {
            parts.add("❤ " + name(partner));
        }
        if (!tree.parentsOf(m).isEmpty()) {
            parts.add("ouders: " + names(tree.parentsOf(m)));
        }
        if (!tree.childrenOf(m).isEmpty()) {
            parts.add("kinderen: " + names(tree.childrenOf(m)));
        }
        if (!tree.siblingsOf(m).isEmpty()) {
            parts.add("broers/zussen: " + names(tree.siblingsOf(m)));
        }
        return parts.isEmpty() ? "-" : String.join(" · ", parts);
    }

    /** Elke relatie één keer: [a, b, tekst]. */
    private List<String[]> relationPairs(Genealogy tree, Family f, List<UUID> members) {
        List<String[]> pairs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (UUID m : members) {
            for (UUID parent : tree.parentsOf(m)) {
                pairs.add(new String[]{parent.toString(), m.toString(), name(parent) + " is ouder van " + name(m)});
            }
            UUID partner = tree.partnerOf(m);
            if (partner != null && seen.add(key(m, partner))) {
                pairs.add(new String[]{m.toString(), partner.toString(), name(m) + " ❤ " + name(partner)});
            }
            for (UUID sibling : tree.siblingsOf(m)) {
                if (seen.add(key(m, sibling))) {
                    pairs.add(new String[]{m.toString(), sibling.toString(), name(m) + " en " + name(sibling) + " zijn broer/zus"});
                }
            }
        }
        return pairs;
    }

    private static String key(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }

    private String name(UUID uuid) {
        return (service.manager().isDeceased(uuid) ? "✝ " : "") + service.nameOf(uuid);
    }

    private String names(Set<UUID> uuids) {
        List<String> list = new ArrayList<>();
        uuids.forEach(u -> list.add(name(u)));
        return String.join(", ", list);
    }

    // ------------------------------------------------------------------ bouwstenen

    private void header(StringBuilder b, AdminWeb.Ctx ctx, Family family) {
        b.append("<header><h1>⚜ FenixCore <span>admin</span></h1><p class=\"muted\">")
                .append(esc(Bukkit.getServer().getName())).append(' ').append(esc(Bukkit.getMinecraftVersion()))
                .append(" · FenixCore ").append(esc(core.getPluginMeta().getVersion()))
                .append(" · ").append(Bukkit.getOnlinePlayers().size()).append(" online · ingelogd als ")
                .append(esc(ctx.as())).append(" · ").append(esc(date(Instant.now()))).append("</p><p>")
                .append(badge("Wands", core.wands() != null)).append(badge("Family", service != null)).append("</p><nav>")
                .append("<a href=\"").append(homeUrl(ctx)).append("\">Overzicht</a>")
                .append("<a href=\"").append(homeUrl(ctx)).append("#families\">Families</a>")
                .append("<a href=\"").append(homeUrl(ctx)).append("#archive\">Archief</a>")
                .append("<a href=\"").append(homeUrl(ctx)).append("#banned\">Verboden</a>")
                .append("<a href=\"").append(homeUrl(ctx)).append("#log\">Logboek</a>")
                .append("<a href=\"").append(homeUrl(ctx)).append("#commands\">Commando's</a>")
                .append("<a href=\"").append(homeUrl(ctx)).append("#perms\">Permissies</a>");
        if (family != null) {
            b.append("<a class=\"current\" href=\"").append(familyUrl(ctx, family.getTag())).append("\">")
                    .append(esc(family.getTag())).append("</a>");
        }
        b.append("</nav></header>");
        if (ctx.msg() != null && !ctx.msg().isEmpty()) {
            b.append("<div class=\"flash ").append(ctx.ok() ? "ok" : "err").append("\">").append(esc(ctx.msg())).append("</div>");
        }
    }

    private static String homeUrl(AdminWeb.Ctx ctx) {
        return "/?token=" + esc(enc(ctx.token())) + "&amp;as=" + esc(enc(ctx.as()));
    }

    private static String familyUrl(AdminWeb.Ctx ctx, String tag) {
        return "/family?tag=" + esc(enc(tag)) + "&amp;token=" + esc(enc(ctx.token())) + "&amp;as=" + esc(enc(ctx.as()));
    }

    /** POST-formulier met het token erin (geen cookies, dus geen CSRF). */
    private static String form(AdminWeb.Ctx ctx, String action, String back, String inner, String button, String cls) {
        return "<form method=\"post\" action=\"/action\">" + hidden("token", ctx.token()) + hidden("as", ctx.as())
                + hidden("action", action) + hidden("back", back) + inner + "<button class=\"" + esc(cls) + "\">"
                + esc(button) + "</button></form>";
    }

    private static String hidden(String name, String value) {
        return "<input type=\"hidden\" name=\"" + esc(name) + "\" value=\"" + esc(value) + "\">";
    }

    private static String confirm() {
        return "<label class=\"confirm\"><input type=\"checkbox\" name=\"confirm\" value=\"yes\" required> zeker</label>";
    }

    private static String card(String title, String body) {
        return "<div class=\"card\"><h3>" + esc(title) + "</h3>" + body + "</div>";
    }

    private boolean adminMode(Player p) {
        return service != null && service.isAdminMode(p);
    }

    private boolean familyDev(Player p) {
        return service != null && service.isDev(p);
    }

    private static boolean isStaffPermission(Permission perm) {
        String n = perm.getName();
        return n.endsWith(".admin") || n.endsWith(".dev") || n.startsWith("fenixcore.") || n.endsWith(".create")
                || n.endsWith(".*") || n.equals("kwa.getwand") || n.startsWith("kwa.wand.");
    }

    private static String defaultName(PermissionDefault def) {
        return switch (def) {
            case TRUE -> "iedereen";
            case FALSE -> "niemand";
            case OP -> "op";
            case NOT_OP -> "niet-op";
        };
    }

    private static String badge(String name, boolean on) {
        return "<span class=\"badge " + (on ? "on" : "off") + "\">" + name + (on ? " aan" : " uit") + "</span>";
    }

    private static String check(boolean yes) {
        return yes ? "<td class=\"yes\">✔</td>" : "<td class=\"no\">–</td>";
    }

    private static String mode(boolean on) {
        return on ? "<td class=\"mode\">AAN</td>" : "<td class=\"no\">–</td>";
    }

    private static String head(UUID uuid) {
        return uuid == null ? "" : "<img class=\"head\" alt=\"\" src=\"https://mc-heads.net/avatar/" + uuid + "/16\">";
    }

    private static String date(Instant instant) {
        return DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm").withZone(ZoneId.systemDefault()).format(instant);
    }

    /** Minecraft-kleurcodes weghalen (&amp;a, §a). */
    static String strip(String text) {
        return text == null ? "" : text.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "");
    }

    /** Eenvoudige pagina met alleen een melding (fout, geen toegang, ...). */
    static String message(String title, String text) {
        return page(title, "<section><h2>" + esc(title) + "</h2><p>" + esc(text) + "</p></section>");
    }

    static String page(String title, String body) {
        return "<!doctype html><html lang=\"nl\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<meta name=\"robots\" content=\"noindex\"><title>" + esc(title) + "</title><style>" + CSS
                + "</style></head><body><main>" + body + "</main></body></html>";
    }

    private static final String CSS = """
            :root{--bg:#15121a;--panel:#1f1a27;--line:#2f2838;--text:#ece6f2;--muted:#9a90a8;--gold:#e8b84a;--green:#5fd38a;--red:#e06a6a;--purple:#b98cf0}
            *{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:15px/1.5 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
            main{max-width:1150px;margin:0 auto;padding:24px 16px 48px}a{color:var(--gold)}
            header{border-bottom:2px solid var(--gold);margin-bottom:20px;padding-bottom:12px}
            h1{margin:0;color:var(--gold);font-size:28px;letter-spacing:.5px}h1 span{color:var(--text);font-weight:400}
            nav{display:flex;flex-wrap:wrap;gap:6px;margin-top:10px}nav a{text-decoration:none;color:var(--text);background:var(--panel);border:1px solid var(--line);border-radius:6px;padding:3px 10px;font-size:13px}nav a.current{border-color:var(--gold);color:var(--gold)}
            h2{color:var(--gold);font-size:19px;margin:0 0 12px}h2.sub{margin-top:20px;font-size:16px}h3{margin:0 0 8px;font-size:14px;color:var(--muted);font-weight:600}
            section{background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:16px;margin-bottom:20px}
            .muted{color:var(--muted);font-size:13px}.count{background:var(--line);color:var(--text);border-radius:999px;padding:1px 9px;font-size:13px;margin-left:6px}
            .badge{display:inline-block;border-radius:6px;padding:2px 10px;margin-right:8px;font-size:13px;font-weight:600}.on{background:#1f3a2a;color:var(--green)}.off{background:#3a1f22;color:var(--red)}
            .flash{border-radius:8px;padding:10px 14px;margin-bottom:16px;font-weight:600}.flash.ok{background:#1f3a2a;color:var(--green)}.flash.err{background:#3a1f22;color:var(--red)}
            .scroll{overflow-x:auto}table{width:100%;border-collapse:collapse;font-size:14px}th,td{text-align:left;padding:7px 10px;border-bottom:1px solid var(--line);vertical-align:middle}
            th{color:var(--muted);font-weight:600;font-size:12px;text-transform:uppercase;letter-spacing:.4px;white-space:nowrap}
            tr.group td{color:var(--purple);font-weight:700;padding-top:16px}tr.staff code{color:var(--gold)}tr.fail td{color:var(--red)}tr.dead td{color:var(--muted)}
            code{background:#2a2333;border-radius:4px;padding:1px 6px;font-size:13px}.tag{background:#3a2f15;color:var(--gold);border-radius:4px;padding:1px 6px;font-size:11px;margin-left:4px}
            .yes{color:var(--green);text-align:center}.no{color:var(--muted);text-align:center}.mode{color:#15121a;background:var(--gold);font-weight:700;text-align:center;border-radius:4px}
            .head{width:16px;height:16px;vertical-align:-3px;margin-right:6px;image-rendering:pixelated;border-radius:2px}
            form{display:inline-flex;flex-wrap:wrap;gap:6px;align-items:center;margin:2px 0}
            input,select{background:#120f17;color:var(--text);border:1px solid var(--line);border-radius:6px;padding:5px 8px;font:inherit;font-size:14px;max-width:100%}
            input.short{width:80px}input.mid{width:150px}
            button,a.button{background:var(--gold);color:#15121a;border:0;border-radius:6px;padding:5px 12px;font:inherit;font-size:13px;font-weight:700;cursor:pointer;text-decoration:none;display:inline-block}
            button.danger{background:var(--red);color:#fff}button.small{padding:3px 9px;font-size:12px}
            .confirm{font-size:12px;color:var(--muted);white-space:nowrap}.actions{white-space:nowrap}
            .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:12px;margin-top:12px}.card{border:1px solid var(--line);border-radius:8px;padding:12px}
            .chips{display:flex;flex-wrap:wrap;gap:6px;margin-bottom:10px}.chip{display:inline-flex;align-items:center;gap:4px;background:var(--line);border-radius:999px;padding:2px 4px 2px 12px}
            button.chipx{background:transparent;color:var(--muted);padding:0 6px}button.chipx:hover{color:var(--red)}
            """;
}
