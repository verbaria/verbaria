package org.zanata.adapter.consulo;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

/**
 * Dumps any string containing an apostrophe in double-quoted style. snakeyaml's
 * default single-quoted style escapes each {@code '} as {@code ''}, which
 * doubles the apostrophes of ICU/MessageFormat strings; double-quoted style
 * keeps them literal so consulo source round-trips unchanged.
 */
public final class ApostropheSafeRepresenter extends Representer {

    public ApostropheSafeRepresenter(DumperOptions options) {
        super(options);
    }

    @Override
    protected Node representScalar(Tag tag, String value,
            DumperOptions.ScalarStyle style) {
        if (Tag.STR.equals(tag) && value.indexOf('\'') >= 0) {
            style = DumperOptions.ScalarStyle.DOUBLE_QUOTED;
        }
        return super.representScalar(tag, value, style);
    }
}
