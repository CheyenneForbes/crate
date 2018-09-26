/*
 * Licensed to Crate under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Crate licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial
 * agreement.
 */

package io.crate.metadata.pgcatalog;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

final class OidIterable<T> implements Iterable<OidProvider<T>>  {

    private final Iterable<T> delegate;

    OidIterable(Iterable<T> delegate) {
        this.delegate = delegate;
    }

    @Nonnull
    @Override
    public Iterator<OidProvider<T>> iterator() {
        AtomicInteger oid = new AtomicInteger();
        return new Iterator<OidProvider<T>>() {

            Iterator<T> it = delegate.iterator();

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public OidProvider<T> next() {
                return new OidProvider<>(oid.getAndIncrement(), it.next());
            }
        };
    }
}
