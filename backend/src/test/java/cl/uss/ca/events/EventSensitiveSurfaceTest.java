package cl.uss.ca.events;

import cl.uss.ca.api.dto.EventPublicDetailDto;
import cl.uss.ca.api.dto.EventRegistrationDto;
import cl.uss.ca.api.dto.EventRegistrationProfileDto;
import cl.uss.ca.api.dto.EventVerificationPreviewDto;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class EventSensitiveSurfaceTest {
    private EventSensitiveSurfaceTest() {
    }

    public static void main(String[] args) {
        assertNoSensitive(EventPublicDetailDto.class);
        assertNoSensitive(EventRegistrationProfileDto.class);
        assertNoSensitive(EventRegistrationDto.class);
        assertNoSensitive(EventVerificationPreviewDto.class);
        System.out.println("EventSensitiveSurfaceTest OK");
    }

    private static void assertNoSensitive(Class<?> type) {
        Set<String> names = Arrays.stream(type.getFields())
                .map(Field::getName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        assert !names.contains("rut");
        assert !names.contains("rutencrypted");
        assert !names.contains("ruthmac");
        assert !names.contains("password");
        assert !names.contains("tokenhash");
    }
}
