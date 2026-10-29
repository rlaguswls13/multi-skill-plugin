package com.msp.sql;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Utility for binding positional parameters to JDBC {@link PreparedStatement}.
 */
public final class SqlBinder {

    private SqlBinder() {
    }

    /**
     * Binds a list of parameters to a JDBC {@link PreparedStatement} using 1-based indexing.
     *
     * @param ps         the PreparedStatement (must not be null)
     * @param parameters list of values to bind
     * @throws SQLException if JDBC binding fails
     */
    public static void bind(PreparedStatement ps, List<Object> parameters) throws SQLException {
        if (ps == null || parameters == null) {
            return;
        }

        for (int i = 0; i < parameters.size(); i++) {
            ps.setObject(i + 1, parameters.get(i));
        }
    }
}
