package org.son.haruharu.domain.identity.storage;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.son.haruharu.common.crypto.IdentityCrypto;
import org.springframework.stereotype.Component;

@Component
@Converter
public class CiDiCryptoConverter implements AttributeConverter<String, String> {

    private final IdentityCrypto crypto;

    public CiDiCryptoConverter(IdentityCrypto crypto) {
        this.crypto = crypto;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return crypto.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return crypto.decrypt(dbData);
    }
}
