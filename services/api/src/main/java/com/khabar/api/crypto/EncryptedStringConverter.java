package com.khabar.api.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/** Put @Convert(converter = EncryptedStringConverter.class) on a String column to store it encrypted. */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final FieldEncryptor encryptor;

    public EncryptedStringConverter(FieldEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    @Override
    public String convertToDatabaseColumn(String plain) {
        return encryptor.encrypt(plain);
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        return encryptor.decrypt(stored);
    }
}
