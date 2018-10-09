package org.mskcc.kickoff.validator;

import org.mskcc.domain.Recipe;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiPredicate;

public class SampleSetBaitSetCompatibilityPredicate implements BiPredicate<String, String> {
    private static Set<Pair<String>> baitSetCompatibility = new HashSet<>();

    static {
        baitSetCompatibility.add(new Pair<>(Recipe.IMPACT_410.getValue(), Recipe.IMPACT_468.getValue()));
        baitSetCompatibility.add(new Pair<>(Recipe.IMPACT_410.getValue(), Recipe.IMPACT_341.getValue()));
        baitSetCompatibility.add(new Pair<>(Recipe.IMPACT_468.getValue(), Recipe.IMPACT_341.getValue()));
    }

    @Override
    public boolean test(String baitSet1, String baitSet2) {
        return baitSet1.equals(baitSet2) || isCompatible(baitSet1, baitSet2);
    }

    private boolean isCompatible(String baitSet1, String baitSet2) {
        return baitSetCompatibility.contains(new Pair<>(baitSet1, baitSet2));
    }

    static class Pair<T> {
        private T element1;
        private T element2;

        public Pair(T element1, T element2) {
            this.element1 = element1;
            this.element2 = element2;
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
