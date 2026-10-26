package cl.uss.ca.db;

public class PersistenciaResumen {
    private int personas;
    private int asociaciones;
    private int titulos;

    public void registrarPersona(int asociacionesGuardadas, int titulosGuardados) {
        personas++;
        asociaciones += asociacionesGuardadas;
        titulos += titulosGuardados;
    }

    public int getPersonas() {
        return personas;
    }

    public int getAsociaciones() {
        return asociaciones;
    }

    public int getTitulos() {
        return titulos;
    }
}
