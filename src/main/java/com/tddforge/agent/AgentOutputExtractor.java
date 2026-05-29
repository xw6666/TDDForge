package com.tddforge.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tddforge.domain.*;
import com.tddforge.opencode.OpenCodeNdjsonParser;
import com.tddforge.opencode.OpenCodeResult;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AgentOutputExtractor {

    private static final Logger log = LoggerFactory.getLogger(AgentOutputExtractor.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final Pattern JSON_PATTERN = Pattern.compile("\\{[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\\}", Pattern.DOTALL);
    private static final Pattern FILE_LIST_PATTERN = Pattern.compile(
            "(?:test files? changed|files? (?:changed|modified|created|added)):?\\s*\\n((?:\\s*[-*]?\\s*`?[^\\n]+`?\\s*\\n?)+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TEST_COMMAND_PATTERN = Pattern.compile(
            "`([^`]+)`",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CLASSIFICATION_PATTERN = Pattern.compile(
            "\\b(PASS|EXPECTED_RED|INVALID)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMIT_HASH_PATTERN = Pattern.compile(
            "(?:commit(?:\\s+hash)?|committed?)(?:\\s*[:=])?[ \\t]*`?([0-9a-f]{7,40})`?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMIT_MSG_PATTERN = Pattern.compile(
            "(?:commit(?:\\s+message)?|message)(?:\\s*[:=])?[ \\t]*[`'\"]?(.+?)[`'\"]?[ \\t]*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private static final Set<String> TEST_ISSUE_KEYWORDS = Set.of(
            "test", "tests", "testing", "coverage", "assertion", "assertions",
            "weak test", "invalid test", "test coverage", "test was weakened",
            "skipped test", "fixture", "test fixture"
    );

    public ExtractionResult<PlannerResult> extractPlannerResult(AgentRun run) {
        String rawOutput = run.output();
        String text = extractFinalText(rawOutput);

        String jsonStr = findJsonObject(text);
        if (jsonStr == null) {
            return ExtractionResult.criticalError("No valid JSON found in Planner output", rawOutput);
        }

        try {
            Map<String, Object> rawMap = MAPPER.readValue(jsonStr, Map.class);
            Map<String, Object> normalizedMap = normalizePlannerFields(rawMap);
            PlannerResult result = MAPPER.convertValue(normalizedMap, PlannerResult.class);
            return ExtractionResult.success(result, rawOutput);
        } catch (Exception e) {
            log.warn("Failed to parse Planner JSON: {}", e.getMessage());
            return ExtractionResult.criticalError("Invalid Planner JSON: " + e.getMessage(), rawOutput);
        }
    }

    public ExtractionResult<TestWriterResult> extractTestWriterResult(AgentRun run) {
        String rawOutput = run.output();
        String text = extractFinalText(rawOutput);
        List<String> warnings = new ArrayList<>();

        String classification = extractClassification(text);
        if (classification == null) {
            return ExtractionResult.criticalError("TestWriter result classification (PASS/EXPECTED_RED/INVALID) not found", rawOutput);
        }

        List<String> files = extractFiles(text);
        String testCommand = extractTestCommand(text);
        String commitHash = extractCommitHash(text);
        String summary = extractTestSummary(text);

        if (testCommand == null) {
            warnings.add("Test command not found in output");
            testCommand = "unknown";
        }
        if (summary == null) {
            warnings.add("Test summary not found in output");
            summary = "No summary provided";
        }

        TestWriterResult result = new TestWriterResult(summary, testCommand, classification, commitHash, files.isEmpty() ? null : files);

        if (warnings.isEmpty()) {
            return ExtractionResult.success(result, rawOutput);
        }
        return ExtractionResult.successWithWarnings(result, warnings, rawOutput);
    }

    public ExtractionResult<ReviewVerdict> extractReviewVerdict(AgentRun run) {
        String rawOutput = run.output();
        String text = extractFinalText(rawOutput);

        String verdictStr = extractFirstLineVerdict(text);
        if (verdictStr == null) {
            return ExtractionResult.criticalError("Review verdict (APPROVE/REQUEST_CHANGES) not found on first line", rawOutput);
        }

        try {
            ReviewVerdict verdict = ReviewVerdict.fromString(verdictStr);
            return ExtractionResult.success(verdict, rawOutput);
        } catch (Exception e) {
            return ExtractionResult.criticalError("Invalid review verdict: " + verdictStr, rawOutput);
        }
    }

    public ExtractionResult<CoderResult> extractCoderResult(AgentRun run) {
        String rawOutput = run.output();
        String text = extractFinalText(rawOutput);
        List<String> warnings = new ArrayList<>();

        String summary = extractImplementationSummary(text);
        List<String> files = extractFiles(text);
        String testCommand = extractTestCommand(text);
        String testResult = extractTestResult(text);
        String commitHash = extractCommitHash(text);

        if (summary == null) {
            warnings.add("Implementation summary not found in output");
            summary = "No summary provided";
        }
        if (testCommand == null) {
            warnings.add("Test command not found in output");
            testCommand = "unknown";
        }
        if (testResult == null) {
            warnings.add("Test result not found in output");
            testResult = "unknown";
        }

        CoderResult result = new CoderResult(summary, testCommand, testResult, commitHash, files.isEmpty() ? null : files);

        if (warnings.isEmpty()) {
            return ExtractionResult.success(result, rawOutput);
        }
        return ExtractionResult.successWithWarnings(result, warnings, rawOutput);
    }

    public ExtractionResult<ReviewerResult> extractReviewerResult(AgentRun run, String reviewerId) {
        String rawOutput = run.output();
        String text = extractFinalText(rawOutput);

        String verdictStr = extractFirstLineVerdict(text);
        if (verdictStr == null) {
            return ExtractionResult.criticalError("Reviewer verdict (APPROVE/REQUEST_CHANGES) not found on first line", rawOutput);
        }

        ReviewVerdict verdict;
        try {
            verdict = ReviewVerdict.fromString(verdictStr);
        } catch (Exception e) {
            return ExtractionResult.criticalError("Invalid reviewer verdict: " + verdictStr, rawOutput);
        }

        String feedback = extractFeedbackBody(text);
        String category = classifyReviewerFeedback(verdict, feedback);

        ReviewerResult result = new ReviewerResult(reviewerId, verdict, feedback, category);
        return ExtractionResult.success(result, rawOutput);
    }

    private String extractFinalText(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return "";
        }
        OpenCodeNdjsonParser parser = new OpenCodeNdjsonParser();
        OpenCodeResult parsed = parser.parse(rawOutput);
        return parsed.text();
    }

    @Nullable
    private String findJsonObject(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = JSON_PATTERN.matcher(text);
        String bestMatch = null;
        while (matcher.find()) {
            String candidate = matcher.group();
            try {
                MAPPER.readValue(candidate, Map.class);
                if (bestMatch == null || candidate.length() > bestMatch.length()) {
                    bestMatch = candidate;
                }
            } catch (JsonProcessingException e) {
                // not valid JSON, skip
            }
        }
        return bestMatch;
    }

    private Map<String, Object> normalizePlannerFields(Map<String, Object> raw) {
        Map<String, Object> result = new LinkedHashMap<>(raw);

        if (result.containsKey("sub_tasks") && !result.containsKey("subTasks")) {
            Object subTasks = result.remove("sub_tasks");
            result.put("subTasks", subTasks);
        }

        if (result.containsKey("subTasks") && result.get("subTasks") instanceof List<?> list) {
            List<Map<String, Object>> normalized = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> subTask = new LinkedHashMap<>((Map<String, Object>) map);
                    if (subTask.containsKey("depends_on") && !subTask.containsKey("dependsOn")) {
                        Object dependsOn = subTask.remove("depends_on");
                        subTask.put("dependsOn", dependsOn);
                    }
                    normalized.add(subTask);
                }
            }
            result.put("subTasks", normalized);
        }

        return result;
    }

    @Nullable
    private String extractClassification(String text) {
        if (text == null) return null;
        Matcher matcher = CLASSIFICATION_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return null;
    }

    @Nullable
    private String extractTestCommand(String text) {
        if (text == null) return null;
        Matcher matcher = TEST_COMMAND_PATTERN.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group(1).trim();
            if (isLikelyTestCommand(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isLikelyTestCommand(String s) {
        String lower = s.toLowerCase();
        return lower.contains("test") || lower.contains("mvn") || lower.contains("gradle") ||
                lower.contains("npm") || lower.contains("pytest") || lower.contains("jest") ||
                lower.contains("cargo") || lower.contains("dotnet") || lower.contains("make");
    }

    @Nullable
    private String extractCommitHash(String text) {
        if (text == null) return null;
        Matcher matcher = COMMIT_HASH_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    @Nullable
    private String extractTestSummary(String text) {
        if (text == null) return null;
        Pattern[] patterns = {
                Pattern.compile("(?:behavior covered|test(?:s)? (?:cover|description|summary)|what (?:the )?tests? (?:cover|do)):?\\s*\\n?(.{10,200})", Pattern.CASE_INSENSITIVE),
                Pattern.compile("(?:## (?:Behavior|Test|Description|Summary))\\s*\\n(.{10,200})", Pattern.CASE_INSENSITIVE)
        };
        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        return null;
    }

    @Nullable
    private String extractImplementationSummary(String text) {
        if (text == null) return null;
        Pattern[] patterns = {
                Pattern.compile("(?:implementation summary|summary|what (?:i|was) (?:did|implemented|changed)):?\\s*\\n?(.{10,500})", Pattern.CASE_INSENSITIVE),
                Pattern.compile("(?:## (?:Implementation |)Summary)\\s*\\n(.{10,500})", Pattern.CASE_INSENSITIVE)
        };
        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        return null;
    }

    private List<String> extractFiles(String text) {
        List<String> files = new ArrayList<>();
        if (text == null) return files;

        Matcher matcher = FILE_LIST_PATTERN.matcher(text);
        if (matcher.find()) {
            String fileListStr = matcher.group(1);
            for (String line : fileListStr.split("\\n")) {
                String cleaned = line.trim().replaceAll("^[-*]\\s*", "").replaceAll("`", "").trim();
                if (!cleaned.isEmpty() && cleaned.contains(".")) {
                    files.add(cleaned);
                }
            }
        }

        if (files.isEmpty()) {
            Pattern inlineFilePattern = Pattern.compile("`([^`]+\\.[a-z]{1,5})`", Pattern.CASE_INSENSITIVE);
            Matcher inlineMatcher = inlineFilePattern.matcher(text);
            while (inlineMatcher.find()) {
                String file = inlineMatcher.group(1);
                if (file.contains("/") || file.contains("\\")) {
                    files.add(file);
                }
            }
        }

        return files;
    }

    @Nullable
    private String extractTestResult(String text) {
        if (text == null) return null;
        Pattern[] patterns = {
                Pattern.compile("(?:test (?:result|outcome|status)|pass/fail result):?\\s*`?([^\\n`]+)`?", Pattern.CASE_INSENSITIVE),
                Pattern.compile("All tests? (?:passed|pass)\\.?", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\d+ (?:test|spec)s? (?:passed|failed|total)", Pattern.CASE_INSENSITIVE)
        };
        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                return m.groupCount() > 0 ? m.group(1).trim() : m.group().trim();
            }
        }
        return null;
    }

    @Nullable
    private String extractFirstLineVerdict(String text) {
        if (text == null || text.isBlank()) return null;

        String[] lines = text.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String upper = trimmed.toUpperCase();
            if (upper.equals("APPROVE")) return "APPROVE";
            if (upper.equals("REQUEST_CHANGES")) return "REQUEST_CHANGES";
            if (upper.startsWith("APPROVE")) return "APPROVE";
            if (upper.startsWith("REQUEST_CHANGES")) return "REQUEST_CHANGES";
            break;
        }
        return null;
    }

    private String extractFeedbackBody(String text) {
        if (text == null) return "";
        String[] lines = text.split("\\n");
        StringBuilder feedback = new StringBuilder();
        boolean foundVerdict = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (!foundVerdict) {
                String upper = trimmed.toUpperCase();
                if (upper.equals("APPROVE") || upper.equals("REQUEST_CHANGES") ||
                        upper.startsWith("APPROVE") || upper.startsWith("REQUEST_CHANGES")) {
                    foundVerdict = true;
                    continue;
                }
                if (!trimmed.isEmpty()) {
                    foundVerdict = true;
                }
            }
            if (foundVerdict) {
                feedback.append(line).append("\n");
            }
        }
        return feedback.toString().trim();
    }

    private String classifyReviewerFeedback(ReviewVerdict verdict, String feedback) {
        if (verdict == ReviewVerdict.APPROVE) {
            return null;
        }
        if (feedback == null || feedback.isBlank()) {
            return "unclear";
        }
        String lower = feedback.toLowerCase();
        for (String keyword : TEST_ISSUE_KEYWORDS) {
            if (lower.contains(keyword)) {
                return "test_issue";
            }
        }
        return "implementation_issue";
    }
}
