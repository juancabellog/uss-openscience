package cl.uss.ca.api.dto;

public class ScopusCountDto {
    public int count;
    public long people;

    public ScopusCountDto() {
    }

    public ScopusCountDto(int count, long people) {
        this.count = count;
        this.people = people;
    }
}
