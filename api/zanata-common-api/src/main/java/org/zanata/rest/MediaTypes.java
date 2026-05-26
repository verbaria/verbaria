package org.zanata.rest;

public class MediaTypes {

    public static enum Format {
        XML("xml"), JSON("json");

        private final String format;

        private Format(String format) {
            this.format = format;
        }

        public String toString() {
            return "+" + format;
        };
    }

    private static final String XML = "+xml";
    private static final String JSON = "+json";

    private static final String APPLICATION_VND_ZANATA =
            "application/vnd.zanata";

    public static final String APPLICATION_ZANATA_XML = APPLICATION_VND_ZANATA
            + XML;

    public static final String APPLICATION_ZANATA_PROJECT =
            APPLICATION_VND_ZANATA + ".project";
    public static final String APPLICATION_ZANATA_PROJECT_XML =
            APPLICATION_ZANATA_PROJECT + XML;
    public static final String APPLICATION_ZANATA_PROJECT_JSON =
            APPLICATION_ZANATA_PROJECT + JSON;

    public static final String APPLICATION_ZANATA_PROJECT_LOCALES =
            APPLICATION_ZANATA_PROJECT + ".locales";
    public static final String APPLICATION_ZANATA_PROJECT_LOCALES_XML =
            APPLICATION_ZANATA_PROJECT_LOCALES + XML;
    public static final String APPLICATION_ZANATA_PROJECT_LOCALES_JSON =
            APPLICATION_ZANATA_PROJECT_LOCALES + JSON;

    public static final String APPLICATION_ZANATA_PROJECTS =
            APPLICATION_VND_ZANATA + ".projects";
    public static final String APPLICATION_ZANATA_PROJECTS_XML =
            APPLICATION_ZANATA_PROJECTS + XML;
    public static final String APPLICATION_ZANATA_PROJECTS_JSON =
            APPLICATION_ZANATA_PROJECTS + JSON;

    public static final String APPLICATION_ZANATA_PROJECT_ITERATION =
            APPLICATION_VND_ZANATA + ".project.iteration";
    public static final String APPLICATION_ZANATA_PROJECT_ITERATION_XML =
            APPLICATION_ZANATA_PROJECT_ITERATION + XML;
    public static final String APPLICATION_ZANATA_PROJECT_ITERATION_JSON =
            APPLICATION_ZANATA_PROJECT_ITERATION + JSON;

    public static final String APPLICATION_ZANATA_ACCOUNT =
            APPLICATION_VND_ZANATA + ".account";
    public static final String APPLICATION_ZANATA_ACCOUNT_XML =
            APPLICATION_ZANATA_ACCOUNT + XML;
    public static final String APPLICATION_ZANATA_ACCOUNT_JSON =
            APPLICATION_ZANATA_ACCOUNT + JSON;

    public static final String APPLICATION_ZANATA_VERSION =
            APPLICATION_VND_ZANATA + ".Version";
    public static final String APPLICATION_ZANATA_VERSION_XML =
            APPLICATION_ZANATA_VERSION + XML;
    public static final String APPLICATION_ZANATA_VERSION_JSON =
            APPLICATION_ZANATA_VERSION + JSON;

    public static final String APPLICATION_ZANATA_GLOSSARY =
            APPLICATION_VND_ZANATA + ".glossary";
    public static final String APPLICATION_ZANATA_GLOSSARY_XML =
            APPLICATION_ZANATA_GLOSSARY + XML;
    public static final String APPLICATION_ZANATA_GLOSSARY_JSON =
            APPLICATION_ZANATA_GLOSSARY + JSON;

    public static final String APPLICATION_ZANATA_PROJECT_VERSION =
        APPLICATION_VND_ZANATA + ".version";
    public static final String APPLICATION_ZANATA_PROJECT_VERSION_JSON =
        APPLICATION_ZANATA_PROJECT_VERSION + JSON;

    public static final String APPLICATION_ZANATA_VERSION_LOCALES =
        APPLICATION_ZANATA_PROJECT_VERSION + ".locales";
    public static final String APPLICATION_ZANATA_VERSION_LOCALES_JSON =
        APPLICATION_ZANATA_VERSION_LOCALES + JSON;

    public static final String APPLICATION_ZANATA_TRANS_UNIT =
        APPLICATION_VND_ZANATA + ".tu";

    public static final String APPLICATION_ZANATA_TRANS_UNIT_RESOURCE_JSON =
        APPLICATION_ZANATA_TRANS_UNIT + ".resource" + JSON;

}
