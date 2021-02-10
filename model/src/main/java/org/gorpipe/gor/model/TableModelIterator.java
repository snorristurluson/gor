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

import org.gorpipe.gor.binsearch.StringIntKey;
import org.gorpipe.model.gor.RowObj;

import javax.swing.table.TableModel;

/**
 * @version $Id:  $
 */
public class TableModelIterator extends GenomicIterator {
    private final TableModel tableModel;
    Row nextRow = null;

    private int currentRowId;
    private final GenomicIterator.ChromoLookup lookup;

    private static class DefaultLookup {
        final ChromoCache chrcache = new ChromoCache();
        public final GenomicIterator.ChromoLookup chrlookup = new GenomicIterator.ChromoLookup() {
            @Override
            public String idToName(int id) {
                return chrcache.toName(id);
            }

            @Override
            public int chrToId(String chr) {
                return chrcache.toIdOrUnknown(chr, true);
            }

            @Override
            public int chrToLen(String chr) {
                return chrcache.toLen(chr);
            }

            @Override
            public int chrToId(CharSequence str, int strlen) {
                return chrcache.toIdOrUnknown(str, strlen, true);
            }

            @Override
            public int prefixedChrToId(byte[] buf, int offset) {
                return chrcache.prefixedChrToIdOrUnknown(buf, offset, true);
            }

            @Override
            public int prefixedChrToId(byte[] buf, int offset, int buflen) {
                return chrcache.prefixedChrToIdOrUnknown(buf, offset, buflen, true);
            }

            @Override
            public ChromoCache getChromCache() {
                return chrcache;
            }
        };
    }

    /**
     * @param model the table model
     */
    public TableModelIterator(TableModel model) {
        this(model, new DefaultLookup().chrlookup);
    }

    /**
     * @param model  table model
     * @param lookup chromosome lookup
     */
    public TableModelIterator(TableModel model, GenomicIterator.ChromoLookup lookup) {
        this.tableModel = model;
        this.currentRowId = 0;
        this.lookup = lookup;
    }

    @Override
    public String getHeader() {
        String[] header = new String[getModel().getColumnCount()];
        for (int i = 0; i < getModel().getColumnCount(); i++) {
            header[i] = getModel().getColumnName(i);
        }
        return String.join("\t",header);
    }

    @Override
    public boolean seek(String ch, int ps) {
        currentRowId = binarySearch(ch, ps);
        return currentRowId >= 0 && currentRowId < getModel().getRowCount();
    }

    private TableModel getModel() {
        return tableModel;
    }

    private int binarySearch(String searchChr, int searchPos) {
        int lo = 0;
        TableModel model = getModel();
        int hi = model.getRowCount() - 1;
        StringIntKey searchKey = new StringIntKey(searchChr, searchPos);
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            String chrInRow = model.getValueAt(mid, 0).toString();
            int posInRow = Integer.parseInt(model.getValueAt(mid, 1).toString());
            StringIntKey rowKey = new StringIntKey(chrInRow, posInRow);
            int rank = searchKey.compareTo(rowKey);
            if (rank < 0) {
                hi = mid - 1;
            } else if (rank > 0) {
                lo = mid + 1;
            } else {
                return mid;
            }
        }
        int res = 0;
        if (lo >= 0) res = lo;
        return res;
    }

    @Override
    public void close() {
        currentRowId = 0;
    }

    @Override
    public boolean hasNext() {
        if (nextRow != null) {
            return true;
        }
        nextRow = next();
        return nextRow != null;
    }

    @Override
    public Row next() {
        if (nextRow != null) {
            Row result = nextRow;
            nextRow = null;
            return result;
        }

        boolean hasNext = true;

        if (currentRowId < 0 || currentRowId >= getModel().getRowCount()) {
            hasNext = false;
        }

        if (currentRowId >= getModel().getRowCount()) {
            hasNext = false;
        }
        if (!hasNext) {
            return null;
        }
        String chr = getModel().getValueAt(currentRowId, 0).toString();
        int pos = Integer.parseInt(getModel().getValueAt(currentRowId, 1).toString());

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(chr);
        stringBuilder.append("\t");
        stringBuilder.append(pos);
        stringBuilder.append("\t");

        for (int c = 2; c < getModel().getColumnCount(); c++) {
            String s = getModel().getValueAt(currentRowId, c).toString();
            stringBuilder.append(s);
            stringBuilder.append("\t");
        }
        currentRowId++;
        return RowObj.apply(stringBuilder);
    }

    @Override
    public boolean next(Line line) {
        throw new UnsupportedOperationException();
    }
}
