package ai.closette.usage;

import ai.closette.usage.model.AiOperation;
import ai.closette.usage.model.UserPlan;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * How much of each operation a plan allows.
 *
 * Every number is configuration rather than a constant: they are proposals until
 * real months of use say otherwise, and a limit that needs a release to change is
 * a limit nobody will change.
 */
@Component
@ConfigurationProperties(prefix = "closette.usage")
public class UsageProperties {

    /** LOG counts what would have been refused; ENFORCE refuses it. */
    public enum Mode {
        LOG,
        ENFORCE
    }

    private Mode mode = Mode.LOG;
    private Allowances free = new Allowances();
    private Allowances plus = new Allowances();
    private Welcome welcome = new Welcome();

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public Allowances getFree() {
        return free;
    }

    public void setFree(Allowances free) {
        this.free = free;
    }

    public Allowances getPlus() {
        return plus;
    }

    public void setPlus(Allowances plus) {
        this.plus = plus;
    }

    public Welcome getWelcome() {
        return welcome;
    }

    public void setWelcome(Welcome welcome) {
        this.welcome = welcome;
    }

    /** The monthly allowance table for one plan. */
    public Map<AiOperation, Integer> monthlyFor(UserPlan plan) {
        return (plan == UserPlan.PLUS ? plus : free).asMap();
    }

    public static class Allowances {
        private int photoAnalysis;
        private int outfit;
        private int shoppingAdvice;
        private int ingredientsOcr;
        private int ingredientExplanation;

        public Map<AiOperation, Integer> asMap() {
            Map<AiOperation, Integer> out = new EnumMap<>(AiOperation.class);
            out.put(AiOperation.PHOTO_ANALYSIS, photoAnalysis);
            out.put(AiOperation.OUTFIT, outfit);
            out.put(AiOperation.SHOPPING_ADVICE, shoppingAdvice);
            out.put(AiOperation.INGREDIENTS_OCR, ingredientsOcr);
            out.put(AiOperation.INGREDIENT_EXPLANATION, ingredientExplanation);
            return out;
        }

        public int getPhotoAnalysis() {
            return photoAnalysis;
        }

        public void setPhotoAnalysis(int v) {
            photoAnalysis = v;
        }

        public int getOutfit() {
            return outfit;
        }

        public void setOutfit(int v) {
            outfit = v;
        }

        public int getShoppingAdvice() {
            return shoppingAdvice;
        }

        public void setShoppingAdvice(int v) {
            shoppingAdvice = v;
        }

        public int getIngredientsOcr() {
            return ingredientsOcr;
        }

        public void setIngredientsOcr(int v) {
            ingredientsOcr = v;
        }

        public int getIngredientExplanation() {
            return ingredientExplanation;
        }

        public void setIngredientExplanation(int v) {
            ingredientExplanation = v;
        }
    }

    /** The one-time grant a newly verified account gets, and how long it lasts. */
    public static class Welcome extends Allowances {
        private int days = 30;

        public int getDays() {
            return days;
        }

        public void setDays(int days) {
            this.days = days;
        }
    }
}
