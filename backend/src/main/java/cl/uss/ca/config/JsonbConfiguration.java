package cl.uss.ca.config;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;

@Provider
public class JsonbConfiguration implements ContextResolver<Jsonb> {
    private final Jsonb jsonb = JsonbBuilder.newBuilder()
            .withConfig(new JsonbConfig().withNullValues(true))
            .build();

    @Override
    public Jsonb getContext(Class<?> type) {
        return jsonb;
    }
}
