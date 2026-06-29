/**
 * SPI for adapter/extension contributions to the Verbaria server UI.
 * <p>
 * Adapters (e.g. {@code zanata-adapter-consulo}) describe extra, type-specific
 * editor affordances for a text flow here — for example editing the Consulo
 * mnemonic of an inline-text entry — without depending on the UI framework
 * (Vaadin). The {@code zanata-server-ui} module discovers these contributions
 * and renders the corresponding controls in the translation editor.
 */
package org.verbaria.server.ui;
