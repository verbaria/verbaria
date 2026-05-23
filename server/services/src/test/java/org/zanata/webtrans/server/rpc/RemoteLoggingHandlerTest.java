package org.zanata.webtrans.server.rpc;

import io.github.cdiunit.InRequestScope;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.zanata.ZanataTest;
import org.zanata.security.ZanataCredentials;
import org.zanata.security.ZanataIdentity;
import org.zanata.test.CdiUnitRunner;
import org.zanata.webtrans.shared.rpc.NoOpResult;
import org.zanata.webtrans.shared.rpc.RemoteLoggingAction;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Patrick Huang <a
 *         href="mailto:pahuang@redhat.com">pahuang@redhat.com</a>
 */
@RunWith(CdiUnitRunner.class)
public class RemoteLoggingHandlerTest extends ZanataTest {
    @Inject @Any
    private RemoteLoggingHandler handler;

    @Produces @Mock
    private ZanataIdentity identity;

    @Before
    public void setUp() throws Exception {
        when(identity.getCredentials()).thenReturn(new ZanataCredentials());
    }

    @Test
    @InRequestScope
    public void testExecute() throws Exception {
        handler.execute(new RemoteLoggingAction("blah"), null);

        verify(identity).checkLoggedIn();
        verify(identity).getCredentials();
    }

    @Test
    @InRequestScope
    public void testExecuteWithoutLoggedIn() throws Exception {
        doThrow(new RuntimeException("not logged in")).when(identity)
                .checkLoggedIn();

        NoOpResult result =
                handler.execute(new RemoteLoggingAction("blah"), null);

        assertThat(result).isNotNull();
    }

    @Test
    @InRequestScope
    public void testRollback() throws Exception {
        handler.rollback(new RemoteLoggingAction("blow"), new NoOpResult(),
                null);
    }
}
