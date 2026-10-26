package cl.uss.ca.scopus;

public record ResultadoCorreccion(
        CorreccionScopus correccion,
        String identifierPureId,
        String accion,
        Integer httpGet,
        Integer httpPut,
        String mensaje
) {
    public static final String DRY_RUN_ELIMINARIA = "DRY_RUN_ELIMINARIA";
    public static final String ELIMINADO = "ELIMINADO";
    public static final String NO_ENCONTRADO_EN_PURE = "NO_ENCONTRADO_EN_PURE";
    public static final String AMBIGUO = "AMBIGUO";
    public static final String VALIDACION_FALLIDA = "VALIDACION_FALLIDA";
    public static final String ERROR_GET = "ERROR_GET";
    public static final String ERROR_PUT = "ERROR_PUT";
}
