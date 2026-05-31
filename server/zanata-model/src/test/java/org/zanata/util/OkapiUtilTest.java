package org.zanata.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.zanata.util.OkapiUtil.countWords;

public class OkapiUtilTest {
    // @formatter:off
    private String[] strings = {
        "<author><firstname>Emmanuel</firstname> <surname>Bernard</surname></author>",
        "Graphic Design",
        "the object is passed up to the UI tier",

        // 4 (tokens in current line) [0 (total)]
        "// in the first session\n" +
        // 6 [10] (a.b is one token)
        "Cat cat = (Cat) firstSession.load(Cat.class, catID);\n\n" +
        // 7 [17]
        "// in a higher tier of the application\n" +
        // 4 [21]
        "Cat mate = new Cat();\n" +
        // 2 [23] (a.b is one token)
        "cat.setMate(mate);\n\n" +
        // 5 [28]
        "// later, in a new session\n" +
        // 10 [38] (a.b is one token, non-null is one token)
        "secondSession.saveOrUpdate(cat);   // update existing state (cat has a non-null id)\n" +
        // 11 [49] (a.b is one token)
        "secondSession.saveOrUpdate(mate);  // save the new instance (mate has a null id)",

        "<filename class=\"directory\">/var/lib/ricci</filename>",
        "https://cdn.redhat.com",
        "/etc/rhsm/rhsm.conf",
    };
    // @formatter:on
    // These counts represent the expected word counts for the strings listed
    // above.
    // Expected counts reflect okapi-step-tokenization 1.47.0 behaviour.
    private long[] count = {
            2,
            2, 8,
            48,
            3,
            1,
            3 };

    @Test
    public void testCountWords() {
        countWords(null, "en-US");
        int i = 0;
        for (String s : strings) {
            long expected = count[i++];
            Assertions.assertEquals(expected, countWords(s, "en-US"));
        }
    }

}
