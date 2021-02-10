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

package org.gorpipe.gor.driver.providers.stream.datatypes.gor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GorHeader {
    private final List<String> columns = new ArrayList<>();
    private final List<String> types = new ArrayList<>();

    public GorHeader() {}
    public GorHeader(String[] columns) {
        Collections.addAll(this.columns, columns);
    }

    public String[] getColumns() {
        return columns.toArray(new String[columns.size()]);
    }
    public String[] getTypes() {
        return types.toArray(new String[types.size()]);
    }

    public void addColumn(String name) {
        addColumn(name, "");
    }

    public void addColumn(String name, String tpe) {
        columns.add(name);
        types.add(tpe);
    }

    public GorHeader select(int[] colIndices) {
        final GorHeader toReturn = new GorHeader();
        for (int idx : colIndices) {
            toReturn.addColumn(this.columns.get(idx));
        }
        return toReturn;
    }

    @Override
    public String toString() {
        return String.join(", ", columns);
    }
}
