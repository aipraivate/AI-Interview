package com.interview.platform.question;

import com.interview.platform.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
class JdService {
    private static final Set<String> INJECTION_MARKERS = Set.of("ignore previous", "忽略之前", "system prompt",
            "系统提示词", "developer message", "越过安全", "输出密钥", "api_key");
    Analysis analyze(String text) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (normalized.length() < 20 || normalized.length() > 12000) {
            throw new BusinessException("INVALID_JD", "JD 长度需在 20 到 12000 字之间", HttpStatus.BAD_REQUEST);
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (INJECTION_MARKERS.stream().anyMatch(lower::contains)) {
            throw new BusinessException("JD_PROMPT_INJECTION", "JD 包含疑似提示注入内容，请移除后重试",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String family = lower.matches("(?s).*(java|spring|微服务|后端|服务端).*") ? "JAVA_BACKEND"
                : lower.matches("(?s).*(vue|react|前端|typescript|javascript).*") ? "FRONTEND"
                : "PRODUCT";
        List<String> skills = new ArrayList<>();
        for (String keyword : List.of("Java", "Spring", "MySQL", "Redis", "Vue", "React", "TypeScript",
                "用户研究", "需求分析", "数据分析", "项目管理", "AI", "SaaS")) {
            if (lower.contains(keyword.toLowerCase(Locale.ROOT))) skills.add(keyword);
        }
        List<String> clauses = java.util.Arrays.stream(normalized.split("[；;。\\n]"))
                .map(String::trim).filter(value -> value.length() >= 6).limit(8).toList();
        String title = family.equals("JAVA_BACKEND") ? "Java后端工程师"
                : family.equals("FRONTEND") ? "前端工程师" : "产品经理";
        return new Analysis(title, family, clauses.stream().limit(4).toList(), skills,
                clauses.stream().skip(4).limit(4).toList(), skills.isEmpty() ? 0.62 : 0.86,
                "jd-parser-v1", normalized);
    }
    record Analysis(String positionTitle, String roleFamily, List<String> responsibilities,
                    List<String> coreSkills, List<String> requirements, double confidence,
                    String parserVersion, String normalizedText) {}
    record Command(String jdText) {}
}
