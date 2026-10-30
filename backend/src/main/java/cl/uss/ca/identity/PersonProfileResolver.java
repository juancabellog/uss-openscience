package cl.uss.ca.identity;

/**
 * Perfil efectivo.
 * Si la persona está vinculada a PURE, el valor efectivo es el de pure_person.
 * Las columnas locales no actúan como override.
 * Si no está vinculada, el valor efectivo es el de Person.
 */
public final class PersonProfileResolver {
    private PersonProfileResolver() {
    }

    public static EffectivePersonProfile resolve(SharedPerson person, PurePersonView pure) {
        if (person == null) {
            throw new IllegalArgumentException("person is required");
        }
        if (!person.linkedToPure()) {
            return local(person);
        }
        PurePersonView source = pure != null && pure.id() == person.purePersonId() ? pure : null;
        return new EffectivePersonProfile(
                person.id(),
                person.purePersonId(),
                PersonSource.PURE,
                source == null ? null : present(source.firstName()),
                source == null ? null : present(source.lastName()),
                source == null ? null : present(source.email()),
                null,
                source == null ? null : present(source.campus()),
                source == null ? null : present(source.faculty()),
                source == null ? null : present(source.unit()),
                source == null ? null : present(source.position())
        );
    }

    private static EffectivePersonProfile local(SharedPerson person) {
        return new EffectivePersonProfile(
                person.id(),
                null,
                PersonSource.LOCAL,
                person.firstName(),
                person.lastName(),
                person.email(),
                person.institution(),
                person.campus(),
                person.faculty(),
                person.unit(),
                person.position()
        );
    }

    private static String present(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
