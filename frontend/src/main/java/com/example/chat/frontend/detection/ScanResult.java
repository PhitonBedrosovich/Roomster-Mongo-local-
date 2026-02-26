package com.example.chat.frontend.detection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Результат сканирования текста на наличие чувствительных данных.
 */
public class ScanResult {
    private final List<Detection> detections;
    private final boolean hasSensitiveData;
    private final Severity maxSeverity;
    
    public ScanResult(List<Detection> detections) {
        this.detections = new ArrayList<>(detections);
        this.hasSensitiveData = !detections.isEmpty();
        
        // Определяем максимальный уровень серьезности
        this.maxSeverity = detections.stream()
            .map(d -> d.getType().getSeverity())
            .max(Enum::compareTo)
            .orElse(Severity.LOW);
    }
    
    public List<Detection> getDetections() {
        return new ArrayList<>(detections);
    }
    
    public boolean hasSensitiveData() {
        return hasSensitiveData;
    }
    
    public Severity getMaxSeverity() {
        return maxSeverity;
    }
    
    /**
     * Получает сообщение-предупреждение для показа пользователю.
     */
    public String getWarningMessage() {
        if (!hasSensitiveData) {
            return null;
        }
        
        StringBuilder sb = new StringBuilder("Обнаружены чувствительные данные:\n\n");
        
        // Группируем по уровню серьезности
        Map<Severity, List<String>> bySeverity = detections.stream()
            .collect(Collectors.groupingBy(
                d -> d.getType().getSeverity(),
                Collectors.mapping(d -> d.getType().getDisplayName(), Collectors.toList())
            ));
        
        // Сортируем по серьезности (от критической к низкой)
        Severity[] severities = {Severity.CRITICAL, Severity.HIGH, Severity.MEDIUM, Severity.LOW};
        
        for (Severity severity : severities) {
            if (bySeverity.containsKey(severity)) {
                List<String> types = bySeverity.get(severity);
                String severityName = getSeverityDisplayName(severity);
                sb.append(severityName).append(": ");
                sb.append(String.join(", ", types));
                sb.append("\n");
            }
        }
        
        sb.append("\nВы уверены, что хотите отправить это сообщение?");
        
        return sb.toString();
    }
    
    private String getSeverityDisplayName(Severity severity) {
        return switch (severity) {
            case CRITICAL -> "🔴 КРИТИЧЕСКИЙ РИСК";
            case HIGH -> "🟠 ВЫСОКИЙ РИСК";
            case MEDIUM -> "🟡 СРЕДНИЙ РИСК";
            case LOW -> "🟢 НИЗКИЙ РИСК";
        };
    }
    
    /**
     * Получает количество обнаружений каждого типа.
     */
    public Map<SensitiveDataType, Long> getDetectionCounts() {
        return detections.stream()
            .collect(Collectors.groupingBy(Detection::getType, Collectors.counting()));
    }
}
