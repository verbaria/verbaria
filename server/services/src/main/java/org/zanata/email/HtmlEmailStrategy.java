package org.zanata.email;

import org.zanata.i18n.Messages;

import java.util.List;

/**
 * Defines a strategy for creating HTML emails in HtmlEmailBuilder.
 * The original kotlin definition used kotlinx.html DSL for the body; ported to
 * a simple HTML-string producer.
 */
public abstract class HtmlEmailStrategy extends AbstractEmailStrategy {

    /**
     * A list of reasons for sending this email, to be shown in the email footer.
     */
    public abstract List<String> getReceivedReasons(Messages msgs);

    public abstract EmailAddressBlock getAddresses();

    /**
     * Returns the HTML body content for the email (plain string in the
     * post-kotlinx port). Implementations should produce well-formed HTML
     * matching the original kotlinx.html DSL output.
     */
    public abstract String renderBody(GeneralEmailContext generalContext, Messages msgs);
}
