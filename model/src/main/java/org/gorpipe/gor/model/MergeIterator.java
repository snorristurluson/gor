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

import org.apache.commons.lang.ArrayUtils;
import org.gorpipe.exceptions.GorDataException;
import org.gorpipe.exceptions.GorSystemException;
import org.gorpipe.gor.session.GorContext;
import org.gorpipe.gor.monitor.GorMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * MergeIterator merges lines from multiple genomic iterators. All the iterators must have the same
 * layout, and lines are interleaved in genomic order. This is effectively doing a merge-sort on
 * the iterators.
 */
public class MergeIterator extends GenomicIterator {
    private static final Logger log = LoggerFactory.getLogger(MergeIterator.class);

    private static final String DEFAULT_SOURCE_COLUMN_NAME = "Source";
    private List<GenomicIterator> sources;
    /**
     * The queue stores rows from each source. The queue is initialized with one row
     * from each source, and when a row is pulled from the queue a new one is pulled
     * from the source where it came from.
     */
    private PriorityQueue<RowFromIterator> queue;
    /**
     * This flag controls whether a column should be added to each row with the name
     * of the of the source. Note that the source may already have the source column
     * added.
     */
    private final boolean insertSource;

    /**
     * Set once queue has been primed.
     */
    private boolean isPrimed = false;

    private boolean isClosed = false;

    /**
     * Optional GorMonitor instance, so that cancelling can be done while priming
     */
    private final GorMonitor gorMonitor;

    public MergeIterator(List<GenomicIterator> sources, boolean insertSource, String sourceColName, GorMonitor gm) {
        this.sources = sources;
        this.insertSource = insertSource;
        gorMonitor = gm;

        try {
            getHeaderFromSources(insertSource, sourceColName);
        } catch (Exception e) {
            try {
                doClose();
            } catch (Exception inner) {
                log.warn("Caught exception while closing when handling exception", inner);
            }
            throw e;
        }
    }

    @Override
    public GenomicIterator filter(Predicate<Row> rf) {
        this.sources = this.sources.stream().map(s -> s.filter(rf)).collect(Collectors.toList());
        return this;
    }

    @Override
    public void setContext(GorContext context) {
        statsSenderName = "MergeIterator";
        super.setContext(context);
        addStat("numSources", sources.size());
    }

    private static String[] getHeaderWithOptionalSourceColumn(boolean insertSource, String sourceColName, GenomicIterator i) {
        String[] header = i.getHeader().split("\t");
        if (insertSource) {
            String name = getSourceColumnName(sourceColName);
            if (i.isSourceAlreadyInserted()) {
                header = (String[]) ArrayUtils.clone(header);
                header[header.length - 1] = name;
            } else {
                header = (String[]) ArrayUtils.add(header, name);
            }
        }
        return header;
    }

    private static String getSourceColumnName(String sourceColName) {
        return sourceColName != null ? sourceColName : DEFAULT_SOURCE_COLUMN_NAME;
    }

    @Override
    public boolean seek(String chr, int pos) {
        incStat("seek");

        clearQueue();
        isPrimed = true;
        for (int itIdx = 0; itIdx < this.sources.size(); ++itIdx) {
            final GenomicIterator it = this.sources.get(itIdx);
            it.seek(chr, pos);
            addNextToQueue(itIdx);
        }

        return this.hasNext();
    }

    @Override
    public boolean hasNext() {
        incStat("hasNext");

        if (isClosed) {
            throw new GorSystemException("Iterator is closed", null);
        }

        if (!isPrimed) {
            primeQueue();
        }
        if (queue.size() > 0) {
            if (queue.peek().row.isProgress) {
                //The first row in the queue is a progress row.
                final RowFromIterator rowFromIterator = queue.poll();
                addNextToQueue(rowFromIterator.itIdx);
                return hasNext();
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    @Override
    public Row next() {
        incStat("next");

        if (isClosed) {
            throw new GorSystemException("Iterator is closed", null);
        }

        if (!isPrimed) {
            primeQueue();
        }
        RowFromIterator rowFromIterator = queue.poll();
        if (rowFromIterator == null || rowFromIterator.row == null) {
            throw new NoSuchElementException();
        }

        final int itIdx = rowFromIterator.itIdx;
        addNextToQueue(itIdx);

        return rowFromIterator.row;
    }

    @Override
    public boolean next(Line line) {
        throw new GorSystemException("next filling Line should not be used from MergeIterator", null);
    }

    @Override
    public void close() {
        doClose();
        isClosed = true;
    }

    private void doClose() {
        for (GenomicIterator it : sources) {
            it.close();
        }
    }

    private void getHeaderFromSources(boolean insertSource, String sourceColName) {
        String firstName = "";
        for (GenomicIterator it : this.sources) {
            String[] headerWithOptionalSourceColumn = getHeaderWithOptionalSourceColumn(insertSource, sourceColName, it);
            String header = getHeader();
            if (header.length() == 0) {
                setHeader(String.join("\t",headerWithOptionalSourceColumn));
                setColnum(headerWithOptionalSourceColumn.length - 2);
                firstName = it.getSourceName();
            } else {
                String[] headerSplit = header.split("\t");
                if (!areHeadersEqual(headerSplit, headerWithOptionalSourceColumn)) {
                    String message = "Error initializing query: Header for " + it.getSourceName() + " ("
                            + String.join(",", headerWithOptionalSourceColumn)
                            + ") is different from the first opened file "
                            + firstName + " (" + String.join(",", headerSplit) + ")";
                    throw new GorDataException(message);
                }
            }
            it.setColnum(getColnum());
        }
    }

    private boolean areHeadersEqual(String[] first, String[] second) {
        if (first.length != second.length) {
            return false;
        }
        for (int i = 0; i < first.length; i++) {
            if (!first[i].equalsIgnoreCase(second[i])) {
                return false;
            }
        }
        return true;
    }

    private void primeQueue() {
        isPrimed = true;
        clearQueue();
        for (int itIdx = 0; itIdx < this.sources.size(); ++itIdx) {
            if (gorMonitor != null && gorMonitor.isCancelled()) {
                return;
            }
            addNextToQueue(itIdx);
        }
    }

    private void clearQueue() {
        if (queue != null) {
            queue.clear();
        } else {
            queue = new PriorityQueue<>(sources.size());
        }
    }

    private void addNextToQueue(int itIdx) {
        final GenomicIterator it = this.sources.get(itIdx);
        if (it.hasNext()) {
            Row r = it.next();
            if (r == null) {
                String msg = String.format("Iterator next returned null after hasNext returned true (%s, %s)", it.getClass().getName(), it.getSourceName());
                throw new GorSystemException(msg, null);
            }
            if (insertSource && !it.isSourceAlreadyInserted()) {
                insertOptionalSourceColumn(r, it.getSourceName());
            }
            queue.add(new RowFromIterator(r, itIdx));
        }
    }

    private void insertOptionalSourceColumn(Row r, String s) {
        String[] header = getHeader().split("\t");
        if (r.numCols() == header.length) {
            r.setColumn(header.length - 3, s);
        } else {
            r.addSingleColumnToRow(s);
        }
    }

    static class RowFromIterator implements Comparable<RowFromIterator> {
        final Row row;
        final int itIdx;

        RowFromIterator(Row r, int itIdx) {
            this.row = r;
            this.itIdx = itIdx;
        }

        @Override
        public int compareTo(RowFromIterator rfi) {
            int chrCompare = this.row.chr.compareTo(rfi.row.chr);
            if (chrCompare == 0) {
                int posCompare = this.row.pos - rfi.row.pos;
                if (posCompare == 0) {
                    return Integer.compare(this.itIdx, rfi.itIdx);
                }
                return posCompare;
            }
            return chrCompare;
        }
    }
}