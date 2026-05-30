package com.tddforge.persistence;

import com.tddforge.domain.ReviewVerdict;
import com.tddforge.domain.ReviewerResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewerResultsConverterTest {

    private final ReviewerResultsConverter converter = new ReviewerResultsConverter();

    @Test
    void serializeNull_returnsEmptyJsonArray() {
        assertThat(converter.convertToDatabaseColumn(null)).isEqualTo("[]");
    }

    @Test
    void serializeEmptyList_returnsEmptyJsonArray() {
        assertThat(converter.convertToDatabaseColumn(new ArrayList<>())).isEqualTo("[]");
    }

    @Test
    void serializeSingleResult_producesValidJson() {
        var result = new ReviewerResult("rev-1", ReviewVerdict.APPROVE, "Looks good", null);
        String json = converter.convertToDatabaseColumn(List.of(result));
        assertThat(json).contains("rev-1");
        assertThat(json).contains("APPROVE");
        assertThat(json).contains("Looks good");
    }

    @Test
    void serializeMultipleResults() {
        var r1 = new ReviewerResult("rev-1", ReviewVerdict.APPROVE, "good", null);
        var r2 = new ReviewerResult("rev-2", ReviewVerdict.REQUEST_CHANGES, "fix it", "test_issue");
        String json = converter.convertToDatabaseColumn(List.of(r1, r2));
        assertThat(json).contains("rev-1");
        assertThat(json).contains("rev-2");
        assertThat(json).contains("test_issue");
    }

    @Test
    void deserializeNull_returnsEmptyList() {
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
    }

    @Test
    void deserializeBlank_returnsEmptyList() {
        assertThat(converter.convertToEntityAttribute("   ")).isEmpty();
    }

    @Test
    void deserializeEmptyArray_returnsEmptyList() {
        assertThat(converter.convertToEntityAttribute("[]")).isEmpty();
    }

    @Test
    void deserializeSingleResult() {
        var json = """
                [{"reviewerId":"rev-1","verdict":"APPROVE","feedback":"good"}]
                """;
        List<ReviewerResult> results = converter.convertToEntityAttribute(json);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).reviewerId()).isEqualTo("rev-1");
        assertThat(results.get(0).verdict()).isEqualTo(ReviewVerdict.APPROVE);
        assertThat(results.get(0).feedback()).isEqualTo("good");
        assertThat(results.get(0).category()).isNull();
    }

    @Test
    void deserializeResultWithCategory() {
        var json = """
                [{"reviewerId":"rev-1","verdict":"REQUEST_CHANGES","feedback":"fix it","category":"test_issue"}]
                """;
        List<ReviewerResult> results = converter.convertToEntityAttribute(json);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).category()).isEqualTo("test_issue");
        assertThat(results.get(0).verdict()).isEqualTo(ReviewVerdict.REQUEST_CHANGES);
    }

    @Test
    void deserializeMultipleResults() {
        var json = """
                [
                    {"reviewerId":"rev-1","verdict":"APPROVE","feedback":"good"},
                    {"reviewerId":"rev-2","verdict":"REQUEST_CHANGES","feedback":"fix","category":"impl_issue"}
                ]
                """;
        List<ReviewerResult> results = converter.convertToEntityAttribute(json);
        assertThat(results).hasSize(2);
        assertThat(results.get(0).reviewerId()).isEqualTo("rev-1");
        assertThat(results.get(1).reviewerId()).isEqualTo("rev-2");
        assertThat(results.get(1).category()).isEqualTo("impl_issue");
    }

    @Test
    void deserializeH2QuotedJson_stripsQuotes() {
        var inner = "[{\"reviewerId\":\"r1\",\"verdict\":\"APPROVE\",\"feedback\":\"ok\"}]";
        var quoted = "\"" + inner.replace("\"", "\\\"") + "\"";
        List<ReviewerResult> results = converter.convertToEntityAttribute(quoted);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).reviewerId()).isEqualTo("r1");
    }

    @Test
    void roundTrip_preservesData() {
        var r1 = new ReviewerResult("rev-1", ReviewVerdict.APPROVE, "good", null);
        var r2 = new ReviewerResult("rev-2", ReviewVerdict.REQUEST_CHANGES, "fix", "test_issue");
        List<ReviewerResult> original = List.of(r1, r2);

        String json = converter.convertToDatabaseColumn(original);
        List<ReviewerResult> restored = converter.convertToEntityAttribute(json);

        assertThat(restored).hasSize(2);
        assertThat(restored.get(0).reviewerId()).isEqualTo("rev-1");
        assertThat(restored.get(0).verdict()).isEqualTo(ReviewVerdict.APPROVE);
        assertThat(restored.get(0).feedback()).isEqualTo("good");
        assertThat(restored.get(0).category()).isNull();
        assertThat(restored.get(1).reviewerId()).isEqualTo("rev-2");
        assertThat(restored.get(1).verdict()).isEqualTo(ReviewVerdict.REQUEST_CHANGES);
        assertThat(restored.get(1).category()).isEqualTo("test_issue");
    }

    @Test
    void deserializeInvalidJson_throwsException() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("not json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to deserialize");
    }
}
