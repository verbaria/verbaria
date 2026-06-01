package org.verbaria.it.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.zanata.common.LocaleId;
import org.zanata.rest.client.SourceDocResourceClient;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;

class SourceDocResourceClientIT extends AbstractRestClientIT {

    private static final Set<String> EXT = Set.of("gettext", "comment");

    private SourceDocResourceClient client(String proj) throws Exception {
        fixtures.ensureProject(proj, "master");
        return factory().getSourceDocResourceClient(proj, "master");
    }

    private static Resource doc(String name) {
        Resource doc = new Resource(name);
        doc.setLang(LocaleId.EN_US);
        doc.getTextFlows().add(new TextFlow("hello", LocaleId.EN_US, "world"));
        return doc;
    }

    @Test
    void testGetResourceMeta() throws Exception {
        SourceDocResourceClient client = client("rcsrcmeta");
        client.putResource("a", doc("a"), EXT, true);
        client.putResource("b", doc("b"), EXT, true);
        assertThat(client.getResourceMeta(null))
                .extracting(m -> m.getName())
                .contains("a", "b");
    }

    @Test
    void testGetResource() throws Exception {
        SourceDocResourceClient client = client("rcsrcget");
        client.putResource("test", doc("test"), EXT, true);
        Resource resource = client.getResource("test", EXT);
        assertThat(resource.getName()).isEqualTo("test");
    }

    @Test
    void testPutResource() throws Exception {
        SourceDocResourceClient client = client("rcsrcput");
        client.putResource("test", doc("test"), EXT, true);
        // The server hashes the text-flow id and keeps the original key in the
        // pot-entry-header context, so match on content rather than id.
        assertThat(client.getResource("test", EXT).getTextFlows())
                .anyMatch(tf -> tf.getContents().contains("world"));
    }

    @Test
    void testDeleteResource() throws Exception {
        SourceDocResourceClient client = client("rcsrcdel");
        client.putResource("test", doc("test"), EXT, true);
        client.deleteResource("test");
        assertThat(client.getResourceMeta(null))
                .noneMatch(m -> "test".equals(m.getName()));
    }
}
