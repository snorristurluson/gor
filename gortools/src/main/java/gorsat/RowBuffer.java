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

package gorsat;

import org.gorpipe.gor.model.Row;

import java.util.Iterator;

/**
 * An array of GOR row objects implementing iterator interface
 * The user class, BatchedReadSource, ensures thread safety
 * <p>
 * Created by sigmar on 24/11/2016.
 */
public class RowBuffer implements Iterator<Row> {
    static final int MAX_NUMBER_OF_ROWS = Integer.parseInt(System.getProperty("gor.rowbuffer.max_rows_buffered", "1024"));
    private static final int DEFAULT_MAX_BYTES_IN_BUFFER = Integer.parseInt(System.getProperty("gor.rowbuffer.max_bytes_buffered", "1073741824"));  // Default 1 GB
    private static final int NUM_LINES_TO_ESTIMATE_LINE_SIZE = Integer.parseInt(System.getProperty("gor.rowbuffer.lines_for_size_estimation", "100"));

    private final Row[] rowArray;
    private int count;
    private int idx;
    private RowBuffer next;
    private int capacity;
    private final int maxBytes;              // Maximum bytes used for buffering.
    private int byteCount;             // Used bytes (or currently an estimate of the used bytes).

    private int estimatedAvgLineSize;


    public RowBuffer(int capacity, RowBuffer next) {
        this(capacity, DEFAULT_MAX_BYTES_IN_BUFFER, next);
    }

    public RowBuffer(int capacity, int maxBytes, RowBuffer next) {
        rowArray = new Row[MAX_NUMBER_OF_ROWS];
        count = 0;
        byteCount = 0;
        idx = 0;
        this.maxBytes = maxBytes;
        this.capacity = capacity;
        this.next = next;
        estimatedAvgLineSize = 0;
    }

    public RowBuffer(RowBuffer next) {
        this(1, next);
    }

    public RowBuffer(int capacity) {
        this(capacity, null);
    }

    public RowBuffer() {
        this(null);
    }

    public Row[] getRowArray() {
        return rowArray;
    }

    public boolean containsEndRow() {
        return count > 0 && rowArray[count-1].pos == -1;
    }

    public void setNextRowBuffer(RowBuffer buffer) {
        this.next = buffer;
    }

    public RowBuffer nextRowBuffer() {
        next.count = 0;
        next.byteCount = 0;
        next.idx = 0;
        next.estimatedAvgLineSize = 0;
        return next;
    }

    public boolean enlarge(int newsize) {
        int oldcapacity = capacity;
        if( byteCount < maxBytes ) capacity = Math.min(newsize, rowArray.length);
        return capacity != oldcapacity;
    }

    public void reduce(int newsize) {
        capacity = Math.max(newsize, 1);
    }

    public Row get(int i) {
        return rowArray[i];
    }

    public void add(Row r) {
        rowArray[count++] = r;

        if (count < NUM_LINES_TO_ESTIMATE_LINE_SIZE) {
            int lineBytes = r.length();
            estimatedAvgLineSize = (count == 1) ? lineBytes : (estimatedAvgLineSize * (count - 1) + lineBytes) / count;
            byteCount += lineBytes;
        } else {
            byteCount += estimatedAvgLineSize;
        }
    }

    public Row pop() { return rowArray[--count]; }

    public boolean hasNext() {
        return available() && rowArray[idx].pos != -1;
    }

    public Row next() {
        return rowArray[idx++];
    }

    public synchronized void clear() {
        count = 0;
        byteCount = 0;
        idx = 0;
        estimatedAvgLineSize = 0;
    }

    public int getIndex() {
        return idx;
    }

    public boolean isWaiting() {
        return !available() && !isFull();
    }

    public boolean available() {
        return idx < count;
    }

    public boolean isFull() {
        return count == capacity || byteCount >= maxBytes;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public int size() {
        return count;
    }

    public int getCapacity() {
        return capacity;
    }
}
