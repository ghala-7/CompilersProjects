package core;

import java.util.*;

public class LALRConverter {

    public static class Result {
        public HashMap<String, Action>[] actionTable;   
        public HashMap<String, Integer>[] goToTable;   
        public boolean hasConflict;
        public String message;
        public String mergeInfo;
        public String lalrActionStr;              
        public String lalrGoToStr;
        public String lalrStatesStr;
    }

    public static Result convert(HashMap<String, Action>[] actionTableOrig,
                                 HashMap<String, Integer>[] goToTableOrig,
                                 String canonicalCollectionStr) {

        Result res = new Result();

        Map<Integer, LinkedHashMap<String, LinkedHashSet<String>>> itemsByState =
                parseItemsWithLookahead(canonicalCollectionStr);

        Map<String, List<Integer>> groups = buildGroupsByLR0Core(itemsByState);

        Map<Integer, Integer> oldToNew = new HashMap<>();
        int newIndex = 0;
        StringBuilder mergeSb = new StringBuilder("Merged states:\n");

        for (List<Integer> g : groups.values()) {
            for (int s : g) {
                oldToNew.put(s, newIndex);
            }
            if (g.size() > 1) {
                for (int i = 0; i < g.size(); i++) {
                    mergeSb.append("I").append(g.get(i));
                    if (i != g.size() - 1) mergeSb.append(" and ");
                }
                mergeSb.append("\n");
            }
            newIndex++;
        }
        if (mergeSb.toString().equals("Merged states:\n")) {
            mergeSb.append("No merged states\n");
        }

        int n = newIndex;

        @SuppressWarnings("unchecked")
        HashMap<String, Action>[] newAction = new HashMap[n];
        @SuppressWarnings("unchecked")
        HashMap<String, Integer>[] newGoto = new HashMap[n];

        for (int i = 0; i < n; i++) {
            newAction[i] = new HashMap<>();
            newGoto[i] = new HashMap<>();
        }

    
        String[] stateLabels = new String[n];
        for (List<Integer> g : groups.values()) {
            if (g.isEmpty()) continue;
            int ni = oldToNew.get(g.get(0));
            List<Integer> sorted = new ArrayList<>(g);
            Collections.sort(sorted);
            StringBuilder lab = new StringBuilder();
            for (int s : sorted) {
                lab.append(s);
            }
            stateLabels[ni] = lab.toString();
        }

        boolean conflict = false;
        StringBuilder conflicts = new StringBuilder();

    
        for (int old = 0; old < actionTableOrig.length; old++) {
            Integer niObj = oldToNew.get(old);
            if (niObj == null) continue; 
            int ni = niObj;

            for (Map.Entry<String, Action> e : actionTableOrig[old].entrySet()) {
                String symbol = e.getKey();
                Action orig = e.getValue();

                Action incoming;
                if (orig.getType() == ActionType.S) {
                    int oldTarget = orig.getOperand();
                    int newTarget = oldToNew.get(oldTarget);
                    incoming = new Action(ActionType.S, newTarget);
                } else {
                    incoming = new Action(orig.getType(), orig.getOperand());
                }

                Action existing = newAction[ni].get(symbol);
                if (existing == null) {
                    newAction[ni].put(symbol, incoming);
                } else {
                    if (existing.getType() != incoming.getType()) {
                        conflict = true;
                        conflicts.append("Conflict at state ")
                                .append(stateLabels[ni])
                                .append(" symbol ")
                                .append(symbol)
                                .append(" : existing=")
                                .append(formatAction(existing, stateLabels))
                                .append(" new=")
                                .append(formatAction(incoming, stateLabels))
                                .append("\n");
                    } else if (existing.getType() == ActionType.ACC) {
                    } else {
                        if (existing.getOperand() != incoming.getOperand()) {
                            conflict = true;
                            conflicts.append("Conflict at state ")
                                    .append(stateLabels[ni])
                                    .append(" symbol ")
                                    .append(symbol)
                                    .append(" : existing=")
                                    .append(formatAction(existing, stateLabels))
                                    .append(" new=")
                                    .append(formatAction(incoming, stateLabels))
                                    .append("\n");
                        }
                    }
                }
            }

            
            for (Map.Entry<String, Integer> e : goToTableOrig[old].entrySet()) {
                String var = e.getKey();
                int oldTarget = e.getValue();
                int newTarget = oldToNew.get(oldTarget);

                Integer existing = newGoto[ni].get(var);
                if (existing == null) {
                    newGoto[ni].put(var, newTarget);
                } else if (!existing.equals(newTarget)) {
                    conflict = true;
                    conflicts.append("Goto conflict at state ")
                            .append(stateLabels[ni])
                            .append(" variable ")
                            .append(var)
                            .append(" (existing -> ")
                            .append(stateLabels[existing])
                            .append(" , new -> ")
                            .append(stateLabels[newTarget])
                            .append(")\n");
                }
            }
        }

        res.hasConflict = conflict;
        res.mergeInfo = mergeSb.toString();

        if (conflict) {
            res.message = "Grammar is NOT LALR(1)\n" + conflicts;
        } else {
            res.message = "no shift/reduce or \nno reduce/reduce conflict\nso, it is a LALR(1) grammar";
        }

        res.actionTable = newAction;
        res.goToTable = newGoto;
        res.lalrActionStr = buildActionStr(newAction, stateLabels);
        res.lalrGoToStr = buildGotoStr(newGoto, stateLabels);
        res.lalrStatesStr = buildMergedLalrStates(groups, itemsByState, oldToNew, stateLabels);

        return res;
    }


    private static String formatAction(Action a, String[] labels) {
        if (a == null) return "-";
        if (a.getType() == ActionType.ACC) return "ACC";
        if (a.getType() == ActionType.S) {
            int idx = a.getOperand();
            String lab = (idx >= 0 && idx < labels.length && labels[idx] != null)
                    ? labels[idx]
                    : String.valueOf(idx);
            return "S " + lab;
        }
        return "R " + a.getOperand();
    }

    private static Map<Integer, LinkedHashMap<String, LinkedHashSet<String>>> parseItemsWithLookahead(String txt) {
        Map<Integer, LinkedHashMap<String, LinkedHashSet<String>>> m = new LinkedHashMap<>();
        Integer current = null;

        for (String rawLine : txt.split("\\r?\\n")) {
            String l = rawLine.trim();
            if (l.isEmpty()) continue;

            if (l.startsWith("State")) {
                String numStr = l.replaceAll("[^0-9]", "");
                if (!numStr.isEmpty()) {
                    current = Integer.parseInt(numStr);
                    m.put(current, new LinkedHashMap<>());
                } else current = null;
                continue;
            }

            if (current == null) continue;

            if (l.contains("->")) {
                String corePart = l;
                String lookPart = "";
                int idx = l.indexOf(" , ");
                if (idx < 0) idx = l.indexOf(" ,");
                if (idx < 0) idx = l.indexOf(",");
                if (idx >= 0) {
                    corePart = l.substring(0, idx).trim();
                    lookPart = l.substring(idx + 1).trim();
                    if (lookPart.startsWith(",")) lookPart = lookPart.substring(1).trim();
                }

                corePart = corePart.replaceAll("\\s+\\.\\s+", ".");
                LinkedHashSet<String> lookSet = new LinkedHashSet<>();
                if (!lookPart.isEmpty()) {
                    lookPart = lookPart.replaceAll("[\\[\\]\\{\\}]", "");
                    String[] toks = lookPart.split("\\s*,\\s*|\\s+");
                    for (String t : toks) {
                        t = t.trim();
                        if (t.isEmpty()) continue;
                        t = t.replaceAll("[^\\w\\$<>/=*+-]", "");
                        if (!t.isEmpty()) lookSet.add(t);
                    }
                }
                LinkedHashMap<String, LinkedHashSet<String>> stateMap = m.get(current);
                if (!stateMap.containsKey(corePart)) {
                    stateMap.put(corePart, lookSet);
                } else {
                    stateMap.get(corePart).addAll(lookSet);
                }
            }
        }
        return m;
    }

    private static Map<String, List<Integer>> buildGroupsByLR0Core(
            Map<Integer, LinkedHashMap<String, LinkedHashSet<String>>> itemsByState) {

        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        for (Integer s : itemsByState.keySet()) {
            List<String> cores = new ArrayList<>(itemsByState.get(s).keySet());
            Collections.sort(cores);
            String key = String.join("|", cores);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }
        return groups;
    }

    private static String buildMergedLalrStates(
            Map<String, List<Integer>> groups,
            Map<Integer, LinkedHashMap<String, LinkedHashSet<String>>> itemsByState,
            Map<Integer, Integer> oldToNew,
            String[] labels) {

        Map<Integer, LinkedHashMap<String, LinkedHashSet<String>>> merged = new LinkedHashMap<>();

        for (List<Integer> oldStates : groups.values()) {
            if (oldStates.isEmpty()) continue;
            int newId = oldToNew.get(oldStates.get(0));
            merged.putIfAbsent(newId, new LinkedHashMap<>());
            LinkedHashMap<String, LinkedHashSet<String>> map = merged.get(newId);

            for (int old : oldStates) {
                LinkedHashMap<String, LinkedHashSet<String>> stateItems = itemsByState.get(old);
                if (stateItems == null) continue;
                for (var it : stateItems.entrySet()) {
                    String core = it.getKey();
                    LinkedHashSet<String> la = it.getValue();
                    if (!map.containsKey(core)) {
                        map.put(core, new LinkedHashSet<>(la));
                    } else {
                        map.get(core).addAll(la);
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\nLALR States (After Merge)\n");
        for (var e : merged.entrySet()) {
            int id = e.getKey();
            String name = (labels[id] != null ? labels[id] : ("I" + id));
            sb.append("I").append(name).append(" :\n");
            var map = e.getValue();
            for (var it : map.entrySet()) {
                String core = it.getKey();
                LinkedHashSet<String> look = it.getValue();
                String laStr = String.join(",", look);
                if (laStr.isEmpty()) {
                    sb.append(core).append("\n");
                } else {
                    sb.append(core).append(" ,").append(laStr).append("\n");
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private static List<String> orderTerminals(Set<String> cols) {
        List<String> ordered = new ArrayList<>();
        String[] pref = {"id", "*", "=", "$"};
        for (String p : pref) {
            if (cols.contains(p)) ordered.add(p);
        }
        ArrayList<String> rest = new ArrayList<>(cols);
        rest.removeAll(ordered);
        Collections.sort(rest);
        ordered.addAll(rest);
        return ordered;
    }

    private static List<String> orderVariables(Set<String> vars) {
        List<String> ordered = new ArrayList<>();
        String[] pref = {"S", "L", "R"};
        for (String p : pref) {
            if (vars.contains(p)) ordered.add(p);
        }
        ArrayList<String> rest = new ArrayList<>(vars);
        rest.removeAll(ordered);
        Collections.sort(rest);
        ordered.addAll(rest);
        return ordered;
    }

    private static String buildActionStr(HashMap<String, Action>[] t, String[] labels) {
        Set<String> cols = new LinkedHashSet<>();
        for (var r : t) cols.addAll(r.keySet());

        List<String> columns = orderTerminals(cols);

        StringBuilder sb = new StringBuilder();
        sb.append("Output: LALR parsing table\n\n");

        sb.append(String.format("%-8s", " "));
        for (String c : columns) {
            sb.append(String.format("%-12s", c));
        }
        sb.append("\n");

        for (int i = 0; i < t.length; i++) {
            String rowName = (labels[i] != null ? labels[i] : String.valueOf(i));
            sb.append(String.format("%-8s", rowName));
            for (String c : columns) {
                Action a = t[i].get(c);
                String val = formatAction(a, labels);
                sb.append(String.format("%-12s", val));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static String buildGotoStr(HashMap<String, Integer>[] t, String[] labels) {
        StringBuilder sb = new StringBuilder();
        Set<String> vars = new LinkedHashSet<>();
        for (HashMap<String, Integer> row : t) {
            vars.addAll(row.keySet());
        }
        List<String> orderedVars = orderVariables(vars);

        sb.append("LALR GOTO (by variables)\n");
        sb.append(String.format("%-8s", "State"));
        for (String v : orderedVars) {
            sb.append(String.format("%-12s", v));
        }
        sb.append("\n");

        for (int i = 0; i < t.length; i++) {
            String rowName = (labels[i] != null ? labels[i] : String.valueOf(i));
            sb.append(String.format("%-8s", rowName));
            for (String v : orderedVars) {
                Integer to = t[i].get(v);
                if (to == null) {
                    sb.append(String.format("%-12s", "-"));
                } else {
                    String lab = (labels[to] != null ? labels[to] : String.valueOf(to));
                    sb.append(String.format("%-12s", lab));
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
