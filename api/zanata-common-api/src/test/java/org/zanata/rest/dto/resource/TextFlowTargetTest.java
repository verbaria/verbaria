package org.zanata.rest.dto.resource;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TextFlowTargetTest {
    ObjectMapper om = new ObjectMapper();

    @Test
    public void testReadJsonPlural() throws JsonParseException,
            JsonMappingException, IOException {
        String json =
                "{\n" + "    \"id\" : \"_id\",\n"
                        + "    \"resId\" : \"_resid\",\n"
                        + "    \"revision\" : 17,\n"
                        + "    \"lang\" : \"es-ES\",\n"
                        + "    \"contents\" : [\"plural1\", \"plural2\"]\n"
                        + "}";
        TextFlowTarget tft = om.readValue(json, TextFlowTarget.class);

        TextFlowTarget expected = new TextFlowTarget("_id");
        expected.setResId("_resid");
        expected.setContents("plural1", "plural2");
        expected.setRevision(17);
        assertEquals(expected, tft);
    }

    @Test
    public void testReadJsonSingular() throws JsonParseException,
            JsonMappingException, IOException {
        String json =
                "{\n" + "    \"id\" : \"_id\",\n"
                        + "    \"resId\" : \"_resid\",\n"
                        + "    \"revision\" : 17,\n"
                        + "    \"lang\" : \"es-ES\",\n"
                        + "    \"content\" : \"single\"\n" + "}";
        TextFlowTarget tft = om.readValue(json, TextFlowTarget.class);

        TextFlowTarget expected = new TextFlowTarget("_id");
        expected.setResId("_resid");
        expected.setContents("single");
        expected.setRevision(17);
        assertEquals(expected, tft);
    }
}
