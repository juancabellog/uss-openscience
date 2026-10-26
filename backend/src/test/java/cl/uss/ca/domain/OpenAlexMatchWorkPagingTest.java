package cl.uss.ca.domain;

/**
 * Presentation paging bounds for OpenAlex evidence works. Not executed by the agent.
 */
public final class OpenAlexMatchWorkPagingTest {
    private OpenAlexMatchWorkPagingTest() {
    }

    public static void main(String[] args) {
        assert OpenAlexMatchWorkPaging.page(null) == 1;
        assert OpenAlexMatchWorkPaging.page(0) == 1;
        assert OpenAlexMatchWorkPaging.page(3) == 3;
        assert OpenAlexMatchWorkPaging.size(null) == OpenAlexMatchWorkPaging.DEFAULT_SIZE;
        assert OpenAlexMatchWorkPaging.size(0) == OpenAlexMatchWorkPaging.DEFAULT_SIZE;
        assert OpenAlexMatchWorkPaging.size(25) == 25;
        assert OpenAlexMatchWorkPaging.size(1000) == OpenAlexMatchWorkPaging.MAX_SIZE;
        assert OpenAlexMatchWorkPaging.search(null) == null;
        assert OpenAlexMatchWorkPaging.search("  ") == null;
        assert "doi-1".equals(OpenAlexMatchWorkPaging.search(" doi-1 "));
        assert OpenAlexMatchWorkPaging.offset(1, 25) == 0;
        assert OpenAlexMatchWorkPaging.offset(3, 25) == 50;
        System.out.println("OpenAlexMatchWorkPagingTest ok");
    }
}
