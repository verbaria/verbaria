/*
 * Copyright 2026, verbaria.org and Red Hat, Inc. and individual contributors as indicated by the
 * @author tags. See the copyright.txt file in the distribution for a full
 * listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.zanata.rest.dto.v1;

import java.net.URI;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.zanata.common.ContentState;
import org.zanata.common.ContentType;
import org.zanata.common.LocaleId;
import org.zanata.common.ResourceType;
import org.zanata.rest.dto.Glossary;
import org.zanata.rest.dto.GlossaryEntry;
import org.zanata.rest.dto.GlossaryTerm;
import org.zanata.rest.dto.Link;
import org.zanata.rest.dto.Links;
import org.zanata.rest.dto.Person;
import org.zanata.rest.dto.Project;
import org.zanata.rest.dto.extensions.comment.SimpleComment;
import org.zanata.rest.dto.extensions.gettext.HeaderEntry;
import org.zanata.rest.dto.extensions.gettext.PoHeader;
import org.zanata.rest.dto.extensions.gettext.PoTargetHeader;
import org.zanata.rest.dto.extensions.gettext.PotEntryHeader;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.ResourceMeta;
import org.zanata.rest.dto.resource.TextFlow;
import org.zanata.rest.dto.resource.TextFlowTarget;
import org.zanata.rest.dto.resource.TranslationsResource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Round-trip JSON serialization tests for the public REST DTOs.
 * Replaces the legacy Spock test (which also covered XML via the now-removed
 * JaxbTestUtil) — the API is JSON-only now.
 */
public class SerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private <T> T roundTripJson(T obj) throws Exception {
        String json = mapper.writeValueAsString(obj);
        @SuppressWarnings("unchecked")
        T result = (T) mapper.readValue(json, obj.getClass());
        return result;
    }

    private static Person createPerson() {
        return new Person("user@localhost", "User");
    }

    private static Project createProject() throws Exception {
        Project p = new Project().createSample();
        Links links = new Links();
        links.add(new Link(new URI("http://www.zanata.org"), "", "linkType"));
        links.add(new Link(new URI("http://www2.zanata.org"), "", "linkType"));
        p.setLinks(links);
        return p;
    }

    @Test
    public void roundTripProject() throws Exception {
        Project p = createProject();
        assertEquals(p, roundTripJson(p));
    }

    @Test
    public void roundTripPerson() throws Exception {
        Person p = createPerson();
        assertEquals(p, roundTripJson(p));
    }

    @Test
    public void roundTripResourceMeta() throws Exception {
        ResourceMeta res = new ResourceMeta("id");
        PoHeader poHeader = new PoHeader("comment",
                new HeaderEntry("h1", "v1"),
                new HeaderEntry("h2", "v2"));
        res.getExtensions(true).add(poHeader);
        assertEquals(res, roundTripJson(res));
    }

    @Test
    public void roundTripSourceResource() throws Exception {
        Resource sourceResource = new Resource("Acls.pot");
        sourceResource.setType(ResourceType.FILE);
        sourceResource.setContentType(ContentType.PO);
        sourceResource.setLang(LocaleId.EN);

        TextFlow tf = new TextFlow();
        tf.setContents("ttff");
        SimpleComment comment = new SimpleComment("test");
        PotEntryHeader pot = new PotEntryHeader();
        pot.setContext("context");
        pot.getReferences().add("fff");
        tf.getExtensions(true).add(comment);
        tf.getExtensions(true).add(pot);

        TextFlow tf2 = new TextFlow();
        tf2.setContents("ttff2");
        sourceResource.getTextFlows().add(tf);
        sourceResource.getTextFlows().add(tf2);

        PoHeader poHeader = new PoHeader("comment",
                new HeaderEntry("h1", "v1"),
                new HeaderEntry("h2", "v2"));
        sourceResource.getExtensions(true).add(poHeader);

        assertEquals(sourceResource, roundTripJson(sourceResource));
    }

    @Test
    public void roundTripTranslationsResource() throws Exception {
        TranslationsResource entity = new TranslationsResource();
        TextFlowTarget target = new TextFlowTarget("rest1");
        target.setContents("hello world");
        target.setState(ContentState.Translated);
        target.setTranslator(createPerson());
        target.getExtensions(true).add(new SimpleComment("testcomment"));
        entity.getTextFlowTargets().add(target);
        entity.getExtensions(true);
        PoTargetHeader poTargetHeader = new PoTargetHeader(
                "target header comment",
                new HeaderEntry("ht", "vt1"),
                new HeaderEntry("th2", "tv2"));
        entity.getExtensions(true).add(poTargetHeader);
        assertEquals(entity, roundTripJson(entity));
    }

    @Test
    public void roundTripGlossary() throws Exception {
        Glossary glossary = new Glossary();
        GlossaryEntry entry = new GlossaryEntry();
        entry.setSrcLang(LocaleId.EN_US);
        entry.setSourceReference("source ref");

        GlossaryTerm term = new GlossaryTerm();
        term.setContent("testData1");
        term.setLocale(LocaleId.EN_US);
        term.setComment("comment1");
        term.setComment("comment2");
        term.setComment("comment3");

        GlossaryTerm term2 = new GlossaryTerm();
        term2.setContent("testData2");
        term2.setLocale(LocaleId.DE);
        term2.setComment("comment4");
        term2.setComment("comment5");
        term2.setComment("comment6");

        entry.getGlossaryTerms().add(term);
        entry.getGlossaryTerms().add(term2);
        glossary.getGlossaryEntries().add(entry);

        assertEquals(glossary, roundTripJson(glossary));
    }
}
