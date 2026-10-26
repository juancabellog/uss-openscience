package cl.uss.ca.pure;

import cl.uss.ca.pure.json.IdentifierItem;

public class PersonIdentifierMapper {
    static final int DISCRIMINATOR_MAX_LENGTH = 100;
    static final int TYPE_MAX_LENGTH = 100;
    static final int VALUE_MAX_LENGTH = 255;

    public PersonIdentifier mapear(IdentifierItem item) {
        if (item == null) {
            return null;
        }
        String discriminator = LocalizedTexts.trimToNull(item.getTypeDiscriminator());
        String value = valor(item);
        if (discriminator == null || value == null) {
            return null;
        }
        PersonIdentifier identifier = new PersonIdentifier();
        identifier.setPureId(item.getPureId());
        identifier.setTypeDiscriminator(truncar(discriminator, DISCRIMINATOR_MAX_LENGTH));
        identifier.setIdentifierType(truncar(
                LocalizedTexts.obtenerTextoPreferido(item.getType()), TYPE_MAX_LENGTH));
        identifier.setValue(truncar(value, VALUE_MAX_LENGTH));
        return identifier;
    }

    public String valor(IdentifierItem item) {
        if (item == null) {
            return null;
        }
        String value = LocalizedTexts.trimToNull(item.getValue());
        if (value != null) {
            return value;
        }
        return LocalizedTexts.trimToNull(item.getId());
    }

    public String typeDiscriminator(IdentifierItem item) {
        return item == null ? null : LocalizedTexts.trimToNull(item.getTypeDiscriminator());
    }

    private static String truncar(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
