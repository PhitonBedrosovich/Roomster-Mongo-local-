package com.example.chat.frontend.detection;

/**
 * Представляет одно обнаружение чувствительных данных в тексте.
 */
public class Detection {
    private final SensitiveDataType type;
    private final String matchedText; // Маскированная версия для показа пользователю
    private final String originalText; // Оригинальный текст (для внутреннего использования)
    private final int position; // Позиция в тексте
    
    public Detection(SensitiveDataType type, String matchedText, String originalText, int position) {
        this.type = type;
        this.matchedText = matchedText;
        this.originalText = originalText;
        this.position = position;
    }
    
    public SensitiveDataType getType() {
        return type;
    }
    
    public String getMatchedText() {
        return matchedText;
    }
    
    public String getOriginalText() {
        return originalText;
    }
    
    public int getPosition() {
        return position;
    }
}
