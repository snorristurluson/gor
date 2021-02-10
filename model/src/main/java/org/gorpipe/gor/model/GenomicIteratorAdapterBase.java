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

import org.gorpipe.gor.session.GorSession;

import java.util.function.Predicate;

/**
 * Abstract adapter base class for GenomicIterator to make it easier to
 * create wrapper classes without having to implement all the GenomicIterator methods.
 *
 * NOTE:
 * 1. This class should wrap ALL the methods of GenomicIterator, so if adding methods to
 *    GenomicIterator those should be added here too.
 *
 * 2. We try to make all the props of the wrapper and the
 *    underlying iterator to be in sync but these can go out of
 *    sync if properties are set directly on the underlying iterator
 *    so if wrapped the underlying iterator should not be changed directly.
 *
 *
 */
public abstract class GenomicIteratorAdapterBase extends GenomicIterator {

    GenomicIterator iterator;

    protected GenomicIteratorAdapterBase() {
    }

    public GenomicIteratorAdapterBase(GenomicIterator it) {
        this.setIterator(it);
    }

    protected void setIterator(GenomicIterator it) {
        if (this.iterator != null) {
            this.iterator.close();
        }
        iterator = it;
        setColnum(it.getColnum());
        setSourceAlreadyInserted(it.isSourceAlreadyInserted());
        setSourceName(it.getSourceName());
    }

    @Override
    public String getSourceName() {
        return iterator.getSourceName();
    }

    @Override
    public void setSourceName(String sourceName) {
        super.setSourceName(sourceName);
        iterator.setSourceName(sourceName);
    }

    @Override
    public boolean isSourceAlreadyInserted() {
        return iterator.isSourceAlreadyInserted();
    }

    @Override
    public void setSourceAlreadyInserted(boolean sourceAlreadyInserted) {
        super.setSourceAlreadyInserted(sourceAlreadyInserted);
        iterator.setSourceAlreadyInserted(sourceAlreadyInserted);
    }

    @Override
    public int getColnum() {
        return iterator.getColnum();
    }

    @Override
    public void setColnum(int colnum) {
        super.setColnum(colnum);
        iterator.setColnum(colnum);
    }

    @Override
    public ChromoLookup getLookup() {
        return iterator.getLookup();
    }

    @Override
    public String getHeader() {
        return iterator.getHeader();
    }

    @Override
    public void init(GorSession session) {
        iterator.init(session);
    }

    @Override
    public boolean seek(String chr, int pos) {
        return iterator.seek(chr, pos);
    }

    @Override
    public boolean seek(String chr, int pos, int end) {
        return iterator.seek(chr, pos, end);
    }

    @Override
    public boolean next(Line line) {
        return iterator.next(line);
    }

    @Override
    public void close() {
        iterator.close();
    }

    @Override
    public ResourceMonitor getMonitor() {
        return super.getMonitor();
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public Row next() {
        return iterator.next();
    }

    @Override
    public GenomicIterator filter(Predicate<Row> rf) {
        this.iterator = this.iterator.filter(rf);
        return this;
    }

    @Override
    public GenomicIterator select(int[] cols) {
        this.iterator = this.iterator.select(cols);
        return this;
    }

    @Override
    protected void selectHeader(int[] cols) {
        this.iterator.selectHeader(cols);
    }

    @Override
    public boolean pushdownFilter(String where) {
        return iterator.pushdownFilter(where);
    }

    @Override
    public boolean pushdownSelect(String[] colList) {
        return iterator.pushdownSelect(colList);
    }

    @Override
    public boolean pushdownCalc(String formula, String colName) {
        return iterator.pushdownCalc(formula, colName);
    }

    @Override
    public boolean pushdownGor(String gor) {
        return iterator.pushdownGor(gor);
    }

    @Override
    public boolean pushdownTop(int limit) {
        return iterator.pushdownTop(limit);
    }

    @Override
    public boolean pushdownWrite(String write) {
        return iterator.pushdownWrite(write);
    }
}
