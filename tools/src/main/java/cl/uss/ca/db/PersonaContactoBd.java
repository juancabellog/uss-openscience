package cl.uss.ca.db;

public class PersonaContactoBd {
    private final String email;
    private final String rut;

    public PersonaContactoBd(String email, String rut) {
        this.email = email == null ? "" : email;
        this.rut = rut == null ? "" : rut;
    }

    public static PersonaContactoBd vacio() {
        return new PersonaContactoBd("", "");
    }

    public String getEmail() {
        return email;
    }

    public String getRut() {
        return rut;
    }
}
