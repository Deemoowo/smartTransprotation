package org.example.smarttransportation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日期时间解析服务
 * 用于从用户自然语言输入中提取日期时间信息
 */
@Service
public class DateTimeParserService {
    
    private static final Logger logger = LoggerFactory.getLogger(DateTimeParserService.class);
    
    // 常见日期格式
    private static final List<DateTimeFormatter> DATE_FORMATTERS = Arrays.asList(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("MM-dd-yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy")
    );
    
    // 日期正则表达式模式
    private static final List<Pattern> DATE_PATTERNS = Arrays.asList(
        Pattern.compile("(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})"),  // yyyy-MM-dd 或 yyyy/MM/dd
        Pattern.compile("(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4})"),  // MM-dd-yyyy 或 MM/dd/yyyy
        Pattern.compile("(\\d{1,2})月(\\d{1,2})日"),              // 中文格式：2月10日
        Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日")     // 中文格式：2024年2月10日
    );
    
    // 相对时间关键词
    private static final Pattern TODAY_PATTERN = Pattern.compile("今天|today", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOMORROW_PATTERN = Pattern.compile("明天|tomorrow", Pattern.CASE_INSENSITIVE);
    private static final Pattern YESTERDAY_PATTERN = Pattern.compile("昨天|yesterday", Pattern.CASE_INSENSITIVE);
    
    /**
     * 从用户输入中解析日期时间
     * @param userInput 用户输入文本
     * @return 解析出的日期时间，如果无法解析则返回null
     */
    public LocalDateTime parseDateTime(String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return null;
        }
        
        String input = userInput.toLowerCase().trim();
        
        // 处理相对时间
        LocalDateTime relativeDateTime = parseRelativeDateTime(input);
        if (relativeDateTime != null) {
            return relativeDateTime;
        }
        
        // 处理绝对日期
        LocalDate absoluteDate = parseAbsoluteDate(input);
        if (absoluteDate != null) {
            return absoluteDate.atStartOfDay();
        }
        
        logger.debug("无法从用户输入中解析出日期时间: {}", userInput);
        return null;
    }
    
    /**
     * 解析相对时间（今天、明天、昨天）
     */
    private LocalDateTime parseRelativeDateTime(String input) {
        LocalDate today = LocalDate.now();
        
        if (TODAY_PATTERN.matcher(input).find()) {
            return today.atStartOfDay();
        }
        
        if (TOMORROW_PATTERN.matcher(input).find()) {
            return today.plusDays(1).atStartOfDay();
        }
        
        if (YESTERDAY_PATTERN.matcher(input).find()) {
            return today.minusDays(1).atStartOfDay();
        }
        
        return null;
    }
    
    /**
     * 解析绝对日期
     */
    private LocalDate parseAbsoluteDate(String input) {
        // 尝试正则表达式匹配
        for (Pattern pattern : DATE_PATTERNS) {
            Matcher matcher = pattern.matcher(input);
            if (matcher.find()) {
                LocalDate date = extractDateFromMatcher(matcher, pattern);
                if (date != null) {
                    return date;
                }
            }
        }
        
        // 尝试标准格式解析
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                // 提取可能的日期字符串
                String[] words = input.split("\\s+");
                for (String word : words) {
                    if (word.matches("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}") || 
                        word.matches("\\d{1,2}[-/]\\d{1,2}[-/]\\d{4}")) {
                        return LocalDate.parse(word, formatter);
                    }
                }
            } catch (DateTimeParseException e) {
                // 继续尝试下一个格式
            }
        }
        
        return null;
    }
    
    /**
     * 从正则匹配结果中提取日期
     */
    private LocalDate extractDateFromMatcher(Matcher matcher, Pattern pattern) {
        try {
            String patternStr = pattern.pattern();
            
            if (patternStr.contains("年") && patternStr.contains("月") && patternStr.contains("日")) {
                // 中文格式：2024年2月10日
                if (matcher.groupCount() >= 3) {
                    int year = Integer.parseInt(matcher.group(1));
                    int month = Integer.parseInt(matcher.group(2));
                    int day = Integer.parseInt(matcher.group(3));
                    return LocalDate.of(year, month, day);
                }
            } else if (patternStr.contains("月") && patternStr.contains("日")) {
                // 中文格式：2月10日（使用当前年份）
                if (matcher.groupCount() >= 2) {
                    int year = LocalDate.now().getYear();
                    int month = Integer.parseInt(matcher.group(1));
                    int day = Integer.parseInt(matcher.group(2));
                    return LocalDate.of(year, month, day);
                }
            } else if (patternStr.startsWith("(\\\\d{4})")) {
                // yyyy-MM-dd 格式
                if (matcher.groupCount() >= 3) {
                    int year = Integer.parseInt(matcher.group(1));
                    int month = Integer.parseInt(matcher.group(2));
                    int day = Integer.parseInt(matcher.group(3));
                    return LocalDate.of(year, month, day);
                }
            } else {
                // MM-dd-yyyy 格式
                if (matcher.groupCount() >= 3) {
                    int month = Integer.parseInt(matcher.group(1));
                    int day = Integer.parseInt(matcher.group(2));
                    int year = Integer.parseInt(matcher.group(3));
                    return LocalDate.of(year, month, day);
                }
            }
        } catch (Exception e) {
            logger.warn("解析日期时出错: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 检查日期是否在数据库数据范围内（2024年2月）
     */
    public boolean isDateInDatabaseRange(LocalDateTime dateTime) {
        if (dateTime == null) {
            return false;
        }
        
        LocalDate date = dateTime.toLocalDate();
        LocalDate startDate = LocalDate.of(2024, 2, 1);
        LocalDate endDate = LocalDate.of(2024, 2, 29);
        
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }
    
    /**
     * 判断是否需要使用网络搜索获取数据
     */
    public boolean shouldUseNetworkSearch(LocalDateTime dateTime) {
        if (dateTime == null) {
            // 用户未指定日期，使用网络搜索获取最新数据
            return true;
        }
        
        // 如果指定日期不在数据库范围内，使用网络搜索
        return !isDateInDatabaseRange(dateTime);
    }
}
