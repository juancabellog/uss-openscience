package cl.uss.ca.db;

public class PersonaEmailBd {
    private final String email;
    private final String gender;

    public PersonaEmailBd(String email, String gender) {
        this.email = email == null ? "" : email;
        this.gender = gender == null ? "" : gender;
    }

    public static PersonaEmailBd vacio() {
        return new PersonaEmailBd("", "");
    }

    public String getEmail() {
        return email;
    }

    public String getGender() {
        return gender;
    }
}
