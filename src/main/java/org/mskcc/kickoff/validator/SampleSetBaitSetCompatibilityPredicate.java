package org.mskcc.kickoff.validator;

import org.apache.log4j.Logger;
import org.mskcc.kickoff.util.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

public class SampleSetBaitSetCompatibilityPredicate implements BiPredicate<String, String> {
    private static final Logger LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private List<Pair<String>> baitSetCompatibilityPairs = new ArrayList<>();

    public SampleSetBaitSetCompatibilityPredicate(List<Pair<String>> baitSetCompatibilityPairs) {
        this.baitSetCompatibilityPairs = baitSetCompatibilityPairs;
    }

    @Override
    public boolean test(String baitSet1, String baitSet2) {
        boolean isCompatible = baitSet1.equals(baitSet2) || isCompatible(baitSet1, baitSet2);

        if (!isCompatible)
            LOGGER.warn(String.format("Bait set '%s' and '%s' are incompatible", baitSet1, baitSet2));
        else
            LOGGER.info(String.format("Bait set '%s' and '%s' are compatible", baitSet1, baitSet2));

        return isCompatible;
    }

    private boolean isCompatible(String baitSet1, String baitSet2) {
        return baitSetCompatibilityPairs.contains(new Pair<>(baitSet1, baitSet2));
    }

    public static class Pair<T> {
        private T element1;
        private T element2;

        public Pair(T element1, T element2) {
            this.element1 = element1;
            this.element2 = element2;
        }

        @Override
        public String toString() {
            return "Pair{" +
                    "element1=" + element1 +
                    ", element2=" + element2 +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Pair pair = (Pair) o;

            return element1.equals(pair.element1) && element2.equals(pair.element2) ||
                    element1.equals(pair.element2) && element2.equals(pair.element1);
        }

        @Override
        public int hashCode() {
            int result = 31 * element1.hashCode();
            result += 31 * element2.hashCode();
            return result;
        }
    }
}
