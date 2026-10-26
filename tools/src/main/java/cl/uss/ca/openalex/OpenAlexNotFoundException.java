package cl.uss.ca.openalex;

public class OpenAlexNotFoundException extends Exception {
    public OpenAlexNotFoundException(String doi) {
        super("DOI no encontrado en OpenAlex: " + doi);
    }
}
