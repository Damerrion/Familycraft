package nl.fenix.core.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import nl.fenix.core.FenixCore;
import nl.fenix.core.family.FamilyModule;
import nl.fenix.core.family.FamilyService;
import nl.fenix.core.family.model.ArchivedFamily;
import nl.fenix.core.family.model.Family;
import nl.fenix.core.family.model.FamilyManager;
import nl.fenix.core.wands.WandsModule;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Kleine admin-website in de plugin (alleen lezen): admin- en dev-commando's, alle permissies, wie online admin- of
 * dev-rechten / admin mode / dev mode heeft, families, overleden karakters en het archief.
 * Toegang alleen met het geheime token uit config.yml ({@code web.token}); de link krijg je met /fenixcore web.
 * De pagina wordt op de main thread opgebouwd (Bukkit is niet thread-safe) en alle tekst wordt ge-escaped
 * (familienamen komen van spelers).
 */
public final class AdminWeb {

    /** Admin- en dev-commando's: commando, permissie, uitleg. */
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

    /** Rechten die in de tabel met online spelers staan. */
    private static final String[] WATCHED = {"family.admin", "family.dev", "kwa.admin", "kwa.dev", "fenixcore.reload",
            "fenixcore.web"};

    private final FenixCore core;
    private HttpServer server;
    private ExecutorService executor;

    public AdminWeb(FenixCore core) {
        this.core = core;
    }

    // ------------------------------------------------------------------ starten / stoppen

    public synchronized void start() {
        stop();
        if (!core.getConfig().getBoolean("web.enabled", true)) {
            return;
        }
        if (token().isEmpty()) {
            newToken();
        }
        String bind = core.getConfig().getString("web.bind", "0.0.0.0");
        int port = core.getConfig().getInt("web.port", 8095);
        try {
            server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        } catch (IOException | RuntimeException e) {
            core.getLogger().warning("Admin-website kon niet starten op " + bind + ":" + port + " (" + e.getMessage()
                    + "). Kies een andere web.port in config.yml.");
            server = null;
            return;
        }
        executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "FenixCore-web");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
        core.getLogger().info("Admin-website aan op poort " + port + " (link: /fenixcore web).");
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public boolean isRunning() {
        return server != null;
    }

    public String token() {
        String token = core.getConfig().getString("web.token", "");
        return token == null ? "" : token.trim();
    }

    /** Nieuw geheim token (de oude link werkt dan niet meer). */
    public String newToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        core.getConfig().set("web.token", token);
        core.saveConfig();
        return token;
    }

    /** De link voor /fenixcore web. */
    public String link() {
        String host = core.getConfig().getString("web.public-address", "");
        if (host == null || host.isBlank()) {
            host = Bukkit.getIp() == null || Bukkit.getIp().isBlank() ? "<server-ip>" : Bukkit.getIp();
        }
        return "http://" + host + ":" + core.getConfig().getInt("web.port", 8095) + "/?token=" + token();
    }

    // ------------------------------------------------------------------ verzoeken

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                send(exchange, 405, page("Niet toegestaan", "<p>Alleen bekijken.</p>"));
                return;
            }
            if (!exchange.getRequestURI().getPath().equals("/")) {
                send(exchange, 404, page("Niet gevonden", "<p>Deze pagina bestaat niet.</p>"));
                return;
            }
            String given = query(exchange.getRequestURI().getRawQuery(), "token");
            String token = token();
            if (token.isEmpty() || given == null || !MessageDigest.isEqual(
                    given.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
                send(exchange, 403, page("Geen toegang", "<p>Gebruik de link van <code>/fenixcore web</code> in-game.</p>"));
                return;
            }
            String html = Bukkit.getScheduler().callSyncMethod(core, this::render).get(5, TimeUnit.SECONDS);
            send(exchange, 200, html);
        } catch (Exception e) {
            core.getLogger().log(Level.WARNING, "Admin-website: fout bij het opbouwen van de pagina", e);
            send(exchange, 500, page("Fout", "<p>Er ging iets mis; zie de console.</p>"));
        } finally {
            exchange.close();
        }
    }

    private static String query(String raw, String key) {
        if (raw == null) {
            return null;
        }
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(key)) {
                return URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void send(HttpExchange exchange, int status, String html) throws IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/html; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("X-Frame-Options", "DENY");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Content-Security-Policy",
                "default-src 'none'; style-src 'unsafe-inline'; img-src https://mc-heads.net; base-uri 'none'; form-action 'none'");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    // ------------------------------------------------------------------ pagina (main thread)

    private String render() {
        StringBuilder b = new StringBuilder();
        FamilyModule familyModule = core.family();
        FamilyService family = familyModule == null ? null : familyModule.service();
        WandsModule wands = core.wands();

        b.append("<header><h1>⚜ FenixCore <span>admin-overzicht</span></h1><p class=\"muted\">")
                .append(esc(Bukkit.getServer().getName())).append(' ').append(esc(Bukkit.getMinecraftVersion()))
                .append(" · FenixCore ").append(esc(core.getPluginMeta().getVersion()))
                .append(" · ").append(Bukkit.getOnlinePlayers().size()).append(" online · bijgewerkt ")
                .append(esc(date(Instant.now()))).append("</p><p>")
                .append(badge("Wands", wands != null)).append(badge("Family", family != null))
                .append("</p></header>");

        // wie is er nu online met admin/dev-rechten of een mode aan
        b.append("<section><h2>Online staff</h2>");
        List<Player> staff = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean any = p.isOp();
            for (String perm : WATCHED) {
                any |= p.hasPermission(perm);
            }
            if (any || (family != null && (family.isAdminMode(p) || family.isDev(p))) || (wands != null && wands.isDev(p))) {
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
            b.append("<th>Admin mode</th><th>Family dev</th><th>Kwa dev</th></tr>");
            staff.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
            for (Player p : staff) {
                b.append("<tr><td>").append(head(p.getUniqueId())).append(esc(p.getName())).append("</td>")
                        .append(check(p.isOp()));
                for (String perm : WATCHED) {
                    b.append(check(p.hasPermission(perm)));
                }
                b.append(mode(family != null && family.isAdminMode(p)))
                        .append(mode(family != null && family.isDev(p)))
                        .append(mode(wands != null && wands.isDev(p))).append("</tr>");
            }
            b.append("</table></div>");
        }
        b.append("<p class=\"muted\">Rechten van offline spelers staan in LuckPerms (<code>/lp user &lt;naam&gt; permission info</code>).</p></section>");

        // commando's
        b.append("<section><h2>Admin- en dev-commando's</h2><div class=\"scroll\"><table><tr><th>Commando</th><th>Permissie</th><th>Wat</th></tr>");
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

        // alle permissies uit plugin.yml
        List<Permission> perms = new ArrayList<>(core.getPluginMeta().getPermissions());
        perms.sort(Comparator.comparing(Permission::getName));
        b.append("<section><h2>Permissies</h2><div class=\"scroll\"><table><tr><th>Permissie</th><th>Standaard</th><th>Wat</th></tr>");
        for (Permission perm : perms) {
            boolean staffPerm = isStaffPermission(perm);
            b.append("<tr").append(staffPerm ? " class=\"staff\"" : "").append("><td><code>").append(esc(perm.getName()))
                    .append("</code>").append(staffPerm ? " <span class=\"tag\">admin/dev</span>" : "")
                    .append("</td><td>").append(esc(defaultName(perm.getDefault()))).append("</td><td>")
                    .append(esc(perm.getDescription())).append("</td></tr>");
        }
        b.append("</table></div><p class=\"muted\">Per wand komen er automatisch <code>kwa.use.&lt;wand&gt;</code> en "
                + "<code>kwa.wand.&lt;wand&gt;</code> bij.</p></section>");

        if (family != null) {
            renderFamilies(b, family);
        }
        return page("FenixCore admin", b.toString());
    }

    private void renderFamilies(StringBuilder b, FamilyService service) {
        FamilyManager manager = service.manager();
        List<Family> families = new ArrayList<>(manager.getFamilies());
        families.sort(Comparator.comparing(f -> f.getName().toLowerCase()));
        b.append("<section><h2>Families <span class=\"count\">").append(families.size()).append("</span></h2>");
        if (families.isEmpty()) {
            b.append("<p class=\"muted\">Nog geen families.</p>");
        } else {
            b.append("<div class=\"scroll\"><table><tr><th>Tag</th><th>Naam</th><th>Hoofd</th><th>Leden</th>"
                    + "<th>Overleden</th><th>Afstamming</th><th>Opgericht</th></tr>");
            for (Family f : families) {
                int dead = f.getMembers().size() - manager.livingCount(f);
                b.append("<tr><td><b>").append(esc(f.getTag())).append("</b></td><td>").append(esc(f.getName()))
                        .append(f.needsRename() ? " <span class=\"tag\">moet hernoemen</span>" : "")
                        .append("</td><td>").append(head(f.getOwner())).append(esc(f.getOwnerName())).append("</td><td>")
                        .append(manager.livingCount(f)).append("</td><td>").append(dead == 0 ? "" : "✝ " + dead)
                        .append("</td><td>").append(esc(f.getKingdomName() == null ? "-" : f.getKingdomName()))
                        .append("</td><td>").append(esc(date(f.getCreatedAt()))).append("</td></tr>");
            }
            b.append("</table></div>");
        }
        b.append("</section>");

        // overleden karakters
        b.append("<section><h2>Overleden karakters <span class=\"count\">").append(manager.getAllDeceased().size())
                .append("</span></h2>");
        if (manager.getAllDeceased().isEmpty()) {
            b.append("<p class=\"muted\">Nog niemand overleden.</p>");
        } else {
            b.append("<div class=\"scroll\"><table><tr><th>Karakter</th><th>Gespeeld door</th><th>Familie</th><th>Was</th><th>Overleden</th></tr>");
            for (Map.Entry<UUID, FamilyManager.Deceased> e : manager.getAllDeceased().entrySet()) {
                FamilyManager.Deceased d = e.getValue();
                Family f = manager.getFamily(e.getKey());
                b.append("<tr><td>✝ ").append(esc(d.getName())).append("</td><td>").append(head(d.getPlayer()))
                        .append(esc(manager.getGenealogy().name(d.getPlayer()))).append("</td><td>")
                        .append(esc(f == null ? "(archief)" : f.getName())).append("</td><td>")
                        .append(esc(service.rankName(d.getFormerRank()))).append("</td><td>")
                        .append(esc(date(d.getDiedAt()))).append("</td></tr>");
            }
            b.append("</table></div>");
        }
        b.append("</section>");

        // archief
        List<ArchivedFamily> archive = manager.getArchive();
        b.append("<section><h2>Archief <span class=\"count\">").append(archive.size()).append("</span></h2>");
        if (archive.isEmpty()) {
            b.append("<p class=\"muted\">Het archief is leeg.</p>");
        } else {
            b.append("<div class=\"scroll\"><table><tr><th>Id</th><th>Naam</th><th>Reden</th><th>Leden</th><th>Gearchiveerd</th></tr>");
            for (ArchivedFamily a : archive) {
                b.append("<tr><td><code>").append(esc(a.getId())).append("</code></td><td>").append(esc(a.getName()))
                        .append(" [").append(esc(a.getTag())).append("]</td><td>")
                        .append(esc(strip(service.archiveReason(a.getReason())))).append("</td><td>")
                        .append(a.getMembers().size()).append("</td><td>").append(esc(date(a.getArchivedAt())))
                        .append("</td></tr>");
            }
            b.append("</table></div><p class=\"muted\">Terugzetten: <code>/family archive restore &lt;id&gt;</code> (admin mode).</p>");
        }
        b.append("</section>");
    }

    // ------------------------------------------------------------------ hulp

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
    private static String strip(String text) {
        return text == null ? "" : text.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "");
    }

    static String esc(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : strip(text).toCharArray()) {
            switch (c) {
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '&' -> sb.append("&amp;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String page(String title, String body) {
        return "<!doctype html><html lang=\"nl\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<meta name=\"robots\" content=\"noindex\"><title>" + esc(title) + "</title><style>" + CSS
                + "</style></head><body><main>" + body + "</main></body></html>";
    }

    private static final String CSS = """
            :root{--bg:#15121a;--panel:#1f1a27;--line:#2f2838;--text:#ece6f2;--muted:#9a90a8;--gold:#e8b84a;--green:#5fd38a;--red:#e06a6a;--purple:#b98cf0}
            *{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:15px/1.5 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
            main{max-width:1100px;margin:0 auto;padding:24px 16px 48px}
            header{border-bottom:2px solid var(--gold);margin-bottom:24px;padding-bottom:12px}
            h1{margin:0;color:var(--gold);font-size:28px;letter-spacing:.5px}h1 span{color:var(--text);font-weight:400}
            h2{color:var(--gold);font-size:19px;margin:0 0 12px}section{background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:16px;margin-bottom:20px}
            .muted{color:var(--muted);font-size:13px}.count{background:var(--line);color:var(--text);border-radius:999px;padding:1px 9px;font-size:13px;margin-left:6px}
            .badge{display:inline-block;border-radius:6px;padding:2px 10px;margin-right:8px;font-size:13px;font-weight:600}.on{background:#1f3a2a;color:var(--green)}.off{background:#3a1f22;color:var(--red)}
            .scroll{overflow-x:auto}table{width:100%;border-collapse:collapse;font-size:14px}th,td{text-align:left;padding:7px 10px;border-bottom:1px solid var(--line);vertical-align:middle;white-space:nowrap}
            td:last-child{white-space:normal}th{color:var(--muted);font-weight:600;font-size:12px;text-transform:uppercase;letter-spacing:.4px}
            tr.group td{color:var(--purple);font-weight:700;padding-top:16px}tr.staff code{color:var(--gold)}
            code{background:#2a2333;border-radius:4px;padding:1px 6px;font-size:13px}.tag{background:#3a2f15;color:var(--gold);border-radius:4px;padding:1px 6px;font-size:11px;margin-left:4px}
            .yes{color:var(--green);text-align:center}.no{color:var(--muted);text-align:center}.mode{color:#15121a;background:var(--gold);font-weight:700;text-align:center;border-radius:4px}
            .head{width:16px;height:16px;vertical-align:-3px;margin-right:6px;image-rendering:pixelated;border-radius:2px}
            """;
}
