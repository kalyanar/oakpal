/*
 * Copyright 2018 Mark Adamcin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.adamcin.oakpal.core.jcrfacade.query;

import net.adamcin.oakpal.core.jcrfacade.NodeIteratorFacade;
import net.adamcin.oakpal.core.jcrfacade.SessionFacade;
import org.jetbrains.annotations.NotNull;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.QueryResult;
import javax.jcr.query.RowIterator;

/**
 * Wraps {@link QueryResult} to ensure returned objects are wrapped appropriately.
 */
public final class QueryResultFacade<S extends Session> implements QueryResult {
    private final @NotNull QueryResult delegate;
    private final @NotNull SessionFacade<S> session;

    @SuppressWarnings("WeakerAccess")
    public QueryResultFacade(final @NotNull QueryResult delegate, final @NotNull SessionFacade<S> session) {
        this.delegate = delegate;
        this.session = session;
    }

    @Override
    public String[] getColumnNames() throws RepositoryException {
        return delegate.getColumnNames();
    }

    @Override
    public RowIterator getRows() throws RepositoryException {
        RowIterator internal = delegate.getRows();
        return new RowIteratorFacade<>(internal, session);
    }

    @Override
    public NodeIterator getNodes() throws RepositoryException {
        NodeIterator internal = delegate.getNodes();
        return new NodeIteratorFacade<>(internal, session);
    }

    @Override
    public String[] getSelectorNames() throws RepositoryException {
        return delegate.getSelectorNames();
    }
}
