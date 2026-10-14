package nl.fenix.core.web;

import nl.fenix.core.family.FamilyService;
import nl.fenix.core.family.model.Family;
import nl.fenix.core.family.model.FamilyException;
import nl.fenix.core.family.model.FamilyManager;
import nl.fenix.core.family.model.FamilyRank;
import nl.fenix.core.family.model.Genealogy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Wat de admin-editor op de website kan doen. Alles op de main thread (via {@link AdminWeb}); de acties gaan langs
 * dezelfde regels als in-game (FamilyManager), alleen met admin-rechten (bypass). Spelers krijgen dezelfde meldingen.
 */
final class WebActions {

    /** Fout voor op de website (geen FamilyException-sleutel). */
    static final class WebException extends Exception {
        WebException(String message) {
            super(message);
        }
    }

    private final FamilyService service;
    private final FamilyManager manager;

    WebActions(FamilyService service) {
        this.service = service;
        this.manager = service.manager();
    }

    /** Voert de actie uit. @return melding voor op de website (en in het logboek) */
    String run(String action, Map<String, String> form) throws WebException, FamilyException {
        return switch (action) {
            case "family.rename" -> rename(family(form), form);
            case "family.kingdom" -> kingdom(family(form), form);
            case "family.crestreset" -> crestReset(family(form));
            case "family.disband" -> disband(family(form), form);
            case "member.rank" -> rank(family(form), form);
            case "member.kick" -> kick(family(form), form);
            case "member.deceased" -> deceased(family(form), form);
            case "deceased.revive" -> revive(family(form), form);
            case "deceased.remove" -> removeDeceased(family(form), form);
            case "tree.link" -> link(family(form), form);
            case "tree.unlink" -> unlink(family(form), form);
            case "archive.restore" -> restore(form);
            case "archive.delete" -> deleteArchived(form);
            case "banned.add" -> bannedAdd(form);
            case "banned.remove" -> bannedRemove(form);
            case "crestban.remove" -> crestBanRemove(form);
            case "staff.modeoff" -> modeOff(form);
            default -> throw new WebException("Onbekende actie.");
        };
    }

    // ------------------------------------------------------------------ familie

    private String rename(Family family, Map<String, String> form) throws FamilyException {
        String oldName = family.getName();
        String name = field(form, "name").replace(' ', '_');
        String tag = field(form, "newtag");
        manager.rename(family, family.getOwner(), true, name, tag.isEmpty() ? family.getTag() : tag);
        family.setNeedsRename(false);
        form.put("back", "family:" + family.getTag()); // de tag kan veranderd zijn
        service.save();
        service.refreshAll(family);
        service.broadcast(family, null, "renamed", "%old%", oldName, "%name%", family.getName(), "%tag%", family.getTag());
        return oldName + " heet nu " + family.getName() + " [" + family.getTag() + "].";
    }

    private String kingdom(Family family, Map<String, String> form) {
        String kingdom = field(form, "kingdom");
        family.setKingdomName(kingdom.isEmpty() ? null : kingdom);
        service.save();
        return "Afstamming van " + family.getName() + ": " + (kingdom.isEmpty() ? "geen" : kingdom) + ".";
    }

    private String crestReset(Family family) throws FamilyException {
        service.resetCrest(Bukkit.getConsoleSender(), family);
        return "Familiewapen van " + family.getName() + " teruggezet naar de standaard.";
    }

    private String disband(Family family, Map<String, String> form) throws WebException {
        confirm(form);
        List<UUID> members = new ArrayList<>(family.getMembers().keySet());
        manager.disband(family);
        service.save();
        for (UUID member : members) {
            service.refresh(member);
            Player online = Bukkit.getPlayer(member);
            if (online != null) {
                service.send(online, "disbanded-target", "%name%", family.getName());
            }
        }
        return family.getName() + " is opgeheven (staat in het archief).";
    }

    // ------------------------------------------------------------------ leden

    private String rank(Family family, Map<String, String> form) throws WebException, FamilyException {
        UUID member = member(family, form, "member");
        FamilyRank rank = FamilyRank.parse(field(form, "rank"));
        if (rank == null) {
            throw new WebException("Onbekende rang.");
        }
        UUID oldHead = family.getOwner();
        manager.setRank(family, oldHead, true, member, rank);
        service.save();
        service.refresh(member);
        service.refresh(oldHead);
        Player online = Bukkit.getPlayer(member);
        if (online != null) {
            service.send(online, "rank-received", "%rank%", service.rankName(rank), "%name%", family.getName());
        }
        if (rank == FamilyRank.HEAD) {
            service.broadcast(family, null, "new-head", "%player%", service.nameOf(member), "%name%", family.getName());
        }
        return service.nameOf(member) + " is nu " + service.rankName(rank) + " van " + family.getName() + ".";
    }

    private String kick(Family family, Map<String, String> form) throws WebException, FamilyException {
        confirm(form);
        UUID member = member(family, form, "member");
        String name = service.nameOf(member);
        manager.kick(family, family.getOwner(), true, member);
        service.save();
        service.refresh(member);
        Player online = Bukkit.getPlayer(member);
        if (online != null) {
            service.send(online, "kicked-target", "%name%", family.getName());
        }
        return name + " is uit " + family.getName() + " gezet.";
    }

    private String deceased(Family family, Map<String, String> form) throws WebException, FamilyException {
        UUID member = member(family, form, "member");
        String player = service.nameOf(member);
        FamilyManager.Death death = manager.declareDeceased(family, family.getOwner(), true, member, field(form, "character"));
        service.save();
        service.refresh(member);
        String character = family.getMembers().get(death.memorial());
        service.broadcast(family, null, "deceased-broadcast", "%character%", character, "%player%", player,
                "%name%", family.getName());
        if (death.newHead() != null) {
            service.refresh(death.newHead());
            service.broadcast(family, null, "new-head", "%player%", service.nameOf(death.newHead()), "%name%", family.getName());
        }
        return character + " (" + player + ") is overleden.";
    }

    private String revive(Family family, Map<String, String> form) throws WebException, FamilyException {
        UUID memorial = member(family, form, "member");
        String character = service.nameOf(memorial);
        UUID player = manager.revive(family, family.getOwner(), true, memorial);
        service.save();
        service.refresh(player);
        return character + " leeft weer (" + service.nameOf(player) + ").";
    }

    private String removeDeceased(Family family, Map<String, String> form) throws WebException, FamilyException {
        confirm(form);
        UUID memorial = member(family, form, "member");
        String character = service.nameOf(memorial);
        manager.removeDeceased(family, family.getOwner(), true, memorial);
        service.save();
        return "Gedenkplaats van " + character + " weggehaald.";
    }

    // ------------------------------------------------------------------ stamboom

    private String link(Family family, Map<String, String> form) throws WebException, FamilyException {
        Genealogy.Relation relation = Genealogy.Relation.parse(field(form, "relation"));
        if (relation == null) {
            throw new WebException("Onbekende relatie.");
        }
        UUID a = member(family, form, "a");
        UUID b = member(family, form, "b");
        manager.link(family, family.getOwner(), true, relation, a, b);
        service.save();
        return service.nameOf(a) + " en " + service.nameOf(b) + " gekoppeld ("
                + relation.name().toLowerCase(Locale.ROOT) + ").";
    }

    private String unlink(Family family, Map<String, String> form) throws WebException, FamilyException {
        UUID a = uuid(field(form, "a"));
        UUID b = uuid(field(form, "b"));
        manager.unlink(family, family.getOwner(), true, a, b);
        service.save();
        return "Relatie tussen " + service.nameOf(a) + " en " + service.nameOf(b) + " weggehaald.";
    }

    // ------------------------------------------------------------------ archief, verboden lijsten, staff

    private String restore(Map<String, String> form) throws FamilyException {
        String id = field(form, "id");
        service.unarchive(Bukkit.getConsoleSender(), id);
        Family family = null;
        for (Family f : manager.getFamilies()) {
            if (id.startsWith(f.getTag().toLowerCase(Locale.ROOT) + "-")) {
                family = f;
            }
        }
        return (family == null ? id : family.getName()) + " is teruggezet uit het archief.";
    }

    private String deleteArchived(Map<String, String> form) throws WebException, FamilyException {
        confirm(form);
        String id = field(form, "id");
        service.deleteArchived(Bukkit.getConsoleSender(), id);
        return id + " is definitief uit het archief gehaald.";
    }

    private String bannedAdd(Map<String, String> form) throws WebException {
        String word = field(form, "word").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}]", "");
        if (word.isEmpty() || word.length() > 30) {
            throw new WebException("Vul een woord in (alleen letters, max 30).");
        }
        List<String> words = new ArrayList<>(service.config().getStringList("banned.names"));
        if (words.contains(word)) {
            throw new WebException("'" + word + "' staat al op de lijst.");
        }
        words.add(word);
        saveBannedNames(words);
        return "'" + word + "' is nu verboden in familienamen en tags.";
    }

    private String bannedRemove(Map<String, String> form) throws WebException {
        String word = field(form, "word");
        List<String> words = new ArrayList<>(service.config().getStringList("banned.names"));
        if (!words.remove(word)) {
            throw new WebException("'" + word + "' staat niet op de lijst.");
        }
        saveBannedNames(words);
        return "'" + word + "' is niet meer verboden.";
    }

    private void saveBannedNames(List<String> words) {
        service.module().setAndSave("banned.names", words);
        manager.setBannedWords(words);
    }

    private String crestBanRemove(Map<String, String> form) throws WebException {
        if (!service.crestBans().remove(field(form, "fingerprint"))) {
            throw new WebException("Dat wapen staat niet (meer) op de lijst.");
        }
        return "Wapen weer toegestaan.";
    }

    private String modeOff(Map<String, String> form) throws WebException {
        Player player = Bukkit.getPlayer(uuid(field(form, "player")));
        if (player == null) {
            throw new WebException("Die speler is niet meer online.");
        }
        service.setAdminMode(player, false);
        service.setDev(player, false);
        service.send(player, "admin-off");
        return "Admin mode en family dev mode van " + player.getName() + " staan uit.";
    }

    // ------------------------------------------------------------------ hulp

    private Family family(Map<String, String> form) throws WebException {
        Family family = manager.getFamilyByTag(field(form, "tag"));
        if (family == null) {
            throw new WebException("Die familie bestaat niet (meer).");
        }
        return family;
    }

    private static UUID member(Family family, Map<String, String> form, String key) throws WebException {
        UUID uuid = uuid(field(form, key));
        if (!family.isMember(uuid)) {
            throw new WebException("Die speler zit niet (meer) in " + family.getName() + ".");
        }
        return uuid;
    }

    private static UUID uuid(String raw) throws WebException {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new WebException("Ongeldige speler.");
        }
    }

    private static void confirm(Map<String, String> form) throws WebException {
        if (!"yes".equals(form.get("confirm"))) {
            throw new WebException("Vink 'zeker' aan om dit te doen.");
        }
    }

    private static String field(Map<String, String> form, String key) {
        String value = form.get(key);
        return value == null ? "" : value.trim();
    }
}
