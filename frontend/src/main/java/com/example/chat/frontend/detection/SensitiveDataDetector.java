package com.example.chat.frontend.detection;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Детектор чувствительных данных в тексте.
 * Использует regex паттерны для обнаружения различных типов чувствительной информации.
 */
public class SensitiveDataDetector {
    
    // Паттерны для разных типов чувствительных данных
    private static final PatternInfo[] PATTERNS = {
        // Паспорт РФ: серия и номер (4 цифры пробел/без пробела 6 цифр)
        new PatternInfo(
            Pattern.compile("\\b(\\d{4}\\s?\\d{6})\\b"),
            SensitiveDataType.PASSPORT
        ),
        
        // ИНН РФ: 10 или 12 цифр
        new PatternInfo(
            Pattern.compile("\\b(\\d{10}|\\d{12})\\b"),
            SensitiveDataType.INN
        ),
        
        // СНИЛС: формат XXX-XXX-XXX XX или 11 цифр подряд
        new PatternInfo(
            Pattern.compile("\\b(\\d{3}-\\d{3}-\\d{3}\\s?\\d{2}|\\d{11})\\b"),
            SensitiveDataType.SNILS
        ),
        
        // Банковская карта: 16 цифр с возможными пробелами или дефисами
        new PatternInfo(
            Pattern.compile("\\b(\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4})\\b"),
            SensitiveDataType.CREDIT_CARD
        ),
        
        // CVV/CVC код: слово CVV/CVC/код и 3-4 цифры
        new PatternInfo(
            Pattern.compile("\\b(CVV|CVC|код)[\\s:]*\\d{3,4}\\b", Pattern.CASE_INSENSITIVE),
            SensitiveDataType.CVV
        ),
        
        // Email адрес
        new PatternInfo(
            Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"),
            SensitiveDataType.EMAIL
        ),
        
        // Телефон РФ: +7 или 8, затем формат номера
        new PatternInfo(
            Pattern.compile("\\b(\\+?7|8)[\\s-]?\\(?\\d{3}\\)?[\\s-]?\\d{3}[\\s-]?\\d{2}[\\s-]?\\d{2}\\b"),
            SensitiveDataType.PHONE
        ),
        
        // Пароль: слово "пароль/password/pwd" и значение после знака равенства/двоеточия
        new PatternInfo(
            Pattern.compile("\\b(пароль|password|pwd)[\\s:]*[=:][\\s]*[\\w!@#$%^&*()]{6,}\\b", Pattern.CASE_INSENSITIVE),
            SensitiveDataType.PASSWORD
        ),
        
        // Криптокошелек: Ethereum (0x...) или Bitcoin адрес
        new PatternInfo(
            Pattern.compile("\\b(0x[a-fA-F0-9]{40}|[13][a-km-zA-HJ-NP-Z1-9]{25,34})\\b"),
            SensitiveDataType.CRYPTO_WALLET
        ),
        
        // API токен: слова api_key, token, access_token и длинная строка
        new PatternInfo(
            Pattern.compile("\\b(api[_-]?key|token|access[_-]?token)[\\s:=]+[A-Za-z0-9_-]{20,}\\b", Pattern.CASE_INSENSITIVE),
            SensitiveDataType.API_TOKEN
        )
    };
    
    /**
     * Сканирует текст на наличие чувствительных данных.
     * 
     * @param text Текст для сканирования
     * @return Результат сканирования с обнаруженными данными
     */
    public ScanResult scan(String text) {
        if (text == null || text.isEmpty()) {
            return new ScanResult(new ArrayList<>());
        }
        
        List<Detection> detections = new ArrayList<>();
        
        for (PatternInfo patternInfo : PATTERNS) {
            Matcher matcher = patternInfo.pattern.matcher(text);
            while (matcher.find()) {
                String matched = matcher.group();
                String masked = maskSensitiveData(matched, patternInfo.type);
                
                detections.add(new Detection(
                    patternInfo.type,
                    masked,
                    matched,
                    matcher.start()
                ));
            }
        }
        
        return new ScanResult(detections);
    }
    
    /**
     * Маскирует чувствительные данные для безопасного показа пользователю.
     */
    private String maskSensitiveData(String text, SensitiveDataType type) {
        return switch (type) {
            case CREDIT_CARD -> maskCard(text);
            case PHONE -> maskPhone(text);
            case EMAIL -> maskEmail(text);
            case PASSPORT -> maskPassport(text);
            case INN -> maskInn(text);
            case SNILS -> maskSnils(text);
            case CRYPTO_WALLET -> maskCryptoWallet(text);
            case PASSWORD, CVV, API_TOKEN -> maskCritical(text);
            default -> maskGeneric(text);
        };
    }
    
    private String maskCard(String card) {
        // Оставляем только последние 4 цифры
        String digits = card.replaceAll("[^0-9]", "");
        if (digits.length() >= 4) {
            return "**** **** **** " + digits.substring(digits.length() - 4);
        }
        return "****";
    }
    
    private String maskPhone(String phone) {
        // Оставляем последние 4 цифры
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() >= 4) {
            return "+7 *** *** " + digits.substring(digits.length() - 4);
        }
        return "+7 *** *** **";
    }
    
    private String maskEmail(String email) {
        // Маскируем часть до @
        int atIndex = email.indexOf('@');
        if (atIndex > 0) {
            String local = email.substring(0, atIndex);
            String domain = email.substring(atIndex);
            if (local.length() > 2) {
                return local.substring(0, 2) + "***" + domain;
            }
            return "***" + domain;
        }
        return "***@***";
    }
    
    private String maskPassport(String passport) {
        // Оставляем только последние 2 цифры
        String digits = passport.replaceAll("[^0-9]", "");
        if (digits.length() >= 2) {
            return "**** ****" + digits.substring(digits.length() - 2);
        }
        return "****";
    }
    
    private String maskInn(String inn) {
        // Оставляем только последние 2 цифры
        String digits = inn.replaceAll("[^0-9]", "");
        if (digits.length() >= 2) {
            return "***" + digits.substring(digits.length() - 2);
        }
        return "***";
    }
    
    private String maskSnils(String snils) {
        // Оставляем только последние 2 цифры
        String digits = snils.replaceAll("[^0-9]", "");
        if (digits.length() >= 2) {
            return "***-***-*** " + digits.substring(digits.length() - 2);
        }
        return "***-***-*** **";
    }
    
    private String maskCryptoWallet(String wallet) {
        // Оставляем первые 6 и последние 4 символа
        if (wallet.length() > 10) {
            return wallet.substring(0, 6) + "..." + wallet.substring(wallet.length() - 4);
        }
        return "***";
    }
    
    private String maskCritical(String text) {
        // Полностью маскируем критичные данные
        return "***";
    }
    
    private String maskGeneric(String text) {
        // Общая маскировка
        if (text.length() > 4) {
            return text.substring(0, 2) + "***" + text.substring(text.length() - 2);
        }
        return "***";
    }
    
    /**
     * Вспомогательный класс для хранения паттерна и типа данных.
     */
    private static class PatternInfo {
        final Pattern pattern;
        final SensitiveDataType type;
        
        PatternInfo(Pattern pattern, SensitiveDataType type) {
            this.pattern = pattern;
            this.type = type;
        }
    }
}
