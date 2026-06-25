package org.verbaria.server.headless.extension.chrome;

import org.springframework.stereotype.Component;
import org.verbaria.server.headless.extension.TextFlowExtensionFactory;
import org.zanata.rest.dto.extensions.chrome.ChromeMessage;

@Component
public class ChromeExtensionFactory
        implements TextFlowExtensionFactory<ChromeMessage> {

    @Override
    public String type() {
        return ChromeMessage.ID;
    }

    @Override
    public Class<ChromeMessage> pojoType() {
        return ChromeMessage.class;
    }
}
