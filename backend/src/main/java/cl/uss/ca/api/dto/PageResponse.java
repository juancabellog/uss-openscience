package cl.uss.ca.api.dto;

import java.util.List;

public class PageResponse<T> {
    public List<T> items;
    public int page;
    public int size;
    public long totalElements;
    public int totalPages;

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalElements) {
        PageResponse<T> response = new PageResponse<>();
        response.items = items;
        response.page = page;
        response.size = size;
        response.totalElements = totalElements;
        response.totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return response;
    }
}
