package cl.uss.ca.ethics.auth;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public final class YamlRoleDefinitionProvider implements RoleDefinitionProvider {
    private static final Logger LOG = Logger.getLogger(YamlRoleDefinitionProvider.class.getName());
    private static final Pattern ROLE_ID = Pattern.compile("[A-Z][A-Z0-9_]*");
    private static final Pattern LABEL = Pattern.compile("[a-z][a-z0-9_.]*");
    private static final Pattern CAPABILITY = Pattern.compile("[A-Z][A-Z0-9_]*");
    private static final List<String> CATEGORIES = List.of("global", "committee", "project", "process");
    public static final String CLASSPATH = "/ethics/permissions-V4.yml";

    private final Map<String, RoleDefinition> roles;
    private final Set<String> capabilities;

    private YamlRoleDefinitionProvider(Map<String, RoleDefinition> roles, Set<String> capabilities) {
        this.roles = Map.copyOf(roles);
        this.capabilities = Set.copyOf(capabilities);
    }

    public static YamlRoleDefinitionProvider loadClasspath() {
        return parse(read(CLASSPATH));
    }

    public static YamlRoleDefinitionProvider parse(String yaml) {
        Map<String, Object> root = YamlValues.parseMap(yaml);
        if (!"4".equals(YamlValues.text(root.get("version")))) {
            throw new EthicsAuthException("ETHICS_ROLES_VERSION", "permissions-V4.yml version must be 4");
        }
        Map<String, Object> roles = YamlValues.map(root.get("roles"), "roles");
        if (roles.isEmpty()) {
            throw new EthicsAuthException("ETHICS_ROLES_EMPTY", "permissions-V4.yml has no roles");
        }
        Map<String, RoleDefinition> parsed = new LinkedHashMap<>();
        Set<String> all = new LinkedHashSet<>();
        for (Map.Entry<String, Object> entry : roles.entrySet()) {
            RoleDefinition definition = role(entry.getKey(), entry.getValue());
            if (parsed.containsKey(definition.id())) {
                throw new EthicsAuthException("ETHICS_ROLES_DUPLICATE", "Duplicate role " + definition.id());
            }
            parsed.put(definition.id(), definition);
            all.addAll(definition.capabilities());
        }
        LOG.info("Ethics role definitions loaded: " + parsed.size() + " roles, " + all.size() + " capabilities");
        return new YamlRoleDefinitionProvider(parsed, all);
    }

    @Override
    public Collection<RoleDefinition> definitions() {
        return roles.values();
    }

    @Override
    public Optional<RoleDefinition> find(String roleId) {
        return Optional.ofNullable(roles.get(roleId));
    }

    @Override
    public boolean knownCapability(String capability) {
        return capability != null && capabilities.contains(capability);
    }

    private static RoleDefinition role(String id, Object raw) {
        if (!ROLE_ID.matcher(id).matches()) {
            throw new EthicsAuthException("ETHICS_ROLE_ID", "Invalid role id " + id);
        }
        Map<String, Object> body = YamlValues.map(raw, id);
        String label = YamlValues.text(body.get("id_label"));
        if (label == null || !LABEL.matcher(label).matches()) {
            throw new EthicsAuthException("ETHICS_ROLE_LABEL", id + " needs an id_label key, not a translated label");
        }
        Map<String, Object> groups = YamlValues.map(body.get("capabilities"), id + ".capabilities");
        for (String key : groups.keySet()) {
            if (!CATEGORIES.contains(key)) {
                throw new EthicsAuthException("ETHICS_ROLE_CATEGORY", id + " has unknown capability category " + key);
            }
        }
        List<String> global = capabilities(id, "global", groups.get("global"));
        List<String> committee = capabilities(id, "committee", groups.get("committee"));
        List<String> project = capabilities(id, "project", groups.get("project"));
        List<String> process = capabilities(id, "process", groups.get("process"));
        if (global.isEmpty() && committee.isEmpty() && project.isEmpty() && process.isEmpty()) {
            throw new EthicsAuthException("ETHICS_ROLE_CAPABILITIES", id + " has no capabilities");
        }
        return new RoleDefinition(id, label, global, committee, project, process);
    }

    private static List<String> capabilities(String roleId, String category, Object raw) {
        if (raw == null) {
            throw new EthicsAuthException("ETHICS_ROLE_CATEGORY", roleId + " is missing capabilities." + category);
        }
        List<String> values = new ArrayList<>();
        for (Object item : YamlValues.list(raw, roleId + "." + category)) {
            String capability = YamlValues.text(item);
            if (capability == null || !CAPABILITY.matcher(capability).matches()) {
                throw new EthicsAuthException("ETHICS_CAPABILITY", roleId + " has an invalid capability in " + category);
            }
            if (values.contains(capability)) {
                throw new EthicsAuthException("ETHICS_CAPABILITY_DUPLICATE", roleId + " repeats " + capability);
            }
            values.add(capability);
        }
        return List.copyOf(values);
    }

    static String read(String classpath) {
        try (InputStream input = YamlRoleDefinitionProvider.class.getResourceAsStream(classpath)) {
            if (input == null) {
                throw new EthicsAuthException("ETHICS_YAML_MISSING", "Missing classpath resource " + classpath);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new EthicsAuthException("ETHICS_YAML_READ", "Could not read " + classpath);
        }
    }
}
