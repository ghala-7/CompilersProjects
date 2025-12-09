package MainGUI;

import LARA.GrammarValidator;
import LARA.LR1Parser;
import core.Grammar;
import java.util.*;


public class Main {

    public static String parserKind = "LARA(1)";
    public static LR1Parser lr1Parser;

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        try {
            System.out.println("Enter number of non-terminals:");
            int nVars = Integer.parseInt(readNonEmptyLine(sc));
            System.out.println("Enter " + nVars + " non-terminals (tokens):");
            String[] vars = readTokensLine(sc, nVars);

            System.out.println("Enter number of terminals:");
            int nTerms = Integer.parseInt(readNonEmptyLine(sc));
            System.out.println("Enter " + nTerms + " terminals (tokens, without $):");
            String[] terms = readTokensLine(sc, nTerms);

            System.out.println("Enter number of productions:");
            int nProds = Integer.parseInt(readNonEmptyLine(sc));
            System.out.println("Enter each production in the form:");
            System.out.println("LHS RHS1 RHS2 ... RHSk");
            System.out.println("If RHS is epsilon, write: LHS epsilon");
            LinkedHashMap<String, ArrayList<String>> prodMap = new LinkedHashMap<>();
            for (int i = 0; i < nProds; i++) {
                String line = readNonEmptyLine(sc);
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 2) {
                    System.out.println("Invalid production (must have LHS and at least one RHS token). Line: " + line);
                    i--;
                    continue;
                }
                String lhs = parts[0];
                String rhs = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
                prodMap.computeIfAbsent(lhs, k -> new ArrayList<>()).add(rhs);
            }

            System.out.println("Enter start symbol:");
            String start = readNonEmptyLine(sc).trim();

            LinkedHashMap<String, ArrayList<String>> ordered = new LinkedHashMap<>();
            if (prodMap.containsKey(start)) {
                ordered.put(start, prodMap.get(start));
            } else {
                System.out.println("Warning: start symbol '" + start + "' has no productions in the provided productions.");
                ordered.put(start, prodMap.getOrDefault(start, new ArrayList<>()));
            }
            for (Map.Entry<String, ArrayList<String>> e : prodMap.entrySet()) {
                if (!e.getKey().equals(start)) {
                    ordered.put(e.getKey(), e.getValue());
                }
            }

            StringBuilder grammarBuilder = new StringBuilder();
            for (Map.Entry<String, ArrayList<String>> entry : ordered.entrySet()) {
                String lhs = entry.getKey();
                ArrayList<String> rhss = entry.getValue();
                if (rhss.isEmpty()) {
                    // produce a dummy epsilon if none given (to avoid empty RHS line)
                    grammarBuilder.append(lhs).append(" -> epsilon\n");
                } else {
                    grammarBuilder.append(lhs).append(" -> ");
                    for (int i = 0; i < rhss.size(); i++) {
                        grammarBuilder.append(rhss.get(i));
                        if (i != rhss.size() - 1) grammarBuilder.append(" | ");
                    }
                    grammarBuilder.append("\n");
                }
            }

            String grammarStr = grammarBuilder.toString().trim();

            System.out.println("\n--- Generated grammar (for validator) ---\n");
            System.out.println(grammarStr);
            System.out.println("-----------------------------------------\n");

            String state = GrammarValidator.validateGrammar(grammarStr);
            if (!state.equals("VALID")) {
                System.out.println("Grammar validation error: " + state);
                System.out.println("Fix the grammar and run again.");
                return;
            } else {
                System.out.println("Grammar is VALID.");
            }

Grammar grammar = new Grammar(grammarStr);
lr1Parser = new LR1Parser(grammar);

boolean canBeParse = lr1Parser.parseCLR1();

if (!canBeParse) {
    System.out.println("The grammar cannot be parsed (conflicts detected in CLR(1) tables).");
    return;
} else {
    System.out.println("Canonical LR(1) tables built successfully. Opening GUI output...");
}

            javax.swing.SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    new Output().setVisible(true);
                }
            });

        } finally {
           
        }
    }

    private static String readNonEmptyLine(Scanner sc) {
        String line = "";
        while (line.trim().isEmpty()) {
            if (!sc.hasNextLine()) return "";
            line = sc.nextLine();
        }
        return line;
    }

    private static String[] readTokensLine(Scanner sc, int expectedCount) {
        String line = readNonEmptyLine(sc);
        String[] tokens = line.trim().split("\\s+");
        while (tokens.length < expectedCount) {
            System.out.println("You entered " + tokens.length + " tokens, expected " + expectedCount + ". Enter remaining tokens (space separated):");
            String extra = readNonEmptyLine(sc);
            if (!extra.isEmpty()) {
                String[] more = extra.trim().split("\\s+");
                String[] combined = new String[tokens.length + more.length];
                System.arraycopy(tokens, 0, combined, 0, tokens.length);
                System.arraycopy(more, 0, combined, tokens.length, more.length);
                tokens = combined;
            }
        }
        return tokens;
    }
}
