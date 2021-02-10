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

package org.gorpipe.gor.binsearch;

import org.gorpipe.exceptions.GorException;
import org.gorpipe.exceptions.GorResourceException;
import org.gorpipe.exceptions.GorSystemException;
import org.gorpipe.gor.driver.adapters.StreamSourceSeekableFile;
import org.gorpipe.gor.driver.providers.stream.datatypes.gor.GorHeader;
import org.gorpipe.gor.model.GenomicIterator;
import org.gorpipe.gor.model.Line;
import org.gorpipe.gor.model.Row;
import org.gorpipe.model.gor.RowObj;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class GorSeekableIterator extends GenomicIterator {
    private static final Logger log = LoggerFactory.getLogger(GorSeekableIterator.class);

    private final SeekableIterator iterator;
    private final String filePath;
    private GorHeader header;

    public GorSeekableIterator(StreamSourceSeekableFile file) {
        try {
            this.filePath = file.getCanonicalPath();
            this.iterator = new SeekableIterator(file, true);
        } catch (IOException e) {
            throw wrapIOException(e);
        }
        final String headerAsString = this.iterator.getHeader();
        setGorHeader(headerAsString);
    }

    private GorException wrapIOException(IOException e) {
        if (e.getMessage().equals("Stale file handle")) {
            return new GorSystemException("Stale file handle reading " + this.filePath, e);
        } else {
            return new GorResourceException("Error reading gorz file: " + e.getMessage(), this.filePath, e);
        }
    }

    @Override
    public String getHeader() {
        return String.join("\t",this.header.getColumns());
    }

    @Override
    public boolean seek(String chr, int pos) {
        try {
            this.iterator.seek(new StringIntKey(chr, pos));
            return this.iterator.hasNext();
        } catch (IOException e) {
            throw wrapIOException(e);
        }
    }

    @Override
    public boolean hasNext() {
        return this.iterator.hasNext();
    }

    @Override
    public Row next() {
        try {
            return RowObj.apply(this.iterator.getNextAsString());
        } catch (IOException e) {
            throw wrapIOException(e);
        } catch (NumberFormatException e) {
            throw new GorResourceException("Invalid row", this.filePath, e);
        }
    }

    @Override
    public boolean next(Line line) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
        try {
            this.iterator.close();
        } catch (IOException e) {
            log.warn(e.getMessage());
        }
    }

    private void setGorHeader(String headerAsString) {
        int i = 0;
        while (i < headerAsString.length() && headerAsString.charAt(i) == '#') ++i;

        String headerString = headerAsString.substring(i);
        this.header = new GorHeader(headerString.split("\t"));
        setHeader(headerString);
    }

    @Override
    protected void selectHeader(int[] cols) {
        this.header = this.header.select(cols);
    }
}

