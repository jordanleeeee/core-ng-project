package core.framework.internal.module;

import core.framework.api.json.Property;
import core.framework.api.validate.Min;
import core.framework.api.validate.NotNull;
import core.framework.http.HTTPMethod;
import core.framework.internal.web.service.TestWebService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jordan
 */
class PropertyParserTest {
    private final PropertyParser parser = new PropertyParser();

    public Integer aInteger;
    public Boolean aBoolean;
    public Double aDouble;
    public TestWebService.TestEnum aTestEnum;
    public HTTPMethod bTestEnum;

    public Integer[] aIntArr;
    public String[] aStrArr;
    public Double[] aDoubleArr;
    public TestWebService.TestEnum[] aEnumArr;
    public TestJson[] aJsonArr;

    public List<Integer> aIntList;
    public List<String> aStrList;
    public List<Double> aDoubleList;
    public List<TestWebService.TestEnum> aEnumList;
    public List<TestJson> aJsonList;

    public Set<Integer> aIntSet;
    public Set<String> aStrSet;
    public Set<Double> aDoubleSet;
    public Set<TestWebService.TestEnum> aEnumSet;

    String simpleCollectionProperty = "1;2;3;4";
    String enumCollectionProperty = "B1;A1";
    String jsonCollectionProperty = """
          {
            "number": 12,
            "name": "a1"
          };
          {
            "number": 13,
            "name": "a2"
          }
        """;

    @Test
    void property() throws NoSuchFieldException {
        Field integerField = getClass().getDeclaredField("aInteger");
        assertThat(parser.parseTypedProperty(integerField, "aInteger", "1", false, '\0')).isEqualTo(1);

        Field boolField = getClass().getDeclaredField("aBoolean");
        assertThat(parser.parseTypedProperty(boolField, "aBoolean", "true", false, '\0')).isEqualTo(Boolean.TRUE);
        assertThat(parser.parseTypedProperty(boolField, "aBoolean", "false", false, '\0')).isEqualTo(Boolean.FALSE);

        Field doubleField = getClass().getDeclaredField("aDouble");
        assertThat(parser.parseTypedProperty(doubleField, "aDouble", "1.23", false, '\0')).isEqualTo(1.23);

        Field enumField = getClass().getDeclaredField("aTestEnum");
        assertThat(parser.parseTypedProperty(enumField, "aTestEnum", "A1", false, '\0')).isEqualTo(TestWebService.TestEnum.A);

        Field enumField2 = getClass().getDeclaredField("bTestEnum");
        assertThat(parser.parseTypedProperty(enumField2, "bTestEnum", "GET", false, '\0')).isEqualTo(HTTPMethod.GET);
    }

    @Test
    void arrayProperty() throws NoSuchFieldException {
        Field intArrType = getClass().getDeclaredField("aIntArr");
        assertThat(parser.parseTypedProperty(intArrType, "aIntArr", simpleCollectionProperty, false, ';')).isEqualTo(new Integer[]{1, 2, 3, 4});

        Field strArrType = getClass().getDeclaredField("aStrArr");
        assertThat(parser.parseTypedProperty(strArrType, "aStrArr", simpleCollectionProperty, false, ';')).isEqualTo(new String[]{"1", "2", "3", "4"});

        Field doubleArrType = getClass().getDeclaredField("aDoubleArr");
        assertThat(parser.parseTypedProperty(doubleArrType, "aDoubleArr", simpleCollectionProperty, false, ';')).isEqualTo(new Double[]{1.0, 2.0, 3.0, 4.0});

        Field enumArrType = getClass().getDeclaredField("aEnumArr");
        assertThat(parser.parseTypedProperty(enumArrType, "aEnumArr", enumCollectionProperty, false, ';')).isEqualTo(new TestWebService.TestEnum[]{TestWebService.TestEnum.B, TestWebService.TestEnum.A});

        Field jsonArrType = getClass().getDeclaredField("aJsonArr");
        assertThat(parser.parseTypedProperty(jsonArrType, "aJsonArr", jsonCollectionProperty, true, ';'))
            .usingRecursiveComparison()
            .isEqualTo(new TestJson[]{TestJson.valueOf(12, "a1"), TestJson.valueOf(13, "a2")});
    }

    @Test
    void listProperty() throws NoSuchFieldException {
        Field intListType = getClass().getDeclaredField("aIntList");
        assertThat(parser.parseTypedProperty(intListType, "aStrArr", simpleCollectionProperty, false, ';')).isEqualTo(List.of(1, 2, 3, 4));

        Field strListType = getClass().getDeclaredField("aStrList");
        assertThat(parser.parseTypedProperty(strListType, "aStrList", simpleCollectionProperty, false, ';')).isEqualTo(List.of("1", "2", "3", "4"));

        Field doubleListType = getClass().getDeclaredField("aDoubleList");
        assertThat(parser.parseTypedProperty(doubleListType, "aDoubleList", simpleCollectionProperty, false, ';')).isEqualTo(List.of(1.0, 2.0, 3.0, 4.0));

        Field enumListType = getClass().getDeclaredField("aEnumList");
        assertThat(parser.parseTypedProperty(enumListType, "aEnumList", enumCollectionProperty, false, ';')).isEqualTo(List.of(TestWebService.TestEnum.B, TestWebService.TestEnum.A));

        Field jsonListType = getClass().getDeclaredField("aJsonList");
        assertThat(parser.parseTypedProperty(jsonListType, "aJsonList", jsonCollectionProperty, true, ';'))
            .usingRecursiveComparison()
            .isEqualTo(List.of(TestJson.valueOf(12, "a1"), TestJson.valueOf(13, "a2")));
    }

    @Test
    void setProperty() throws NoSuchFieldException {
        Field intSetType = getClass().getDeclaredField("aIntSet");
        assertThat(parser.parseTypedProperty(intSetType, "aIntSet", simpleCollectionProperty, false, ';')).isEqualTo(Set.of(1, 2, 3, 4));

        Field strSetType = getClass().getDeclaredField("aStrSet");
        assertThat(parser.parseTypedProperty(strSetType, "aStrSet", simpleCollectionProperty, false, ';')).isEqualTo(Set.of("1", "2", "3", "4"));

        Field doubleSetType = getClass().getDeclaredField("aDoubleSet");
        assertThat(parser.parseTypedProperty(doubleSetType, "aStrSet", simpleCollectionProperty, false, ';')).isEqualTo(Set.of(1.0, 2.0, 3.0, 4.0));

        Field enumSetType = getClass().getDeclaredField("aEnumSet");
        assertThat(parser.parseTypedProperty(enumSetType, "aEnumSet", enumCollectionProperty, false, ';')).isEqualTo(Set.of(TestWebService.TestEnum.B, TestWebService.TestEnum.A));
    }

    public static class TestJson {
        public static TestJson valueOf(Integer number, String name) {
            var json = new TestJson();
            json.number = number;
            json.name = name;
            return json;
        }

        @Min(10)
        @Property(name = "number")
        public Integer number;

        @NotNull
        @Property(name = "name")
        public String name;
    }
}
