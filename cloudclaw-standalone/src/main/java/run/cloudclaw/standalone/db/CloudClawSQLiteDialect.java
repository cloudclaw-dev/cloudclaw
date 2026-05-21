package run.cloudclaw.standalone.db;

import org.hibernate.community.dialect.SQLiteDialect;
import org.hibernate.type.SqlTypes;

/**
 * Hibernate dialect for SQLite that stores UUID as TEXT instead of binary.
 */
public class CloudClawSQLiteDialect extends SQLiteDialect {

    public CloudClawSQLiteDialect() {
        super();
    }

    @Override
    protected String columnType(int sqlTypeCode) {
        if (sqlTypeCode == SqlTypes.UUID) {
            return "text";
        }
        return super.columnType(sqlTypeCode);
    }

    @Override
    protected String castType(int sqlTypeCode) {
        if (sqlTypeCode == SqlTypes.UUID) {
            return "text";
        }
        return super.castType(sqlTypeCode);
    }
}
