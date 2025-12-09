package core;

import java.util.Objects;

public class Action {
    private ActionType type;
    private int operand;

    public Action(ActionType type, int operand) {
        this.type = type;
        this.operand = operand;
    }

    @Override
    public String toString() {
        return type + " " + (type == ActionType.ACC ? "" : operand);
    }

    public ActionType getType() {
        return type;
    }

    public int getOperand() {
        return operand;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Action)) return false;
        Action action = (Action) o;
        return operand == action.operand && type == action.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, operand);
    }
}
