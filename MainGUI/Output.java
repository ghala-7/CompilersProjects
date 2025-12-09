package MainGUI;

import core.*;
import core.Action;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import javax.swing.*;


public class Output extends JFrame {

    private JTextArea outputArea;
    private JTextField inputField;
    private JLabel resultLabel;
    private JButton checkBtn;

    private LALRConverter.Result lalr;

    public Output() {
        setTitle("LALR Parser");
        setSize(900, 720);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        outputArea = new JTextArea();
        outputArea.setFont(new Font("Consolas", Font.PLAIN, 14));
        outputArea.setEditable(false);
        JScrollPane scroll = new JScrollPane(outputArea);

        inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.BOLD, 16));

        checkBtn = new JButton("Parse Input");
        resultLabel = new JLabel("Result");
        resultLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));

        checkBtn.addActionListener(e -> parseInput());

        JPanel bottom = new JPanel(new GridLayout(3, 1, 5, 5));
        bottom.add(inputField);
        bottom.add(checkBtn);
        bottom.add(resultLabel);

        panel.add(scroll, BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        add(panel);

        init();
    }

    private void init() {
        Grammar g = Main.lr1Parser.getGrammar();

        lalr = LALRConverter.convert(
                Main.lr1Parser.actionTable,
                Main.lr1Parser.goToTable,
                Main.lr1Parser.canonicalCollectionStr()
        );

        StringBuilder sb = new StringBuilder();

        sb.append("=== Canonical LR(1) Collection ===\n");
        sb.append(Main.lr1Parser.canonicalCollectionStr()).append("\n");

        sb.append("=== Canonical GOTO TABLE (LR(1)) ===\n");
        sb.append(Main.lr1Parser.goToTableStr()).append("\n");

        sb.append("=== Canonical ACTION TABLE (LR(1)) ===\n");
        sb.append(Main.lr1Parser.actionTableStr()).append("\n");

        sb.append("=== LALR(1) merging info ===\n");
        sb.append(lalr.mergeInfo).append("\n");

        sb.append("=== LALR(1) ACTION TABLE ===\n");
        sb.append(lalr.lalrActionStr).append("\n");

        sb.append("=== LALR(1) GOTO TABLE ===\n");
        sb.append(lalr.lalrGoToStr).append("\n");

        sb.append("=== LALR(1) States (After Merge) ===\n");
        sb.append(lalr.lalrStatesStr).append("\n");

        sb.append(lalr.message).append("\n");

        outputArea.setText(sb.toString());
        outputArea.setCaretPosition(0);
    }

    private String formatSetMap(Map<String, ? extends Set<String>> map) {
        StringBuilder sb = new StringBuilder();
        List<String> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        for (String k : keys) {
            Set<String> v = map.get(k);
            sb.append(k).append(" = {");
            if (v != null && !v.isEmpty()) {
                List<String> list = new ArrayList<>(v);
                Collections.sort(list);
                sb.append(String.join(", ", list));
            }
            sb.append("}\n");
        }
        return sb.toString();
    }

    private void parseInput() {
        String txt = inputField.getText().trim();

        ArrayList<String> words = new ArrayList<>();
        if (!txt.isEmpty()) {
            String[] parts = txt.split("\\s+");
            for (String p : parts) {
                if (!p.isEmpty()) words.add(p);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(outputArea.getText()).append("\n\n");
        sb.append(String.format("%-40s%-40s%-20s\n", "Stack", "Input", "Action"));
        sb.append("-".repeat(100)).append("\n");

        if (lalr.hasConflict) {
            sb.append("\nCannot parse - Grammar not LALR(1)\n");
            resultLabel.setText("Grammar is NOT LALR(1)");
            outputArea.setText(sb.toString());
            outputArea.setCaretPosition(0);
            return;
        }

        ArrayList<String> in = new ArrayList<>(words);
        in.add("$");

        int index = 0;
        Stack<String> stack = new Stack<>();
        stack.add("0");

        boolean accepted = false;

        while (index < in.size()) {
            int state = Integer.parseInt(stack.peek());
            String nextInput = in.get(index);

            Action action = lalr.actionTable[state].get(nextInput);

            String stackStr = String.join(" ", stack);
            String inputStr = String.join(" ", in.subList(index, in.size()));
            String actStr;

            if (action == null) {
                actStr = "error";
                sb.append(String.format("%-40s%-40s%-20s\n", stackStr, inputStr, actStr));
                break;
            } else if (action.getType() == ActionType.S) {
                actStr = "S" + action.getOperand();
                sb.append(String.format("%-40s%-40s%-20s\n", stackStr, inputStr, actStr));
                stack.push(nextInput);
                stack.push(Integer.toString(action.getOperand()));
                index++;
            } else if (action.getType() == ActionType.R) {
                int ruleIndex = action.getOperand();
                Rule rule = Main.lr1Parser.getGrammar().getRules().get(ruleIndex);
                actStr = "R" + ruleIndex;
                sb.append(String.format("%-40s%-40s%-20s\n", stackStr, inputStr, actStr));

                int rightSideLength = rule.getRightSide().length;
                for (int i = 0; i < 2 * rightSideLength; i++) {
                    if (!stack.isEmpty()) stack.pop();
                }

                int nextState = Integer.parseInt(stack.peek());
                stack.push(rule.getLeftSide());

                Integer variableState = lalr.goToTable[nextState].get(rule.getLeftSide());
                if (variableState == null) {
                    sb.append("\nnot accepted");
                    break;
                }
                stack.push(variableState.toString());
            } else if (action.getType() == ActionType.ACC) {
                actStr = "ACC";
                sb.append(String.format("%-40s%-40s%-20s\n", stackStr, "$", actStr));
                accepted = true;
                break;
            }
        }

        if (accepted) {
            sb.append("\naccepted");
            resultLabel.setText("ACCEPTED");
        } else {
            sb.append("\nnot accepted");
            resultLabel.setText("NOT ACCEPTED");
        }

        outputArea.setText(sb.toString());
        outputArea.setCaretPosition(0);
    }
}
