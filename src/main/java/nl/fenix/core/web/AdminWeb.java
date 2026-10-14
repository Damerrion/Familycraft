package nl.fenix.core.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import nl.fenix.core.FenixCore;
import nl.fenix.core.family.FamilyModule;
import nl.fenix.core.family.FamilyService;
import nl.fenix.core.family.model.FamilyException;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Kleine admin-website in de plugin: overzicht (commando's, permissies, staff, families, overledenen, archief) en
 * editor (families, leden, rangen, stamboom, overledenen, archief, verboden namen/wapens).
 * Toegang alleen met het geheime token uit config.yml ({@code web.token}); de link krijg je met /fenixcore web.
 * Pagina's en acties draaien op de main thread (Bukkit is niet thread-safe); alle tekst wordt ge-escaped
 * (familienamen komen van spelers). Wijzigingen gaan alleen via POST-formulieren met het token erin; elke actie komt
 * in de console en het logboek, met de naam uit de link ({@code as=}, alleen ter informatie).
 */
public final class AdminWeb {

    private static final int MAX_BODY = 64 * 1024;
    private static final int MAX_LOG = 100;

    private final FenixCore core;
    private HttpServer server;
    private ExecutorService executor;
    /** logboek, nieuwste eerst: tijd, wie, wat, gelukt */
    private final Deque<LogEntry> log = new ArrayDeque<>();

    record LogEntry(Instant time, String who, String text, boolean ok) {
    }

    /** Per verzoek: token en naam (voor links/formulieren) en de melding van de vorige actie. */
    record Ctx(String token, String as, String msg, boolean ok) {
    }

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

    /** De link voor /fenixcore web; {@code as} = naam in het logboek (wie de link vroeg). */
    public String link(String as) {
        String host = core.getConfig().getString("web.public-address", "");
        if (host == null || host.isBlank()) {
            host = Bukkit.getIp() == null || Bukkit.getIp().isBlank() ? "<server-ip>" : Bukkit.getIp();
        }
        return "http://" + host + ":" + core.getConfig().getInt("web.port", 8095) + "/?token=" + token()
                + "&as=" + enc(cleanName(as));
    }

    // ------------------------------------------------------------------ verzoeken

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod().toUpperCase();
            Map<String, String> params = parse(exchange.getRequestURI().getRawQuery());
            if (method.equals("POST") && path.equals("/action")) {
                params.putAll(parse(readBody(exchange)));
            } else if (!method.equals("GET") || path.equals("/action")) {
                // wijzigen alleen via de formulieren (POST)
                send(exchange, 405, WebPages.message("Niet toegestaan", "Deze actie bestaat niet."));
                return;
            }
            if (!validToken(params.get("token"))) {
                send(exchange, 403, WebPages.message("Geen toegang", "Gebruik de link van /fenixcore web in-game."));
                return;
            }
            Ctx ctx = new Ctx(token(), cleanName(params.get("as")), params.get("msg"), !"0".equals(params.get("ok")));
            switch (path) {
                case "/" -> send(exchange, 200, sync(() -> pages().overview(ctx)));
                case "/family" -> send(exchange, 200, sync(() -> pages().family(ctx, params.get("tag"))));
                case "/action" -> redirect(exchange, sync(() -> action(ctx, params)));
                default -> send(exchange, 404, WebPages.message("Niet gevonden", "Deze pagina bestaat niet."));
            }
        } catch (Exception e) {
            core.getLogger().log(Level.WARNING, "Admin-website: fout", e);
            send(exchange, 500, WebPages.message("Fout", "Er ging iets mis; zie de console."));
        } finally {
            exchange.close();
        }
    }

    private boolean validToken(String given) {
        String token = token();
        return !token.isEmpty() && given != null
                && MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }

    private <T> T sync(java.util.concurrent.Callable<T> task) throws Exception {
        return Bukkit.getScheduler().callSyncMethod(core, task).get(10, TimeUnit.SECONDS);
    }

    private WebPages pages() {
        FamilyModule module = core.family();
        return new WebPages(core, module == null ? null : module.service(), snapshotLog());
    }

    /** Actie uitvoeren (main thread) en de URL om naartoe terug te gaan. */
    private String action(Ctx ctx, Map<String, String> form) {
        String name = form.getOrDefault("action", "");
        String msg;
        boolean ok;
        FamilyModule module = core.family();
        FamilyService service = module == null ? null : module.service();
        if (service == null) {
            msg = "De family-module staat uit.";
            ok = false;
        } else {
            try {
                msg = new WebActions(service).run(name, form);
                ok = true;
            } catch (FamilyException e) {
                msg = WebPages.strip(service.message(e.getKey(), "%max%", "30"));
                ok = false;
            } catch (WebActions.WebException e) {
                msg = e.getMessage();
                ok = false;
            }
        }
        addLog(ctx.as(), (ok ? "" : "Mislukt: ") + msg, ok);
        core.getLogger().info("[Web-admin] " + ctx.as() + " - " + name + ": " + (ok ? "" : "MISLUKT: ") + msg);

        String back = form.getOrDefault("back", "home");
        StringBuilder url = new StringBuilder();
        if (back.startsWith("family:") && service != null
                && service.manager().getFamilyByTag(back.substring("family:".length())) != null) {
            url.append("/family?tag=").append(enc(back.substring("family:".length()))).append('&');
        } else {
            url.append("/?");
        }
        return url.append("token=").append(enc(ctx.token())).append("&as=").append(enc(ctx.as()))
                .append("&ok=").append(ok ? 1 : 0).append("&msg=").append(enc(msg)).toString();
    }

    private synchronized void addLog(String who, String text, boolean ok) {
        log.addFirst(new LogEntry(Instant.now(), who, text, ok));
        while (log.size() > MAX_LOG) {
            log.removeLast();
        }
    }

    private synchronized List<LogEntry> snapshotLog() {
        return new ArrayList<>(log);
    }

    // ------------------------------------------------------------------ http-hulp

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            byte[] body = in.readNBytes(MAX_BODY + 1);
            if (body.length > MAX_BODY) {
                throw new IOException("formulier te groot");
            }
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    static Map<String, String> parse(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return map;
        }
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            String key = URLDecoder.decode(eq < 0 ? part : part.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
            map.put(key, value);
        }
        return map;
    }

    static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /** Naam voor het logboek: alleen Minecraft-naamtekens. */
    static String cleanName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{1,16}") ? name : "web";
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.sendResponseHeaders(303, -1);
    }

    private static void send(HttpExchange exchange, int status, String html) throws IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/html; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("X-Frame-Options", "DENY");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; "
                + "img-src https://mc-heads.net; base-uri 'none'; form-action 'self'; frame-ancestors 'none'");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    /** HTML-escapen en Minecraft-kleurcodes weghalen. */
    static String esc(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : WebPages.strip(text).toCharArray()) {
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
}
