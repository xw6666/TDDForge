package com.tddforge.persistence;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonStringListConverterTest {

    private final JsonStringListConverter converter = new JsonStringListConverter();

    @Test
    void serializeNull_returnsEmptyJsonArray() {
        assertThat(converter.convertToDatabaseColumn(null)).isEqualTo("[]");
    }

    @Test
    void serializeEmptyList_returnsEmptyJsonArray() {
        assertThat(converter.convertToDatabaseColumn(new ArrayList<>())).isEqualTo("[]");
    }

    @Test
    void serializeList_producesValidJson() {
        String json = converter.convertToDatabaseColumn(List.of("a", "b", "c"));
        assertThat(json).isEqualTo("[\"a\",\"b\",\"c\"]");
    }

    @Test
    void serializeSingleElement() {
        assertThat(converter.convertToDatabaseColumn(List.of("only"))).isEqualTo("[\"only\"]");
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
    void deserializeJsonArray_returnsList() {
        List<String> result = converter.convertToEntityAttribute("[\"x\",\"y\"]");
        assertThat(result).containsExactly("x", "y");
    }

    @Test
    void deserializeSingleElement() {
        assertThat(converter.convertToEntityAttribute("[\"only\"]")).containsExactly("only");
    }

    @Test
    void deserializeH2QuotedJson_stripsQuotes() {
        List<String> result = converter.convertToEntityAttribute("\"[\\\"a\\\",\\\"b\\\"]\"");
        assertThat(result).containsExactly("a", "b");
    }

    @Test
    void roundTrip_preservesData() {
        List<String> original = List.of("dep-1", "dep-2", "dep-3");
        String json = converter.convertToDatabaseColumn(original);
        List<String> restored = converter.convertToEntityAttribute(json);
        assertThat(restored).isEqualTo(original);
    }

    @Test
    void roundTrip_emptyList() {
        String json = converter.convertToDatabaseColumn(List.of());
        List<String> restored = converter.convertToEntityAttribute(json);
        assertThat(restored).isEmpty();
    }

    @Test
    void deserializeInvalidJson_throwsException() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("not json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to deserialize");
    }

    @Test
    void serializeWithSpecialCharacters() {
        List<String> original = List.of("has \"quotes\"", "has \\ backslash");
        String json = converter.convertToDatabaseColumn(original);
        List<String> restored = converter.convertToEntityAttribute(json);
        assertThat(restored).isEqualTo(original);
    }

    @Test
    void roundTripWithEmptyStrings() {
        List<String> original = List.of("", "non-empty", "");
        String json = converter.convertToDatabaseColumn(original);
        List<String> restored = converter.convertToEntityAttribute(json);
        assertThat(restored).isEqualTo(original);
    }
}
