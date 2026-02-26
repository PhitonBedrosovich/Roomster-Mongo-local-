package com.example.chat.frontend.detection;

/**
 * Типы чувствительных данных с уровнями серьезности.
 */
public enum SensitiveDataType {
    PASSPORT("Паспортные данные", Severity.HIGH),
    INN("ИНН", Severity.MEDIUM),
    SNILS("СНИЛС", Severity.HIGH),
    CREDIT_CARD("Банковская карта", Severity.HIGH),
    CVV("CVV/CVC код", Severity.CRITICAL),
    EMAIL("Email адрес", Severity.LOW),
    PHONE("Номер телефона", Severity.MEDIUM),
    PASSWORD("Пароль", Severity.CRITICAL),
    CRYPTO_WALLET("Криптокошелек", Severity.HIGH),
    API_TOKEN("API токен", Severity.CRITICAL);
    
    private final String displayName;
    private final Severity severity;
    
    SensitiveDataType(String displayName, Severity severity) {
        this.displayName = displayName;
        this.severity = severity;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public Severity getSeverity() {
        return severity;
    }
}
