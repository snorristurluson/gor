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

package org.gorpipe.gor.driver.meta;

import org.gorpipe.gor.model.GenomicIterator;

/**
 * Builder for the SourceReference, use builder copy constructor to allow copying fields from parent SourceReference.
 */
public class SourceReferenceBuilder {
    private final String url;
    private String securityContext;
    private String commonRoot;
    private GenomicIterator.ChromoLookup lookup;
    private String chrSubset;

    public SourceReferenceBuilder(String url) {
        this.url = url;
    }

    public SourceReferenceBuilder(String url, SourceReference parentSourceReference) {
        this.url = url;
        // Don't copy objects, we want to share the instance with the parent.
        this.securityContext = parentSourceReference.securityContext;
        this.commonRoot = parentSourceReference.commonRoot;
        this.lookup = parentSourceReference.lookup;
        this.chrSubset = parentSourceReference.chrSubset;
    }

    public SourceReference build() {
        return new SourceReference(url, securityContext, commonRoot, lookup, chrSubset);
    }

    public SourceReferenceBuilder securityContext(String securityContext) {
        this.securityContext = securityContext;
        return this;
    }

    public SourceReferenceBuilder commonRoot(String commonRoot) {
        this.commonRoot = commonRoot;
        return this;
    }

    public SourceReferenceBuilder lookup(GenomicIterator.ChromoLookup lookup) {
        this.lookup = lookup;
        return this;
    }

    public SourceReferenceBuilder chrSubset(String chrSubset) {
        this.chrSubset = chrSubset;
        return this;
    }
}
