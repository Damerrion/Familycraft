package nl.fenix.core.family.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * De stamboom: relaties tussen spelers (ouder/kind, partner, broer/zus). Hoort bij de speler, niet bij een
 * familie, zodat de relaties blijven bestaan als iemand naar een aftakking gaat.
 */
public class Genealogy {

    public enum Relation {
        PARENT, PARTNER, SIBLING;

        public static Relation parse(String raw) {
            if (raw == null) {
                return null;
            }
            switch (raw.toLowerCase(java.util.Locale.ROOT)) {
                case "parent": case "ouder": case "child": case "kind":
                    return PARENT;
                case "partner": case "spouse": case "echtgenoot": case "echtgenote":
                    return PARTNER;
                case "sibling": case "broer": case "zus": case "broerzus":
                    return SIBLING;
                default:
                    return null;
            }
        }
    }

    public static final int MAX_PARENTS = 2;

    /** kind -> ouders */
    private final Map<UUID, Set<UUID>> parents = new HashMap<>();
    private final Map<UUID, UUID> partners = new HashMap<>();
    private final Map<UUID, Set<UUID>> siblings = new HashMap<>();
    /** laatst bekende spelersnamen */
    private final Map<UUID, String> names = new HashMap<>();
    /** afkomst: de lijnen (familienamen) waar iemand vandaan komt, oudste eerst */
    private final Map<UUID, java.util.List<String>> origins = new HashMap<>();

    // ---- lezen ----

    public Set<UUID> parentsOf(UUID child) {
        return parents.getOrDefault(child, Set.of());
    }

    public Set<UUID> childrenOf(UUID parent) {
        Set<UUID> children = new LinkedHashSet<>();
        parents.forEach((child, ps) -> {
            if (ps.contains(parent)) {
                children.add(child);
            }
        });
        return children;
    }

    public UUID partnerOf(UUID player) {
        return partners.get(player);
    }

    public Set<UUID> siblingsOf(UUID player) {
        return siblings.getOrDefault(player, Set.of());
    }

    public String name(UUID player) {
        return names.getOrDefault(player, player.toString().substring(0, 8));
    }

    /** Lijnen waar deze speler officieel vandaan stamt, oudste eerst (leeg = nooit van lijn gewisseld). */
    public java.util.List<String> originsOf(UUID player) {
        return origins.getOrDefault(player, java.util.List.of());
    }

    /** Onthoud dat de speler uit deze lijn komt (bij overgaan, opgaan of fuseren). */
    public void addOrigin(UUID player, String familyName) {
        java.util.List<String> list = origins.computeIfAbsent(player, k -> new java.util.ArrayList<>());
        if (list.isEmpty() || !list.get(list.size() - 1).equalsIgnoreCase(familyName)) {
            list.add(familyName);
        }
    }

    public void setName(UUID player, String name) {
        if (name != null) {
            names.put(player, name);
        }
    }

    /** Alle spelers die in de stamboom voorkomen (voor opslaan). */
    public Set<UUID> people() {
        Set<UUID> all = new HashSet<>(names.keySet());
        all.addAll(parents.keySet());
        parents.values().forEach(all::addAll);
        all.addAll(partners.keySet());
        all.addAll(siblings.keySet());
        all.addAll(origins.keySet());
        return all;
    }

    // ---- wijzigen ----

    /** {@code parent} is ouder van {@code child}. */
    public void linkParent(UUID parent, UUID child) throws FamilyException {
        if (parent.equals(child)) {
            throw new FamilyException("tree-self");
        }
        Set<UUID> ps = parentsOf(child);
        if (ps.contains(parent)) {
            throw new FamilyException("tree-already");
        }
        if (ps.size() >= MAX_PARENTS) {
            throw new FamilyException("tree-max-parents");
        }
        if (isAncestor(child, parent)) {
            throw new FamilyException("tree-cycle");
        }
        if (siblingsOf(child).contains(parent)) {
            throw new FamilyException("tree-sibling-parent");
        }
        parents.computeIfAbsent(child, k -> new LinkedHashSet<>()).add(parent);
    }

    public void linkPartner(UUID a, UUID b) throws FamilyException {
        if (a.equals(b)) {
            throw new FamilyException("tree-self");
        }
        if (b.equals(partners.get(a))) {
            throw new FamilyException("tree-already");
        }
        if (partners.containsKey(a) || partners.containsKey(b)) {
            throw new FamilyException("tree-has-partner");
        }
        partners.put(a, b);
        partners.put(b, a);
    }

    public void linkSibling(UUID a, UUID b) throws FamilyException {
        if (a.equals(b)) {
            throw new FamilyException("tree-self");
        }
        if (siblingsOf(a).contains(b)) {
            throw new FamilyException("tree-already");
        }
        // een (voor)ouder of (klein)kind kan niet ook broer/zus zijn
        if (isAncestor(a, b) || isAncestor(b, a)) {
            throw new FamilyException("tree-sibling-parent");
        }
        siblings.computeIfAbsent(a, k -> new LinkedHashSet<>()).add(b);
        siblings.computeIfAbsent(b, k -> new LinkedHashSet<>()).add(a);
    }

    public void link(Relation relation, UUID a, UUID b) throws FamilyException {
        switch (relation) {
            case PARENT:
                linkParent(a, b);
                break;
            case PARTNER:
                linkPartner(a, b);
                break;
            default:
                linkSibling(a, b);
        }
    }

    /** Haalt elke relatie tussen a en b weg. @return of er iets weg is gehaald */
    public boolean unlink(UUID a, UUID b) {
        boolean changed = removeFrom(parents, a, b) | removeFrom(parents, b, a);
        if (b.equals(partners.get(a))) {
            partners.remove(a);
            partners.remove(b);
            changed = true;
        }
        changed |= removeFrom(siblings, a, b);
        changed |= removeFrom(siblings, b, a);
        return changed;
    }

    private static boolean removeFrom(Map<UUID, Set<UUID>> map, UUID key, UUID value) {
        Set<UUID> set = map.get(key);
        if (set == null || !set.remove(value)) {
            return false;
        }
        if (set.isEmpty()) {
            map.remove(key);
        }
        return true;
    }

    /** Is {@code ancestor} (over)ouder van {@code player}? */
    public boolean isAncestor(UUID ancestor, UUID player) {
        Set<UUID> seen = new HashSet<>();
        java.util.ArrayDeque<UUID> todo = new java.util.ArrayDeque<>(parentsOf(player));
        while (!todo.isEmpty()) {
            UUID next = todo.poll();
            if (next.equals(ancestor)) {
                return true;
            }
            if (seen.add(next)) {
                todo.addAll(parentsOf(next));
            }
        }
        return false;
    }

    // ---- laden (zonder controles) ----

    public void loadParent(UUID parent, UUID child) {
        parents.computeIfAbsent(child, k -> new LinkedHashSet<>()).add(parent);
    }

    public void loadPartner(UUID a, UUID b) {
        partners.put(a, b);
        partners.put(b, a);
    }

    public void loadSibling(UUID a, UUID b) {
        siblings.computeIfAbsent(a, k -> new LinkedHashSet<>()).add(b);
        siblings.computeIfAbsent(b, k -> new LinkedHashSet<>()).add(a);
    }
}
