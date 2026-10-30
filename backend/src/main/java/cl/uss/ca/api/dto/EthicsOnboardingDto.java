package cl.uss.ca.api.dto;

/** Resultado administrativo de resolver un correo institucional. No incluye secretos. */
public class EthicsOnboardingDto {
    public String kind;
    public String email;
    public String givenName;
    public String familyName;
    public String position;
    public boolean ethicsAccess;
    public String accountStatus;
    public String invitation;
    public boolean emailSent;
    public EthicsDirectoryUserDto user;
}
