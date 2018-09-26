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

import com.carrotsearch.hppc.ObjectIntHashMap;
import com.carrotsearch.hppc.ObjectIntMap;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.function.Function;

final class ForeignOidIterable<T, F> implements Iterable<ForeignOidProvider<T>> {

    private final OidIterable<T> delegate;
    private final OidIterable<F> foreignIterable;
    private final Function<T, Object> foreignColumnGetter;
    private final Function<F, Object> foreignTableColumnGetter;

    ForeignOidIterable(OidIterable<T> delegate,
                       OidIterable<F> foreignIterable,
                       Function<T, Object> foreignColumnGetter,
                       Function<F, Object> foreignTableColumnGetter) {
        this.delegate = delegate;
        this.foreignIterable = foreignIterable;
        this.foreignColumnGetter = foreignColumnGetter;
        this.foreignTableColumnGetter = foreignTableColumnGetter;
    }

    @Nonnull
    @Override
    public Iterator<ForeignOidProvider<T>> iterator() {
        ObjectIntMap<Object> foreignOidMap = buildForeignOidMap();
        Iterator<OidProvider<T>> delegateIt = delegate.iterator();
        return new Iterator<ForeignOidProvider<T>>() {
            @Override
            public boolean hasNext() {
                return delegateIt.hasNext();
            }

            @Override
            public ForeignOidProvider<T> next() {
                OidProvider<T> oidProvider = delegateIt.next();
                Object foreignVal = foreignColumnGetter.apply(oidProvider.delegate());
                return new ForeignOidProvider<>(oidProvider, foreignOidMap.get(foreignVal));
            }
        };
    }

    private ObjectIntMap<Object> buildForeignOidMap() {
        ObjectIntMap<Object> map = new ObjectIntHashMap<>();
        for (OidProvider<F> wrapper : foreignIterable) {
            Object foreignValue = foreignTableColumnGetter.apply(wrapper.delegate());
            int foreignOid = wrapper.oid();
            map.put(foreignValue, foreignOid);
        }
        return map;
    }
}
