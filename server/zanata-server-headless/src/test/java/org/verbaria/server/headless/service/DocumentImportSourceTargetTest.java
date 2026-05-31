package org.verbaria.server.headless.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.common.ProjectType;
import org.zanata.model.HLocale;
import org.zanata.model.HProjectIteration;
import org.zanata.model.HTextFlowTarget;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;
import org.verbaria.server.headless.repository.DocumentRepository;
import org.verbaria.server.headless.repository.LocaleRepository;
import org.verbaria.server.headless.repository.ProjectIterationRepository;
import org.verbaria.server.headless.repository.TextFlowRepository;
import org.verbaria.server.headless.repository.TextFlowTargetHistoryRepository;
import org.verbaria.server.headless.repository.TextFlowTargetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The source language is the project's editable "key-sharing" base locale, so a
 * source push must give every flow a target in the source locale — for ANY
 * project type, not just Consulo. Approved when an admin imports, Translated
 * otherwise (mirroring the push --approve / non-admin-downgrade rule).
 */
class DocumentImportSourceTargetTest {

    private ProjectIterationRepository iterationRepository;
    private DocumentRepository documentRepository;
    private TextFlowRepository textFlowRepository;
    private TextFlowTargetRepository textFlowTargetRepository;
    private TextFlowTargetHistoryRepository historyRepository;
    private LocaleRepository localeRepository;
    private DocumentImportService service;

    @BeforeEach
    void setUp() {
        iterationRepository = mock(ProjectIterationRepository.class);
        documentRepository = mock(DocumentRepository.class);
        textFlowRepository = mock(TextFlowRepository.class);
        textFlowTargetRepository = mock(TextFlowTargetRepository.class);
        historyRepository = mock(TextFlowTargetHistoryRepository.class);
        localeRepository = mock(LocaleRepository.class);
        service = new DocumentImportService(iterationRepository, documentRepository,
                textFlowRepository, textFlowTargetRepository, historyRepository,
                localeRepository);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** Proves it is NOT gated to Consulo: every project type gets a source
     *  target, and as admin it lands Approved. */
    @ParameterizedTest
    @EnumSource(ProjectType.class)
    void createsApprovedSourceTargetForEveryProjectTypeWhenAdmin(ProjectType pt) {
        asAdmin();
        List<HTextFlowTarget> saved = runImport(pt);

        assertThat(saved).hasSize(1);
        HTextFlowTarget t = saved.get(0);
        assertThat(t.getState()).isEqualTo(ContentState.Approved);
        assertThat(t.getContents()).containsExactly("Add");
    }

    @Test
    void nonAdminGetsTranslatedSourceTarget() {
        asUser(); // authenticated, but not an admin
        List<HTextFlowTarget> saved = runImport(ProjectType.Properties);

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getState()).isEqualTo(ContentState.Translated);
    }

    @Test
    void anonymousGetsTranslatedSourceTarget() {
        // no authentication at all
        List<HTextFlowTarget> saved = runImport(ProjectType.Gettext);

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getState()).isEqualTo(ContentState.Translated);
    }

    // --- helpers -----------------------------------------------------------

    private List<HTextFlowTarget> runImport(ProjectType pt) {
        HProjectIteration iter = new HProjectIteration();
        iter.setProjectType(pt);
        HLocale src = new HLocale(new LocaleId("en-US"));

        when(iterationRepository.findByProjectAndSlug(any(), any()))
                .thenReturn(Optional.of(iter));
        when(localeRepository.findByLocaleId(any()))
                .thenReturn(Optional.of(src));
        when(documentRepository.findByVersionAndDocId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(documentRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(textFlowTargetRepository.findByTextFlowAndLocale(any(), any()))
                .thenReturn(Optional.empty());
        when(textFlowTargetRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Resource res = new Resource("messages");
        res.setLang(new LocaleId("en-US"));
        res.getTextFlows().add(
                new TextFlow("greeting.hello", new LocaleId("en-US"), "Add"));

        service.importSource("proj", "ver", "messages", res, null);

        ArgumentCaptor<HTextFlowTarget> cap =
                ArgumentCaptor.forClass(HTextFlowTarget.class);
        verify(textFlowTargetRepository, atLeastOnce()).save(cap.capture());
        return cap.getAllValues();
    }

    private static void asAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "x",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private static void asUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("bob", "x",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
