package cl.uss.ca.db;

public class GenderPrimaryIdUpdateResumen {
    private Integer personasInformadasPorPure;
    private int personasRecuperadas;
    private int personasActualizadas;
    private int personasNoEncontradas;
    private int conGender;
    private int sinGender;
    private int conPrimaryId;
    private int sinPrimaryId;
    private int errores;

    public void setPersonasInformadasPorPure(Integer personasInformadasPorPure) {
        this.personasInformadasPorPure = personasInformadasPorPure;
    }

    public void registrarRecuperada(String gender, String primaryId) {
        personasRecuperadas++;
        if (gender == null || gender.isBlank()) {
            sinGender++;
        } else {
            conGender++;
        }
        if (primaryId == null || primaryId.isBlank()) {
            sinPrimaryId++;
        } else {
            conPrimaryId++;
        }
    }

    public void registrarActualizada() {
        personasActualizadas++;
    }

    public void registrarActualizadas(int cantidad) {
        personasActualizadas += Math.max(0, cantidad);
    }

    public void registrarNoEncontrada() {
        personasNoEncontradas++;
    }

    public void registrarNoEncontradas(int cantidad) {
        personasNoEncontradas += Math.max(0, cantidad);
    }

    public void registrarError() {
        errores++;
    }

    public void imprimir() {
        System.out.println("==============================================");
        System.out.println("ACTUALIZACIÓN PURE PERSON FINALIZADA");
        System.out.println("==============================================");
        System.out.println();
        System.out.println("Personas informadas por Pure:       "
                + (personasInformadasPorPure == null ? 0 : personasInformadasPorPure));
        System.out.println("Personas recuperadas:               " + personasRecuperadas);
        System.out.println("Personas actualizadas:              " + personasActualizadas);
        System.out.println("Personas no encontradas en MySQL:   " + personasNoEncontradas);
        System.out.println();
        System.out.println("Con gender:                         " + conGender);
        System.out.println("Sin gender:                         " + sinGender);
        System.out.println();
        System.out.println("Con primaryId:                      " + conPrimaryId);
        System.out.println("Sin primaryId:                      " + sinPrimaryId);
        System.out.println();
        System.out.println("Errores:                            " + errores);
    }
}
