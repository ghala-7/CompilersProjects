package core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Stack;

public abstract class LRParser {

    public HashMap<String, Integer>[] goToTable;
    public HashMap<String, Action>[] actionTable;
    protected Grammar grammar;

    public LRParser(Grammar grammar) {
        this.grammar = grammar;
    }

    protected abstract void createGoToTable();

    public boolean accept(ArrayList<String> inputs) {
        inputs.add("$");
        int index = 0;
        Stack<String> stack = new Stack<>();
        stack.add("0");
        while(index < inputs.size()){
            int state = Integer.valueOf(stack.peek());
            String nextInput = inputs.get(index);
            Action action = actionTable[state].get(nextInput);
            if(action == null){
                return false;
            }else if(action.getType() == ActionType.S){
                stack.push(nextInput);
                stack.push(action.getOperand()+"");
                index++;
            }else if(action.getType() == ActionType.R){
                int ruleIndex = action.getOperand();
                Rule rule = grammar.getRules().get(ruleIndex);
                String leftSide = rule.getLeftSide();
                int rightSideLength = rule.getRightSide().length;
                for(int i=0; i <2*rightSideLength ; i++){
                    stack.pop();
                }
                int nextState = Integer.valueOf(stack.peek());
                stack.push(leftSide);
                int variableState = goToTable[nextState].get(leftSide);
                stack.push(variableState+"");
            }else if(action.getType() == ActionType.ACC){
                return true;
            }
        }
        return false;
    }

    private String buildTable(String title, String rowHeader,
                          String[] columns,
                          java.util.function.BiFunction<Integer,String,String> cellProvider) {

        StringBuilder sb = new StringBuilder();

        sb.append(title).append(":\n");

        int colWidth = 12;

        sb.append(String.format("%-8s", rowHeader));
        for (String col : columns) {
            sb.append(String.format("%-" + colWidth + "s", col));
        }
        sb.append("\n");

        sb.append("-".repeat(8 + columns.length * colWidth)).append("\n");

        for (int state = 0; state < actionTable.length; state++) {
            sb.append(String.format("%-8d", state));
            for (String col : columns) {
                String val = cellProvider.apply(state, col);
                sb.append(String.format("%-" + colWidth + "s", val));
            }
            sb.append("\n");
        }

        return sb.toString();

    }

    public String goToTableStr() {

        String[] vars = grammar.getVariables().toArray(new String[0]);

        return buildTable("GOTO TABLE", "State", vars, (state, variable) -> {
            Integer val = goToTable[state].get(variable);
            return (val == null ? "-" : val.toString());
        });
    }

    public String actionTableStr() {

        HashSet<String> terminals = new HashSet<>(grammar.getTerminals());
        terminals.add("$");

        String[] cols = terminals.toArray(new String[0]);

        return buildTable("ACTION TABLE", "State", cols, (state, terminal) -> {
            Action a = actionTable[state].get(terminal);
            return (a == null ? "-" : a.toString());
        });
    }

    public Grammar getGrammar() {
        return grammar;
    }

    public String acceptTrace(ArrayList<String> inputs) {
        ArrayList<String> in = new ArrayList<>(inputs);
        in.add("$");
        int index = 0;
        Stack<String> stack = new Stack<>();
        stack.add("0");
        StringBuilder sb = new StringBuilder();
        String header = String.format("%-40s%-40s%-20s", "Stack", "Input", "Action");
        sb.append(header).append("\n");
        sb.append("-".repeat(100)).append("\n");
        while(index < in.size()){
            int state = Integer.valueOf(stack.peek());
            String nextInput = in.get(index);
            Action action = actionTable[state].get(nextInput);
            String stackStr = String.join(" ", stack);
            String inputStr = String.join(" ", in.subList(index, in.size()));
            String actStr;
            if(action == null){
                actStr = "error";
                sb.append(String.format("%-40s%-40s%-20s", stackStr, inputStr, actStr)).append("\n");
                sb.append("\nnot accepted");
                return sb.toString();
            } else if(action.getType() == ActionType.S){
                actStr = "S" + action.getOperand();
                sb.append(String.format("%-40s%-40s%-20s", stackStr, inputStr, actStr)).append("\n");
                stack.push(nextInput);
                stack.push(action.getOperand()+"");
                index++;
            } else if(action.getType() == ActionType.R){
                int ruleIndex = action.getOperand();
                Rule rule = grammar.getRules().get(ruleIndex);
                actStr = "R" + ruleIndex;
                sb.append(String.format("%-40s%-40s%-20s", stackStr, inputStr, actStr)).append("\n");
                int rightSideLength = rule.getRightSide().length;
                for(int i=0; i < 2*rightSideLength; i++){
                    if(!stack.isEmpty()) stack.pop();
                }
                int nextState = Integer.valueOf(stack.peek());
                stack.push(rule.getLeftSide());
                Integer variableState = goToTable[nextState].get(rule.getLeftSide());
                if(variableState == null){
                    sb.append("\nnot accepted");
                    return sb.toString();
                }
                stack.push(variableState+"");
            } else if(action.getType() == ActionType.ACC){
                actStr = "ACC";
                sb.append(String.format("%-40s%-40s%-20s", stackStr, "$", actStr)).append("\n");
                sb.append("\naccepted");
                return sb.toString();
            }
        }
        sb.append("\nnot accepted");
        return sb.toString();
    }

}
