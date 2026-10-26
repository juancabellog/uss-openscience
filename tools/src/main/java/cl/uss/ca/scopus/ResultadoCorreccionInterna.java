package cl.uss.ca.scopus;

public record ResultadoCorreccionInterna(
        long personId,
        String uuid,
        String nombre,
        String scopusIdDuplicado,
        String scopusIdsDistintosAntes,
        Integer ocurrenciasAntes,
        String pureIdPrimeraOcurrencia,
        String pureIdSegundaOcurrenciaConservada,
        String pureIdsEliminados,
        String scopusIdsDistintosDespues,
        Integer ocurrenciasDespues,
        String accion,
        String mensaje
) {
    public static final String DRY_RUN_CONSERVARIA_SEGUNDO = "DRY_RUN_CONSERVARIA_SEGUNDO";
    public static final String CORREGIDO_OK = "CORREGIDO_OK";
    public static final String YA_CORREGIDO = "YA_CORREGIDO";
    public static final String NO_ENCONTRADO_EN_PURE = "NO_ENCONTRADO_EN_PURE";
    public static final String VALIDACION_FALLIDA = "VALIDACION_FALLIDA";
    public static final String VALIDACION_FALLIDA_SCOPUS_DISTINTOS = "VALIDACION_FALLIDA_SCOPUS_DISTINTOS";
    public static final String ERROR_GET = "ERROR_GET";
    public static final String ERROR_PUT = "ERROR_PUT";
    public static final String VERIFICACION_FALLIDA = "VERIFICACION_FALLIDA";
}
