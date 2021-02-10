/*
 *  BEGIN_COPYRIGHT
 *
 *  Copyright (C) 2011-2013 deCODE genetics Inc.
 *  Copyright (C) 2013-2019 WuXi NextCode Inc.
 *  All Rights Reserved.
 *
 *  GORpipe is free software: you can redistribute it and/or modify
 *  it under the terms of the AFFERO GNU General Public License as published by
 *  the Free Software Foundation.
 *
 *  GORpipe is distributed "AS-IS" AND WITHOUT ANY WARRANTY OF ANY KIND,
 *  INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 *  NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR PURPOSE. See
 *  the AFFERO GNU General Public License for the complete license terms.
 *
 *  You should have received a copy of the AFFERO GNU General Public License
 *  along with GORpipe.  If not, see <http://www.gnu.org/licenses/agpl-3.0.html>
 *
 *  END_COPYRIGHT
 */

package org.gorpipe.gor.model;

import org.gorpipe.gor.db.ConnectionPool;
import org.gorpipe.util.Pair;

import java.sql.*;
import java.util.ArrayList;
import java.util.Iterator;


/**
 * String Iterator for DB statement results from queries that are not genomic-ordered
 * and do thus not support seek.
 */
public class DbNorIterator implements Iterator<String>, AutoCloseable {

    private static final String[] VARS = {"project-id"};

    private Connection conn = null;
    private PreparedStatement stmt = null;
    private ResultSet rs = null;
    private ResultSetMetaData meta;
    private int rowNum = 0;
    private boolean rowWaiting = true;


    /**
     * Construct DbNorIterator for access to database tables and views that are not
     * in genomic order.
     *
     * @param content
     * @param constants
     * @param pool
     */
    public DbNorIterator(String content, Object[] constants, ConnectionPool pool) {
        // Assume content is on the form //db:query.
        // TODO: Add checking here on content starting with //db:
        String sql = content.substring(5);

        // Replace scoping variables.
        Pair<String, Object[]> sqlWithParams = replaceConstants(sql, constants);

        // Get db connection.
        try {
            conn = pool.getConnection();
        } catch (Exception ex) {
            rethrow(ex);
        }

        // Run the query and return results.
        try {
            stmt = prepareStatement(conn, sqlWithParams.getFormer(), sqlWithParams.getLatter());
            stmt.setFetchSize(2000);
            rs = stmt.executeQuery();
            meta = rs.getMetaData();
        } catch (Exception ex) {
            rethrow(ex);
        }
    }

    /**
     * Prepare statement and bind all required values.
     *
     * @param c
     * @param sql
     * @param values
     * @return
     * @throws SQLException
     */
    @SuppressWarnings("squid:S2095") //resource is a return object and should not be closed here
    private static PreparedStatement prepareStatement(Connection c, String sql, Object... values) throws SQLException {
        final PreparedStatement ps = c.prepareStatement(sql);
        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                ps.setObject(i + 1, values[i]);
            }
        }
        return ps;
    }

    /**
     * Replace query constants using the VARS list of usual suspects and populate a bind array with
     * the associated values for use in prepareStatement.
     *
     * @param sql
     * @param constants
     * @return
     */
    // TODO: Clean up this code.
    private static Pair<String, Object[]> replaceConstants(final String sql, final Object[] constants) {
        final ArrayList<Object> values = new ArrayList<>();
        final StringBuilder sb = new StringBuilder(sql);
        if (constants != null && constants.length > 0) {
            int idx = sb.indexOf("#{");    // Find and replace all #{...} variables that are defined
            while (idx >= 0) {
                final int start = idx + 2; // The start of the variable name
                for (int i = 0; i < VARS.length; i++) {
                    final int end = start + VARS[i].length();
                    if (end + 1 <= sb.length()) {
                        if (VARS[i].equals(sb.substring(start, end)) && sb.charAt(end) == '}') {
                            // Found a variable, must replace sql text with ? and add value to value list
                            if (i >= constants.length) {
                                throw new RuntimeException("Unexpected variable with index " + i);
                            }
                            values.add(constants[i]);
                            sb.replace(start - 2, end + 1, "?");
                            break;
                        }
                    }
                }
                idx = sb.indexOf("#{", idx);
            }
        }
        return new Pair<>(sb.toString(), values.toArray());
    }


    /**
     * Returns true if there are more rows in the ResultSet.
     *
     * @return boolean <code>true</code> if there are more rows
     * @throws RuntimeException if an SQLException occurs.
     */
    public boolean hasNext() {
        return rowWaiting;
    }

    /**
     * Returns the next row as an <code>Object[]</code>.
     *
     * @return An <code>Object[]</code> with the same number of elements as
     * columns in the <code>ResultSet</code>.
     * @throws RuntimeException if an SQLException occurs.
     * @see java.util.Iterator#next()
     */
    public String next() {
        try {
            if (rowNum++ == 0) {
                rowWaiting = rs.next();
                return gorHeader();
            } else {
                String gorLine = toGorLine(rs);
                rowWaiting = rs.next();
                return gorLine;
            }
        } catch (SQLException e) {
            rethrow(e);
            return null;
        }
    }

    /**
     * Deletes the current row from the <code>ResultSet</code>.
     *
     * @throws RuntimeException if an SQLException occurs.
     * @see java.util.Iterator#remove()
     */
    public void remove() {
        try {
            this.rs.deleteRow();
        } catch (SQLException e) {
            rethrow(e);
        }
    }

    /**
     * Rethrow the SQLException as a RuntimeException.  This implementation
     * creates a new RuntimeException with the SQLException's error message.
     *
     * @param e SQLException to rethrow
     */
    protected void rethrow(Exception e) {
        throw new RuntimeException(e.getMessage());
    }

    @Override
    public void close() {
        try {
            try {
                try {
                    if (rs != null) rs.close();
                } catch (Exception ex) {
                    rethrow(ex);
                }
                if (stmt != null) stmt.close();
            } catch (Exception ex) {
                rethrow(ex);
            }
            if (conn != null) conn.close();
        } catch (Exception ex) {
            rethrow(ex);
        } finally {
            rs = null;
            stmt = null;
            conn = null;
        }
    }

    /**
     * Convert a ResultSet row into a GOR line String.
     */
    public String toGorLine(ResultSet rs) throws SQLException {
        StringBuilder sb = new StringBuilder();
        int cols = meta.getColumnCount();
        String sep = "";
        for (int i = 0; i < cols; i++) {
            sb.append(sep).append(removeInvalidCharacters(rs.getString(i + 1)));
            sep = "\t";
        }
        return sb.toString();
    }

    /**
     * Compose GOR header from ResultSet metadata.
     */
    public String gorHeader() throws SQLException {
        StringBuilder sb = new StringBuilder();
        int cols = meta.getColumnCount();

        String sep = "#";
        for (int i = 0; i < cols; i++) {
            sb.append(sep).append(removeInvalidCharacters(meta.getColumnName(i + 1)));
            sep = "\t";
        }

        return sb.toString();
    }

    /**
     * Make sure that column data does not contain any characters that have functional
     * significance in the system.
     *
     * @param val
     * @return
     */
    private String removeInvalidCharacters(String val) {
        if (val != null) {
            return val.replace('\t', ' ').replace('\n', ' ').replace('\r', ' '); // tabs and newlines will make gor parsing go haywire
        }
        return "";
    }

}
