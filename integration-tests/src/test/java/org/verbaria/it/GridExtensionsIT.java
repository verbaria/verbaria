package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.util.List;

import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

import org.zanata.client.commands.push.PushCommand;
import org.zanata.client.commands.push.PushOptionsImpl;
import org.zanata.common.LocaleId;
import org.zanata.model.HDocument;
import org.zanata.model.HTextFlow;
import org.zanata.rest.dto.extensions.comment.SimpleComment;

class GridExtensionsIT extends AbstractPushPullIT {

    @Test
    void gridExtensionsRenderAfterSessionCloses() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itgrid", VERSION);
        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        PushOptionsImpl push = pushOpts("source", "properties", "itgrid");
        push.setLocaleMapList(frLocales());
        push.setIncludes("messages*.properties");
        new PushCommand(push).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itgrid", VERSION, "messages").orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        new TransactionTemplate(txManager).executeWithoutResult(s -> {
            HTextFlow tf = textFlowRepository.findById(tfId).orElseThrow();
            extensionStore.put(tf, new SimpleComment("grid note"));
            textFlowRepository.save(tf);
        });

        List<HTextFlow> detached = translationEditService.pageWithExtensions(
                doc.getId(), new LocaleId("fr-FR"), "", 0, PageRequest.of(0, 50));

        HTextFlow rendered = detached.stream()
                .filter(f -> f.getId().equals(tfId)).findFirst().orElseThrow();
        assertThat(extensionStore.get(rendered, SimpleComment.class)
                .orElseThrow().getValue()).isEqualTo("grid note");
        assertThat(extensionStore.contentType(rendered)).isEmpty();
    }

    @Test
    void detachedExtensionsThrowWithoutEagerInit() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itgridlazy", VERSION);
        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        PushOptionsImpl push = pushOpts("source", "properties", "itgridlazy");
        push.setLocaleMapList(frLocales());
        push.setIncludes("messages*.properties");
        new PushCommand(push).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itgridlazy", VERSION, "messages")
                .orElseThrow();

        HTextFlow detached = new TransactionTemplate(txManager).execute(st ->
                textFlowRepository.pageForTranslateView(doc.getId(),
                        new LocaleId("fr-FR"), "", 0, PageRequest.of(0, 50)).get(0));

        assertThatThrownBy(() -> extensionStore.get(detached, SimpleComment.class))
                .isInstanceOf(LazyInitializationException.class);
    }
}
