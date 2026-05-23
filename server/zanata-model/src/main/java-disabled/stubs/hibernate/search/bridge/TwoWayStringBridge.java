package org.hibernate.search.bridge;
public interface TwoWayStringBridge extends StringBridge {
    Object stringToObject(String stringValue);
}
