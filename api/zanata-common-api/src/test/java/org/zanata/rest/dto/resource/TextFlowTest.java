package org.zanata.rest.dto.resource;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.zanata.common.LocaleId;

import static org.junit.Assert.assertEquals;

public class TextFlowTest {
    ObjectMapper om = new ObjectMapper();
    LocaleId esES = new LocaleId("es-ES");

    @Test
    public void testReadJsonPlural() throws JsonParseException,
            JsonMappingException, IOException {
        String json =
                "{\n" + "    \"id\" : \"_id\",\n" + "    \"revision\" : 17,\n"
                        + "    \"lang\" : \"es-ES\",\n"
                        + "    \"contents\" : [\"plural1\", \"plural2\"]\n"
                        + "}";
        TextFlow tf = om.readValue(json, TextFlow.class);

        TextFlow expected = new TextFlow("_id", esES, "plural1", "plural2");
        expected.setRevision(17);
        assertEquals(expected, tf);
    }

    @Test
    public void testReadJsonSingular() throws JsonParseException,
            JsonMappingException, IOException {
        String json =
                "{\n" + "    \"id\" : \"_id\",\n" + "    \"revision\" : 17,\n"
                        + "    \"lang\" : \"es-ES\",\n"
                        + "    \"content\" : \"single\"\n" + "}";
        TextFlow tf = om.readValue(json, TextFlow.class);

        TextFlow expected = new TextFlow("_id", esES, "single");
        expected.setRevision(17);
        assertEquals(expected, tf);
    }
}
