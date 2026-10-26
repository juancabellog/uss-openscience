package cl.uss.ca.directory;

import cl.uss.ca.api.dto.DirectoryImportResultDto;
import cl.uss.ca.api.dto.DirectoryIssueDto;
import cl.uss.ca.api.dto.DirectoryPersonDetailDto;
import cl.uss.ca.api.dto.DirectoryPersonSummaryDto;

import java.lang.reflect.Field;
import java.util.Locale;

public final class DirectorySensitiveSurfaceTest {
    private DirectorySensitiveSurfaceTest() {
    }

    public static void main(String[] args) {
        assertNoRut(DirectoryPersonSummaryDto.class);
        assertNoRut(DirectoryPersonDetailDto.class);
        assertNoRut(DirectoryImportResultDto.class);
        assertNoRut(DirectoryIssueDto.class);
        System.out.println("DirectorySensitiveSurfaceTest OK");
    }

    private static void assertNoRut(Class<?> type) {
        for (Field field : type.getFields()) {
            String name = field.getName().toLowerCase(Locale.ROOT);
            if (name.contains("rut") || name.contains("hmac") || name.contains("pepper") || name.contains("aes")) {
                throw new AssertionError(type.getSimpleName() + " exposes " + field.getName());
            }
        }
    }
}
