package cl.uss.ca.external.pure;

import java.util.List;

public class PurePage<T> {
    public final Integer count;
    public final List<T> items;

    public PurePage(Integer count, List<T> items) {
        this.count = count;
        this.items = items == null ? List.of() : items;
    }
}
