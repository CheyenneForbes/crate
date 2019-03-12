/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.metadata.shard;

import com.google.common.collect.ImmutableMap;
import io.crate.exceptions.ResourceUnknownException;
import io.crate.exceptions.UnhandledServerException;
import io.crate.execution.engine.collect.NestableCollectExpression;
import io.crate.expression.NestableInput;
import io.crate.expression.reference.LiteralNestableInput;
import io.crate.expression.reference.ReferenceResolver;
import io.crate.expression.reference.StaticTableReferenceResolver;
import io.crate.expression.reference.sys.shard.ShardRowContext;
import io.crate.metadata.IndexParts;
import io.crate.metadata.MapBackedRefResolver;
import io.crate.metadata.PartitionName;
import io.crate.metadata.Reference;
import io.crate.metadata.ReferenceIdent;
import io.crate.metadata.RelationName;
import io.crate.metadata.Schemas;
import io.crate.metadata.doc.DocTableInfo;
import io.crate.metadata.sys.SysShardsTableInfo;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.index.Index;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ShardReferenceResolver implements ReferenceResolver<NestableInput<?>> {

    private static final Logger LOGGER = Loggers.getLogger(ShardReferenceResolver.class);
    private static final StaticTableReferenceResolver<ShardRowContext> SHARD_REFERENCE_RESOLVER_DELEGATE =
        new StaticTableReferenceResolver<>(SysShardsTableInfo.expressions());
    private static final ReferenceResolver<NestableInput<?>> EMPTY_RESOLVER =
        new MapBackedRefResolver(Collections.emptyMap());

    private static ReferenceResolver<NestableInput<?>> createPartitionColumnResolver(Index index, Schemas schemas) {
        ImmutableMap.Builder<ReferenceIdent, NestableInput> builder = ImmutableMap.builder();
        PartitionName partitionName;
        try {
            partitionName = PartitionName.fromIndexOrTemplate(index.getName());
        } catch (IllegalArgumentException e) {
            throw new UnhandledServerException(String.format(Locale.ENGLISH,
                "Unable to load PARTITIONED BY columns from partition %s", index.getName()), e);
        }
        RelationName relationName = partitionName.relationName();
        try {
            DocTableInfo info = schemas.getTableInfo(relationName);
            if (!schemas.isOrphanedAlias(info)) {
                assert info.isPartitioned() : "table must be partitioned";
                int i = 0;
                int numPartitionedColumns = info.partitionedByColumns().size();

                List<BytesRef> partitionValue = partitionName.values();
                assert partitionValue.size() ==
                       numPartitionedColumns : "invalid number of partitioned columns";
                for (Reference partitionedInfo : info.partitionedByColumns()) {
                    builder.put(
                        partitionedInfo.ident(),
                        new LiteralNestableInput<>(partitionedInfo.valueType().value(partitionValue.get(i)))
                    );
                    i++;
                }
            } else {
                LOGGER.error("Orphaned partition '{}' with missing table '{}' found", index, relationName.fqn());
            }
        } catch (ResourceUnknownException e) {
            LOGGER.error("Orphaned partition '{}' with missing table '{}' found", index, relationName.fqn());
        }
        return new MapBackedRefResolver(builder.build());
    }

    private final ShardRowContext shardRowContext;
    private final ReferenceResolver<NestableInput<?>> partitionColumnResolver;

    public ShardReferenceResolver(Schemas schemas, ShardRowContext shardRowContext) {
        this.shardRowContext = shardRowContext;
        IndexParts indexParts = shardRowContext.indexParts();
        if (indexParts.isPartitioned()) {
            partitionColumnResolver = createPartitionColumnResolver(
                shardRowContext.indexShard().shardId().getIndex(), schemas);
        } else {
            partitionColumnResolver = EMPTY_RESOLVER;
        }
    }

    @Override
    public NestableInput<?> getImplementation(Reference ref) {
        NestableInput<?> partitionColImpl = partitionColumnResolver.getImplementation(ref);
        if (partitionColImpl != null) {
            return partitionColImpl;
        }
        NestableCollectExpression<ShardRowContext, ?> impl = SHARD_REFERENCE_RESOLVER_DELEGATE.getImplementation(ref);
        impl.setNextRow(shardRowContext);
        return impl;
    }
}
