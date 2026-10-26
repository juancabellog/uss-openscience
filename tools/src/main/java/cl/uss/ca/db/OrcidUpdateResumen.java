package cl.uss.ca.db;

import java.util.ArrayList;
import java.util.List;

public class OrcidUpdateResumen {
    private Integer personasInformadasPorPure;
    private int personasRecuperadas;
    private int personasEncontradas;
    private int personasNoEncontradas;
    private int orcidSinCambio;
    private int errores;
    private final List<OrcidCambio> cambios = new ArrayList<>();

    public void setPersonasInformadasPorPure(Integer personasInformadasPorPure) {
        this.personasInformadasPorPure = personasInformadasPorPure;
    }

    public void registrarRecuperada() {
        personasRecuperadas++;
    }

    public void registrarEncontradas(int cantidad) {
        personasEncontradas += Math.max(0, cantidad);
    }

    public void registrarNoEncontradas(int cantidad) {
        personasNoEncontradas += Math.max(0, cantidad);
    }

    public void registrarSinCambio() {
        orcidSinCambio++;
    }

    public void registrarSinCambio(int cantidad) {
        orcidSinCambio += Math.max(0, cantidad);
    }

    public void registrarCambio(OrcidCambio cambio) {
        if (cambio != null) {
            cambios.add(cambio);
        }
    }

    public void registrarError() {
        errores++;
    }

    public void imprimir() {
        System.out.println("==============================================");
        System.out.println("ACTUALIZACIÓN ORCID PURE FINALIZADA");
        System.out.println("==============================================");
        System.out.println();
        System.out.println("Personas informadas por Pure:       "
                + (personasInformadasPorPure == null ? 0 : personasInformadasPorPure));
        System.out.println("Personas recuperadas:               " + personasRecuperadas);
        System.out.println("Personas encontradas en MySQL:      " + personasEncontradas);
        System.out.println("Personas no encontradas en MySQL:   " + personasNoEncontradas);
        System.out.println();
        System.out.println("ORCID sin cambio:                   " + orcidSinCambio);
        System.out.println("ORCID que cambiaron:                " + cambios.size());
        System.out.println("Errores:                            " + errores);
        System.out.println();
        if (cambios.isEmpty()) {
            System.out.println("Ningún ORCID cambió.");
            return;
        }
        System.out.println("uuid\tnombre\torcid anterior\torcid nuevo");
        for (OrcidCambio cambio : cambios) {
            System.out.println(cambio.getUuid()
                    + "\t" + cambio.getNombre()
                    + "\t" + cambio.getOrcidAnterior()
                    + "\t" + cambio.getOrcidNuevo());
        }
    }
}
