package cl.uss.ca.sync.diff;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

final class SyncValues {
    private static final Logger LOG = Logger.getLogger(SyncValues.class.getName());

    private SyncValues() {
    }

    static String norm(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static boolean eq(String left, String right) {
        return Objects.equals(norm(left), norm(right));
    }

    static String normIssn(String value) {
        String normalized = norm(value);
        if (normalized == null) {
            return null;
        }
        return normalized.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
    }

    static boolean eqIssn(String left, String right) {
        return Objects.equals(normIssn(left), normIssn(right));
    }

    static boolean eq(Boolean left, Boolean right) {
        return Objects.equals(left, right);
    }

    static boolean eq(boolean left, Boolean right) {
        return Objects.equals(left, right != null && right);
    }

    static boolean eq(Long left, Long right) {
        return Objects.equals(left, right);
    }

    static boolean eq(Integer left, Integer right) {
        return Objects.equals(left, right);
    }

    static boolean eq(LocalDate left, LocalDate right) {
        return Objects.equals(left, right);
    }

    static boolean eq(OffsetDateTime left, OffsetDateTime right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.toInstant().equals(right.toInstant());
    }

    static boolean eqFte(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.compareTo(right) == 0;
    }

    static String fte(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.stripTrailingZeros().toPlainString();
    }

    static String key(Object... parts) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                builder.append('|');
            }
            Object part = parts[i];
            if (part instanceof String text) {
                builder.append(norm(text) == null ? "" : norm(text));
            } else if (part instanceof OffsetDateTime time) {
                builder.append(time.toInstant());
            } else {
                builder.append(part == null ? "" : part);
            }
        }
        return builder.toString();
    }

    static String normUuid(String value) {
        String normalized = norm(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    static String clip(String value) {
        String normalized = norm(value);
        if (normalized == null) {
            return null;
        }
        return normalized.length() <= 1000 ? normalized : normalized.substring(0, 1000);
    }

    static <T> boolean diffSignatures(
            Collection<T> previous,
            Collection<T> current,
            Function<T, String> signature,
            Function<T, String> label,
            String scope,
            String addedDescription,
            String removedDescription,
            List<SyncChange> changes
    ) {
        Map<String, Long> oldCounts = counts(previous, signature);
        Map<String, Long> newCounts = counts(current, signature);
        Map<String, String> oldLabels = labels(previous, signature, label);
        Map<String, String> newLabels = labels(current, signature, label);
        boolean changed = false;
        for (Map.Entry<String, Long> entry : oldCounts.entrySet()) {
            long next = newCounts.getOrDefault(entry.getKey(), 0L);
            long removed = entry.getValue() - next;
            for (int i = 0; i < removed; i++) {
                changes.add(SyncChange.updated(
                        scope,
                        removedDescription,
                        oldLabels.get(entry.getKey()),
                        null
                ));
                changed = true;
            }
        }
        for (Map.Entry<String, Long> entry : newCounts.entrySet()) {
            long previousCount = oldCounts.getOrDefault(entry.getKey(), 0L);
            long added = entry.getValue() - previousCount;
            for (int i = 0; i < added; i++) {
                changes.add(SyncChange.updated(
                        scope,
                        addedDescription,
                        null,
                        newLabels.get(entry.getKey())
                ));
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Matches collections by a stable business key, then compares semantic attributes.
     * Order does not matter. Duplicate keys are preserved and warned, not silently collapsed.
     * Items with a null business key are never collapsed together.
     */
    static <T> boolean diffByBusinessKey(
            Collection<T> previous,
            Collection<T> current,
            Function<T, String> businessKey,
            BiPredicate<T, T> semanticEquals,
            BiFunction<T, T, Integer> affinity,
            BiConsumer<T, T> emitFieldUpdates,
            Function<T, String> label,
            String scope,
            String addedDescription,
            String removedDescription,
            String parentUuid,
            List<SyncChange> changes
    ) {
        Map<String, List<T>> oldByKey = new LinkedHashMap<>();
        Map<String, List<T>> newByKey = new LinkedHashMap<>();
        List<T> unkeyedOld = new ArrayList<>();
        List<T> unkeyedNew = new ArrayList<>();
        groupByBusinessKey(previous, businessKey, oldByKey, unkeyedOld);
        groupByBusinessKey(current, businessKey, newByKey, unkeyedNew);

        boolean changed = false;
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(oldByKey.keySet());
        keys.addAll(newByKey.keySet());
        for (String key : keys) {
            List<T> oldItems = oldByKey.getOrDefault(key, List.of());
            List<T> newItems = newByKey.getOrDefault(key, List.of());
            if (oldItems.size() > 1 || newItems.size() > 1) {
                LOG.log(Level.WARNING, () -> "Duplicate business key " + key
                        + " on parent " + parentUuid
                        + " old=" + oldItems.size()
                        + " new=" + newItems.size());
            }
            changed |= matchKeyedItems(
                    oldItems,
                    newItems,
                    key,
                    parentUuid,
                    semanticEquals,
                    affinity,
                    emitFieldUpdates,
                    label,
                    scope,
                    addedDescription,
                    removedDescription,
                    changes
            );
        }
        changed |= matchKeyedItems(
                unkeyedOld,
                unkeyedNew,
                null,
                parentUuid,
                semanticEquals,
                affinity,
                emitFieldUpdates,
                label,
                scope,
                addedDescription,
                removedDescription,
                changes
        );
        return changed;
    }

    static void field(List<SyncChange> changes, String field, String oldValue, String newValue) {
        if (!eq(oldValue, newValue)) {
            changes.add(SyncChange.updated("PARENT", field, oldValue, newValue));
        }
    }

    static void field(List<SyncChange> changes, String field, Object oldValue, Object newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            changes.add(SyncChange.updated("PARENT", field, string(oldValue), string(newValue)));
        }
    }

    private static String string(Object value) {
        if (value instanceof OffsetDateTime time) {
            return time.toInstant().toString();
        }
        return value == null ? null : clip(String.valueOf(value));
    }

    private static <T> Map<String, Long> counts(Collection<T> items, Function<T, String> signature) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (T item : empty(items)) {
            if (item == null) {
                continue;
            }
            String key = signature.apply(item);
            if (key == null) {
                continue;
            }
            counts.merge(key, 1L, Long::sum);
        }
        return counts;
    }

    private static <T> Map<String, String> labels(
            Collection<T> items,
            Function<T, String> signature,
            Function<T, String> label
    ) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (T item : empty(items)) {
            if (item == null) {
                continue;
            }
            String key = signature.apply(item);
            if (key != null && !labels.containsKey(key)) {
                labels.put(key, clip(label.apply(item)));
            }
        }
        return labels;
    }

    private static <T> Collection<T> empty(Collection<T> items) {
        return items == null ? List.of() : items;
    }

    static <T> List<T> list(Collection<T> items) {
        return items == null ? List.of() : new ArrayList<>(items);
    }

    private static <T> void groupByBusinessKey(
            Collection<T> items,
            Function<T, String> businessKey,
            Map<String, List<T>> keyed,
            List<T> unkeyed
    ) {
        for (T item : empty(items)) {
            if (item == null) {
                continue;
            }
            String key = businessKey.apply(item);
            if (key == null || key.isBlank()) {
                unkeyed.add(item);
                continue;
            }
            keyed.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }
    }

    private static <T> boolean matchKeyedItems(
            List<T> oldItems,
            List<T> newItems,
            String businessKey,
            String parentUuid,
            BiPredicate<T, T> semanticEquals,
            BiFunction<T, T, Integer> affinity,
            BiConsumer<T, T> emitFieldUpdates,
            Function<T, String> label,
            String scope,
            String addedDescription,
            String removedDescription,
            List<SyncChange> changes
    ) {
        if (oldItems.isEmpty() && newItems.isEmpty()) {
            return false;
        }
        boolean[] usedOld = new boolean[oldItems.size()];
        boolean[] usedNew = new boolean[newItems.size()];
        boolean changed = false;

        for (int i = 0; i < oldItems.size(); i++) {
            for (int j = 0; j < newItems.size(); j++) {
                if (usedNew[j]) {
                    continue;
                }
                T oldItem = oldItems.get(i);
                T newItem = newItems.get(j);
                if (semanticEquals.test(oldItem, newItem)) {
                    usedOld[i] = true;
                    usedNew[j] = true;
                    logMatch(parentUuid, businessKey, true, true, true);
                    break;
                }
            }
        }

        for (int i = 0; i < oldItems.size(); i++) {
            if (usedOld[i]) {
                continue;
            }
            int partner = bestUnused(oldItems.get(i), newItems, usedNew, affinity);
            if (partner >= 0) {
                usedOld[i] = true;
                usedNew[partner] = true;
                logMatch(parentUuid, businessKey, true, true, false);
                int before = changes.size();
                emitFieldUpdates.accept(oldItems.get(i), newItems.get(partner));
                changed |= changes.size() > before;
            }
        }

        for (int i = 0; i < oldItems.size(); i++) {
            if (usedOld[i]) {
                continue;
            }
            logMatch(parentUuid, businessKey, true, false, false);
            changes.add(SyncChange.updated(
                    scope,
                    removedDescription,
                    clip(label.apply(oldItems.get(i))),
                    null
            ));
            changed = true;
        }
        for (int j = 0; j < newItems.size(); j++) {
            if (usedNew[j]) {
                continue;
            }
            logMatch(parentUuid, businessKey, false, true, false);
            changes.add(SyncChange.updated(
                    scope,
                    addedDescription,
                    null,
                    clip(label.apply(newItems.get(j)))
            ));
            changed = true;
        }
        return changed;
    }

    private static <T> int bestUnused(
            T oldItem,
            List<T> newItems,
            boolean[] usedNew,
            BiFunction<T, T, Integer> affinity
    ) {
        int best = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int j = 0; j < newItems.size(); j++) {
            if (usedNew[j]) {
                continue;
            }
            int score = affinity == null ? 0 : affinity.apply(oldItem, newItems.get(j));
            if (best < 0 || score > bestScore) {
                best = j;
                bestScore = score;
            }
        }
        return best;
    }

    private static void logMatch(
            String parentUuid,
            String businessKey,
            boolean oldFound,
            boolean newFound,
            boolean semanticEquals
    ) {
        if (!LOG.isLoggable(Level.FINE)) {
            return;
        }
        LOG.log(Level.FINE, "Person UUID {0} Organization businessKey={1} OLD found={2} NEW found={3} semanticEquals={4}",
                new Object[]{
                        parentUuid,
                        businessKey == null ? "unkeyed" : businessKey,
                        oldFound,
                        newFound,
                        semanticEquals
                });
    }
}
