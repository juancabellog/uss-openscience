package cl.uss.ca.ethics.auth;

/** La activación de la cuenta compartida avisa a Ethics sin crear otra contraseña. */
public interface EthicsAccountActivated {
    void onActivated(long userId, String givenName, String familyName, String campus, String faculty, String positionTitle);
}
