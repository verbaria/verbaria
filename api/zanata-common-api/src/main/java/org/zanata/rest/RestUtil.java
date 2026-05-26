package org.zanata.rest;

public class RestUtil {

    public static String convertToDocumentURIId(String docId) {
        // NB this currently prevents us from allowing ',' in file names
        if (docId.startsWith("/")) {
            return docId.substring(1).replace('/', ',');
        }
        return docId.replace('/', ',');
    }

    public static String convertFromDocumentURIId(String docIdWithNoSlash) {
        return docIdWithNoSlash.replace(',', '/');
    }

}
