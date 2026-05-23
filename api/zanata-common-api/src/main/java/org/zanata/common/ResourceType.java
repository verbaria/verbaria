package org.zanata.common;

import jakarta.xml.bind.annotation.XmlType;

@XmlType(name = "resourceEnumType")
public enum ResourceType {
    FILE, DOCUMENT, PAGE;
}
