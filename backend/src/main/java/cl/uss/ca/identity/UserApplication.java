package cl.uss.ca.identity;

public record UserApplication(
        String id,
        String userId,
        ApplicationCode application,
        ApplicationAccessStatus status,
        String createdAt,
        String createdBy,
        String modifiedAt,
        String modifiedBy
) {
}
