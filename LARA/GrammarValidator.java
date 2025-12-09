
package LARA;

public class GrammarValidator {

    public static String validateGrammar(String grammarText) {
        String[] lines = grammarText.split("\n");
        int lineNumber = 1;

        for (String line : lines) {
            String original = line;
            line = line.trim();

            if (line.isEmpty()) {
                return "Error on line " + lineNumber + ": Line is empty.";
            }

            if (!line.contains("->")) {
                return "Error on line " + lineNumber + ": Missing '->' separator.";
            }

            String[] parts = line.split("->");
            if (parts.length != 2) {
                return "Error on line " + lineNumber + ": Invalid use of '->'.";
            }

            String left = parts[0].trim();
            String right = parts[1].trim();

            if (!left.matches("[A-Za-z]+")) {
                return "Error on line " + lineNumber + ": Left side must be ONE variable (letters only). Found: " + left;
            }

            if (left.equals("S'")) {
                return "Error on line " + lineNumber + ": Variable S' is forbidden.";
            }

            if (original.contains("->") && !original.contains(" -> ")) {
                return "Error on line " + lineNumber + ": Missing spaces around '->'. Must be: A -> B C";
            }

            String[] alternatives = right.split("\\|");
            for (String alt : alternatives) {
                alt = alt.trim();
                if (alt.isEmpty()) {
                    return "Error on line " + lineNumber + ": Empty alternative found.";
                }

                if (alt.equals("ε") || alt.equals("empty") || alt.equals("lambda")) {
                    return "Error on line " + lineNumber + ": Use 'epsilon' only for empty string.";
                }

                String[] tokens = alt.split("\\s+");
                for (String t : tokens) {
                    if (t.isEmpty()) continue;

                    if (!t.matches("[A-Za-z0-9()+*/=<>]+") && !t.equals("epsilon")) {
                        return "Error on line " + lineNumber + ": Invalid token '" + t + "'";
                    }
                }
            }

            lineNumber++;
        }

        return "VALID";
    }
}

