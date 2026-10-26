package com.sisgic.service;

import com.sisgic.entity.RRHH;
import com.sisgic.entity.TipoRRHH;
import com.sisgic.repository.RRHHRepository;
import com.sisgic.repository.TipoRRHHRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;

/**
 * Servicio para hacer matching de nombres entre autores de DOI/API externa
 * y los investigadores (RRHH) de la base de datos.
 * 
 * Carga todos los RRHH en memoria para realizar búsquedas rápidas con
 * algoritmos de normalización y matching de tokens.
 */
@Service
public class ResearcherMatchingService {
    
    @Autowired
    private RRHHRepository rrhhRepository;
    
    @Autowired
    private TipoRRHHRepository tipoRRHHRepository;

    @Autowired
    private RrhhTipoHistoryService rrhhTipoHistoryService;
    
    // Cache en memoria de todos los RRHH indexados por nombre normalizado
    private HashMap<String, HashMap<String, Object>> hsRRHH;
    
    // Cache indexado por ORCID
    private HashMap<String, HashMap<String, Object>> hsRRHHByOrcid;
    
    // Lista de nombres normalizados para búsqueda
    private ArrayList<String> namesList;
    
    /**
     * Enum que representa el estatus del resultado del matching
     */
    public enum Estatus {
        UNICA,              // Se encontró una única coincidencia
        MAS_DE_UNA,         // Se encontraron múltiples coincidencias
        SIN_COINCIDENCIAS   // No se encontraron coincidencias
    }
    
    /**
     * Clase que representa el resultado de un matching
     */
    public static class Resultado {
        public Estatus estatus;
        public final String elegido;  // Si UNICA: nombre normalizado del elegido
        public final String detalle;  // Si MAS_DE_UNA: candidatos separados por "/"
        
        private final HashMap<String, HashMap<String, Object>> hsRRHH;
        
        public Resultado(Estatus estatus, String elegido, String detalle, 
                        HashMap<String, HashMap<String, Object>> hsRRHH) {
            this.estatus = estatus;
            this.elegido = elegido;
            this.detalle = detalle;
            this.hsRRHH = hsRRHH;
        }
        
        public Long getIdRRHH() {
            if (elegido == null || hsRRHH == null) return null;
            HashMap<String, Object> hs = hsRRHH.get(elegido);
            return hs == null ? null : (Long) hs.get("id");
        }
        
        public String getName() {
            if (elegido == null || hsRRHH == null) return null;
            HashMap<String, Object> hs = hsRRHH.get(elegido);
            return hs == null ? null : (String) hs.get("name");
        }
        
        public String getOrcid() {
            if (elegido == null || hsRRHH == null) return null;
            HashMap<String, Object> hs = hsRRHH.get(elegido);
            return hs == null ? null : (String) hs.get("orcid");
        }
        
        public String getIdRecurso() {
            if (elegido == null || hsRRHH == null) return null;
            HashMap<String, Object> hs = hsRRHH.get(elegido);
            return hs == null ? null : (String) hs.get("idRecurso");
        }
        
        public Long getIdTipoRRHH() {
            if (elegido == null || hsRRHH == null) return null;
            HashMap<String, Object> hs = hsRRHH.get(elegido);
            return hs == null ? null : (Long) hs.get("idTipoRRHH");
        }
        
        @Override
        public String toString() {
            return "estatus=" + estatus 
                    + (elegido != null ? " | elegido=" + elegido : "")
                    + (detalle != null ? " | detalle=" + detalle : "");
        }
    }
    
    /**
     * Carga todos los RRHH en memoria al inicializar el servicio
     */
    @PostConstruct
    public void loadRRHH() {
        hsRRHH = new HashMap<>();
        namesList = new ArrayList<>();
        hsRRHHByOrcid = new HashMap<>();
        
        try {
            List<RRHH> allRRHH = rrhhRepository.findAll();
            
            for (RRHH rrhh : allRRHH) {
                String name = rrhh.getFullname();
                if (name == null || name.trim().isEmpty()) {
                    continue; // Saltar si no tiene nombre
                }
                
                String normalizedName = normalize(name);
                namesList.add(normalizedName);
                
                HashMap<String, Object> hs = new HashMap<>();
                hs.put("id", rrhh.getId());
                hs.put("orcid", rrhh.getOrcid());
                hs.put("idTipoRRHH", rrhh.getTipoRRHH() != null ? rrhh.getTipoRRHH().getId() : null);
                hs.put("idRecurso", rrhh.getIdRecurso());
                hs.put("name", name); // Nombre original sin normalizar
                
                // Indexar por nombre normalizado
                hsRRHH.put(normalizedName, hs);
                
                // Indexar por ORCID si existe
                if (rrhh.getOrcid() != null && !rrhh.getOrcid().trim().isEmpty()) {
                    hsRRHHByOrcid.put(rrhh.getOrcid(), hs);
                }
            }
            
            System.out.println("ResearcherMatchingService: Cargados " + namesList.size() + " investigadores en memoria");
        } catch (Exception e) {
            System.err.println("Error cargando RRHH en ResearcherMatchingService: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Obtiene un investigador por su ID
     */
    public HashMap<String, Object> get(Long idRRHH) {
        if (hsRRHH == null || idRRHH == null) return null;
        
        for (String key : namesList) {
            HashMap<String, Object> hs = hsRRHH.get(key);
            if (hs != null && hs.get("id").equals(idRRHH)) {
                return hs;
            }
        }
        return null;
    }
    
    /**
     * Obtiene un investigador por su ORCID
     */
    public Long getRRHHByOrcid(String orcid) {
        if (orcid == null || hsRRHHByOrcid == null) return null;
        HashMap<String, Object> hs = hsRRHHByOrcid.get(orcid);
        return hs == null ? null : (Long) hs.get("id");
    }
    
    /**
     * Devuelve true si candidatoBD cumple las reglas de coincidencia contra planilla.
     * 
     * Reglas:
     *  - Token de 1 letra en planilla: hace match con cualquier token del candidato 
     *    que comience con esa letra (o sea esa letra).
     *  - Token de 2+ letras en planilla: debe existir token EXACTO en candidato 
     *    (normalizado) o un token de 1 letra igual a su inicial.
     *  - Se acepta candidato con tokens extra.
     *  - (Fallback) También se acepta candidato más corto contenido en planilla si 
     *    hay solapamiento fuerte.
     */
    public boolean coinciden(String planilla, String candidatoBD) {
        List<String> q = tokens(planilla);   // query: planilla
        List<String> c = tokens(candidatoBD); // candidate: BD
        
        // Regla principal: todos los tokens de la planilla contenidos en el candidato
        if (matchAllQueryTokens(q, c)) return true;
        
        // Regla existente (fallback): aceptar candidato más corto contenido en planilla,
        // pero con solapamiento fuerte para evitar falsos positivos.
        if (matchAllQueryTokens(c, q) && tieneSolapamientoFuerte(q, c)) return true;
        
        return false;
    }
    
    /**
     * Devuelve todos los elementos de listaBD que coinciden con planilla según las reglas.
     */
    public List<String> buscarCoincidencias(String planilla, List<String> listaBD) {
        List<String> out = new ArrayList<>();
        for (String cand : listaBD) {
            if (coinciden(planilla, cand)) {
                out.add(cand);
            }
        }
        return out;
    }
    
    /**
     * Verifica si dos nombres son casi iguales (mismos tokens, posiblemente en diferente orden)
     */
    public boolean esCasiIgual(String planilla, String item) {
        String[] token1 = planilla.split("[ ]+");
        ArrayList<String> tokens = new ArrayList<>(Arrays.asList(item.split("[ ]+")));
        
        for (String t1 : token1) {
            if (!tokens.contains(t1)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Encuentra el mejor match para un nombre de planilla.
     * 
     * Devuelve UNICA/MAS_DE_UNA/SIN_COINCIDENCIAS y, si aplica, elegido o detalle.
     */
    public Resultado encontrarMejor(String planilla) {
        if (planilla == null || planilla.trim().isEmpty()) {
            return new Resultado(Estatus.SIN_COINCIDENCIAS, null, null, hsRRHH);
        }
        
        planilla = normalize(planilla.replaceAll("[?]", "")
                                     .replaceAll("[,]", " ")
                                     .replaceAll("/", " "));
        
        List<String> matches = buscarCoincidencias(planilla, namesList);
        
        if (matches.isEmpty()) {
            return new Resultado(Estatus.SIN_COINCIDENCIAS, null, null, hsRRHH);
        }
        
        if (matches.size() == 1) {
            return new Resultado(Estatus.UNICA, matches.get(0), null, hsRRHH);
        }
        
        // Varias coincidencias: detalla en el mismo orden de la BD, separados por "/"
        StringBuilder sb = new StringBuilder();
        String elegido = null;
        
        for (int i = 0; i < matches.size(); i++) {
            String item = matches.get(i);
            if (i > 0) sb.append("/");
            sb.append(item);
            
            if (planilla.equals(item) || esCasiIgual(planilla, item)) {
                elegido = item;
            }
        }
        
        return new Resultado(Estatus.MAS_DE_UNA, elegido, sb.toString(), hsRRHH);
    }
    
    /**
     * Recarga los datos de RRHH en memoria.
     * Útil si se han agregado nuevos investigadores y se necesita actualizar el cache.
     */
    public void reloadRRHH() {
        loadRRHH();
    }
    
    /**
     * Crea un nuevo investigador (RRHH) automáticamente cuando no se encuentra en la BD.
     * Adaptado del método creaRRHH del código original.
     * 
     * @param name Nombre completo del investigador
     * @param orcid ORCID del investigador (puede ser null)
     * @return ID del investigador creado
     */
    @Transactional
    public Long createResearcher(String name, String orcid) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        
        // Limpiar nombre (remover guiones y puntos)
        name = name.replaceAll("-", " ").replaceAll("[.]", " ");
        
        // Normalizar ORCID (extraer solo el ID si viene con URL)
        String normalizedOrcid = null;
        if (orcid != null && !orcid.trim().isEmpty()) {
            normalizedOrcid = orcid;
            if (normalizedOrcid.contains("/")) {
                normalizedOrcid = normalizedOrcid.substring(normalizedOrcid.lastIndexOf('/') + 1);
            }
            // Asegurar formato correcto (19 caracteres)
            if (normalizedOrcid.length() > 19) {
                normalizedOrcid = normalizedOrcid.substring(0, 19);
            }
        }
        
        // Parsear nombre en partes según la lógica especificada
        String[] tokens = name.split("[ ]+");
        
        String pn = capitalize(tokens.length > 0 ? tokens[0] : "");
        String sn = "";
        String pa = "";
        String sa = "";
        
        if (tokens.length == 2) {
            pa = capitalize(tokens[1]);
        } else if (tokens.length == 3) {
            if (tokens[1].length() == 1) {
                sn = capitalize(tokens[1]);
                pa = capitalize(tokens[2]);
            } else {
                pa = capitalize(tokens[1]);
                sa = capitalize(tokens[2]);
            }
        } else if (tokens.length == 4) {
            sn = capitalize(tokens[1]);
            pa = capitalize(tokens[2]);
            sa = capitalize(tokens[3]);
        } else if (tokens.length == 5) {
            sn = capitalize(tokens[1]);
            pa = capitalize(tokens[2]);
            sa = capitalize(tokens[3]) + " " + capitalize(tokens[4]);
        }
        
        // Obtener TipoRRHH con ID 32, o el primero disponible si no existe
        TipoRRHH tipoRRHH = tipoRRHHRepository.findById(32L).orElse(null);
        if (tipoRRHH == null) {
            // Si no existe el ID 32, usar el primero disponible
            List<TipoRRHH> tipos = tipoRRHHRepository.findAll();
            if (!tipos.isEmpty()) {
                tipoRRHH = tipos.get(0);
            } else {
                // Si no hay tipos, crear uno por defecto
                tipoRRHH = new TipoRRHH("Researcher");
                tipoRRHH = tipoRRHHRepository.save(tipoRRHH);
            }
        }
        
        // Crear nuevo RRHH con los campos individuales
        RRHH nuevoRRHH = new RRHH();
        nuevoRRHH.setPrimerNombre(pn);
        nuevoRRHH.setSegundoNombre(sn.isEmpty() ? null : sn);
        nuevoRRHH.setPrimerApellido(pa.isEmpty() ? null : pa);
        nuevoRRHH.setSegundoApellido(sa.isEmpty() ? null : sa);
        nuevoRRHH.setTipoRRHH(tipoRRHH);
        nuevoRRHH.setOrcid(normalizedOrcid);
        // Los demás campos se dejan null (idRecurso, email, etc.)
        
        // Guardar
        nuevoRRHH = rrhhRepository.save(nuevoRRHH);

        if (tipoRRHH != null && tipoRRHH.getId() != null) {
            rrhhTipoHistoryService.recordInitialTipo(nuevoRRHH.getId(), tipoRRHH.getId());
        }
        
        // Recargar cache para incluir el nuevo investigador
        reloadRRHH();
        
        // El fullname se calculará automáticamente desde la función MySQL
        System.out.println("Creado nuevo investigador: ID=" + nuevoRRHH.getId() + 
                         ", primerNombre=" + pn + 
                         ", segundoNombre=" + sn + 
                         ", primerApellido=" + pa + 
                         ", segundoApellido=" + sa);
        
        return nuevoRRHH.getId();
    }
    
    /**
     * Capitaliza una palabra (primera letra mayúscula, resto minúscula)
     * Según la especificación: si tiene 1 carácter, retorna el carácter; 
     * si tiene más, capitaliza la primera letra y el resto en minúscula
     */
    private static String capitalize(String word) {
        if (word == null || word.isEmpty()) {
            return "";
        }
        if (word.length() == 1) {
            return word;
        }
        return Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase();
    }
    
    // ================== Métodos privados de matching ==================
    
    /**
     * Verifica que todos los tokens de la query estén presentes en el candidato
     */
    private boolean matchAllQueryTokens(List<String> queryTokens, List<String> candTokens) {
        if (queryTokens.isEmpty()) return false; // no buscamos vacío
        
        boolean[] used = new boolean[candTokens.size()];
        
        for (String q : queryTokens) {
            int j = findMatchForQueryToken(q, candTokens, used);
            if (j == -1) return false; // algún token de la planilla no encontró match
            used[j] = true; // evita reutilizar el mismo token del candidato
        }
        
        return true;
    }
    
    /**
     * Regla de matching para un token de planilla 'q' contra los tokens del candidato:
     * - Si q tiene 1 letra: buscar c que empiece con esa letra (o sea esa letra).
     * - Si q tiene 2+ letras: buscar c EXACTO (ignorando acentos/case) O c de 1 letra 
     *   igual a la inicial de q.
     * No se acepta prefijo cuando c tiene 2+ letras (ej: "daniel" ≠ "daniela").
     */
    private int findMatchForQueryToken(String q, List<String> candTokens, boolean[] used) {
        boolean qEsInicial = (q.length() == 1);
        char qIni = q.charAt(0);
        
        for (int i = 0; i < candTokens.size(); i++) {
            if (used[i]) continue;
            String c = candTokens.get(i);
            
            if (qEsInicial) {
                // q = "d" → c debe empezar con 'd' (o ser "d")
                if (!c.isEmpty() && c.charAt(0) == qIni) {
                    return i;
                }
            } else {
                // q largo (2+): c debe ser EXACTO (=q) o una inicial igual a la inicial de q
                if (c.length() == 1) {
                    if (c.charAt(0) == qIni) {
                        return i; // c es inicial que representa a q
                    }
                } else {
                    if (c.equals(q)) {
                        return i; // exacto (sin acentos/case por normalización)
                    }
                    // ¡OJO! NO aceptar prefijos: "daniela" NO debe matchear "daniel"
                }
            }
        }
        return -1;
    }
    
    /**
     * Requiere al menos un token NO-inicial (len>=2) del candidato que coincida EXACTO
     * con algún token de la planilla. Y, si la planilla tiene 2+ tokens, idealmente que
     * ese match esté entre los "apellidos" (últimos 2 tokens) de la planilla.
     */
    private boolean tieneSolapamientoFuerte(List<String> planilla, List<String> candidato) {
        // Conjunto de tokens de planilla
        Set<String> setP = new HashSet<>(planilla);
        
        // 1) Al menos un match exacto (no inicial) entre candidato y planilla
        boolean hayMatchFuerte = false;
        for (String tc : candidato) {
            if (tc.length() >= 2 && setP.contains(tc)) {
                hayMatchFuerte = true;
                break;
            }
        }
        if (!hayMatchFuerte) return false;
        
        // 2) Si planilla tiene 2+ tokens, intentamos que alguno de los matches esté en "apellidos"
        if (planilla.size() >= 2) {
            List<String> apellidos = apellidosDe(planilla);
            Set<String> setApe = new HashSet<>(apellidos);
            for (String tc : candidato) {
                if (tc.length() >= 2 && setApe.contains(tc)) {
                    return true; // hay match fuerte en zona de apellidos
                }
            }
            // Si no hubo apellido pero sí hubo match fuerte, igualmente aceptamos
            return true;
        }
        
        return true;
    }
    
    /**
     * Extrae los apellidos de una lista de tokens (últimos 1-2 tokens)
     */
    private List<String> apellidosDe(List<String> planillaTokens) {
        List<String> ap = new ArrayList<>();
        if (planillaTokens.isEmpty()) return ap;
        
        if (planillaTokens.size() == 1) {
            ap.add(planillaTokens.get(0));
        } else if (planillaTokens.size() == 2) {
            ap.add(planillaTokens.get(1));
        } else {
            ap.add(planillaTokens.get(planillaTokens.size() - 2));
            ap.add(planillaTokens.get(planillaTokens.size() - 1));
        }
        return ap;
    }
    
    // ================== Normalización ==================
    
    /**
     * Convierte un string en lista de tokens normalizados
     */
    protected List<String> tokens(String s) {
        return tokens(s, true);
    }
    
    /**
     * Convierte un string en lista de tokens, opcionalmente normalizados
     */
    protected List<String> tokens(String s, boolean normaliza) {
        String t;
        if (normaliza) {
            t = normalize(s);
        } else {
            t = s;
        }
        
        if (t.isEmpty()) return Collections.emptyList();
        
        String[] parts = t.split(" ");
        List<String> out = new ArrayList<>(parts.length);
        for (String p : parts) {
            if (!p.isEmpty()) {
                out.add(p);
            }
        }
        return out;
    }
    
    /**
     * Normaliza un string: minusculas, quita tildes/diacríticos, colapsa espacios, 
     * quita puntos/guiones.
     */
    public static String normalize(String s) {
        if (s == null) return "";
        
        String t = s.toLowerCase(Locale.ROOT);
        
        // Reemplaza variantes raras de "i" y "j" sin punto
        t = t.replace('ı', 'i')  // dotless i (U+0131)
             .replace('ȷ', 'j'); // dotless j (U+0237)
        
        // Normaliza separadores "raros": NBSP (\u00A0), thin space (\u202F), figure space (\u2007)
        // y guiones extraños: "‐" (U+2010), "-" (U+2011), "‒" (U+2012), "–" (U+2013)
        t = t.replace('.', ' ')
             .replace('-', ' ')
             .replaceAll("[‐-‒–]", " ");
        
        // Descompone acentos combinados y los elimina
        t = Normalizer.normalize(t, Normalizer.Form.NFD)
             .replaceAll("\\p{M}", ""); // elimina diacríticos
        
        // Colapsa cualquier separador Unicode a un espacio simple
        t = t.replaceAll("[\\p{Z}\\s]+", " ").trim();
        
        return t;
    }
}


