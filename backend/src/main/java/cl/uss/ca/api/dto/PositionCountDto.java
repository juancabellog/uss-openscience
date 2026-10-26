package cl.uss.ca.api.dto;

public class PositionCountDto {
    public String code;
    public long count;

    public PositionCountDto() {
    }

    public PositionCountDto(String code, long count) {
        this.code = code;
        this.count = count;
    }
}
