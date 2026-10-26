package cl.uss.ca.external.dspace;

import java.util.ArrayList;
import java.util.List;

public class DspaceHalPage<T> {
    public int number;
    public int size;
    public int totalPages;
    public long totalElements;
    public boolean pagePresent;
    public String nextHref;
    public final List<T> elements = new ArrayList<>();

    public boolean inventoryComplete() {
        if (!pagePresent) {
            return false;
        }
        if (totalPages > 1 && number + 1 < totalPages) {
            return false;
        }
        if (totalElements == 0L) {
            return elements.isEmpty();
        }
        return elements.size() == totalElements;
    }

    public boolean confirmedEmpty() {
        return pagePresent && totalElements == 0L && elements.isEmpty();
    }

    public boolean lastPage(int requestedSize) {
        if (pagePresent) {
            if (totalPages > 0) {
                return number + 1 >= totalPages;
            }
            if (totalElements == 0L) {
                return true;
            }
            return elements.isEmpty() || (number + 1L) * Math.max(1, size > 0 ? size : requestedSize) >= totalElements;
        }
        int size = requestedSize > 0 ? requestedSize : this.size;
        return elements.isEmpty() || (size > 0 && elements.size() < size);
    }
}
