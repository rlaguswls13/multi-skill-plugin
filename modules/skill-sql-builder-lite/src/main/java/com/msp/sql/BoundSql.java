package com.msp.sql;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An immutable value record representing generated SQL text and its corresponding positional parameters.
 *
 * @param sql        the parameterized SQL query string (with ? placeholders)
 * @param parameters the ordered list of parameter arguments
 */
public record BoundSql(String sql, List<Object> parameters) {

    public BoundSql {
        Objects.requireNonNull(sql, "sql must not be null");
        parameters = (parameters == null) ? Collections.emptyList() : List.copyOf(parameters);
    }

    /**
     * Binds parameters directly to the given JDBC {@link PreparedStatement}.
     *
     * @param ps JDBC PreparedStatement to bind to
     * @throws SQLException if JDBC binding fails
     */
    public void bindTo(PreparedStatement ps) throws SQLException {
        SqlBinder.bind(ps, parameters);
    }

    /**
     * Returns the parameter count.
     *
     * @return number of bound parameters
     */
    public int parameterCount() {
        return parameters.size();
    }
}
