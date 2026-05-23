package org.zanata.database;

import org.hibernate.dialect.H2Dialect;

// This class is a workaround for https://hibernate.atlassian.net/browse/HHH-7002
public class CustomH2Dialect extends H2Dialect {

    // getDropSequenceString(String) was removed in Hibernate ORM 6; sequence
    // drop SQL is now produced via the standard SequenceSupport SPI.

    @Override
    public boolean dropConstraints() {
        return false;
    }

    @Override
    public boolean supportsIfExistsBeforeTableName() {
        return true;
    }

    @Override
    public boolean supportsIfExistsAfterTableName() {
        return false;
    }

    @Override
    public String getCascadeConstraintsString() {
        return " CASCADE ";
    }
}
