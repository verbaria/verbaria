package org.hibernate.type;
import org.hibernate.usertype.UserType;
import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
/**
 * Stub for Hibernate 5 AbstractSingleColumnStandardBasicType. Implements the
 * current UserType interface so @Type(X.class) compiles. Not functionally
 * complete — DB persistence of custom enum types is broken until ported.
 */
public abstract class AbstractSingleColumnStandardBasicType<T> implements UserType<T> {
    protected AbstractSingleColumnStandardBasicType(Object sqlTypeDescriptor, Object javaTypeDescriptor) {}
    public String getName() { return getClass().getSimpleName(); }
    @SuppressWarnings("unchecked")
    public T fromStringValue(CharSequence sequence) { return null; }
    public String toString(T value) { return value == null ? null : value.toString(); }
    public String objectToSQLString(T value, org.hibernate.dialect.Dialect dialect) {
        return value == null ? "null" : "'" + value + "'";
    }
    public T stringToObject(String xml) { return null; }
    // UserType methods (stubbed)
    @Override public int getSqlType() { return Types.VARCHAR; }
    @Override public Class<T> returnedClass() { return null; }
    @Override public boolean equals(T x, T y) { return java.util.Objects.equals(x, y); }
    @Override public int hashCode(T x) { return x == null ? 0 : x.hashCode(); }
    @Override public T nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException { return null; }
    @Override public void nullSafeSet(PreparedStatement st, T value, int index, SharedSessionContractImplementor session) throws SQLException {}
    @Override public T deepCopy(T value) { return value; }
    @Override public boolean isMutable() { return false; }
    @Override public Serializable disassemble(T value) { return (Serializable) value; }
    @Override @SuppressWarnings("unchecked")
    public T assemble(Serializable cached, Object owner) { return (T) cached; }
}
