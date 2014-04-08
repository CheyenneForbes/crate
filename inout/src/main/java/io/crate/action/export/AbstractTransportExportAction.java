/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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

package io.crate.action.export;

import io.crate.action.export.parser.IExportParser;
import io.crate.export.Exporter;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.ShardOperationFailedException;
import org.elasticsearch.action.support.DefaultShardOperationFailedException;
import org.elasticsearch.action.support.broadcast.BroadcastShardOperationFailedException;
import org.elasticsearch.action.support.broadcast.TransportBroadcastOperationAction;
import org.elasticsearch.cache.recycler.CacheRecycler;
import org.elasticsearch.cache.recycler.PageCacheRecycler;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.block.ClusterBlockLevel;
import org.elasticsearch.cluster.routing.GroupShardsIterator;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.BigArrays;
import org.elasticsearch.env.NodeEnvironment;
import org.elasticsearch.index.service.IndexService;
import org.elasticsearch.index.shard.service.IndexShard;
import org.elasticsearch.indices.IndicesService;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.search.SearchShardTarget;
import org.elasticsearch.search.internal.SearchContext;
import org.elasticsearch.search.internal.ShardSearchRequest;
import org.elasticsearch.search.query.QueryPhaseExecutionException;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static com.google.common.collect.Lists.newArrayList;


/**
 *
 */
public abstract class AbstractTransportExportAction extends TransportBroadcastOperationAction<ExportRequest, ExportResponse, ShardExportRequest, ShardExportResponse> {

    private final IndicesService indicesService;

    private final ScriptService scriptService;

    private final IExportParser exportParser;

    private final Exporter exporter;

    private final CacheRecycler cacheRecycler;

    private final PageCacheRecycler pageRecycler;

    private final BigArrays bigArrays;

    private String nodePath;

    public AbstractTransportExportAction(Settings settings, ThreadPool threadPool, ClusterService clusterService,
                                         TransportService transportService, IndicesService indicesService,
                                         ScriptService scriptService, CacheRecycler cacheRecycler,
                                         PageCacheRecycler pageRecycler, BigArrays bigArrays,
                                         IExportParser exportParser, Exporter exporter,
                                         NodeEnvironment nodeEnv) {
        super(settings, threadPool, clusterService, transportService);
        this.indicesService = indicesService;
        this.scriptService = scriptService;
        this.cacheRecycler = cacheRecycler;
        this.pageRecycler = pageRecycler;
        this.bigArrays = bigArrays;
        this.exportParser = exportParser;
        this.exporter = exporter;
        File[] paths = nodeEnv.nodeDataLocations();
        if (paths.length > 0) {
            nodePath = paths[0].getAbsolutePath();
        }
    }

    @Override
    protected String executor() {
        return ThreadPool.Names.SEARCH;
    }

    @Override
    protected ExportRequest newRequest() {
        return new ExportRequest();
    }

    @Override
    protected ShardExportRequest newShardRequest() {
        return new ShardExportRequest();
    }

    @Override
    protected ShardExportRequest newShardRequest(ShardRouting shard, ExportRequest request) {
        String[] filteringAliases = clusterService.state().metaData().filteringAliases(shard.index(), request.indices());
        return new ShardExportRequest(shard.index(), shard.id(), filteringAliases, request);
    }

    @Override
    protected ShardExportResponse newShardResponse() {
        return new ShardExportResponse();
    }

    @Override
    protected GroupShardsIterator shards(ClusterState clusterState, ExportRequest request, String[] concreteIndices) {
        Map<String, Set<String>> routingMap = clusterState.metaData().resolveSearchRouting(request.routing(), request.indices());
        return clusterService.operationRouting().searchShards(clusterState, request.indices(), concreteIndices, routingMap, request.preference());
    }

    @Override
    protected ClusterBlockException checkGlobalBlock(ClusterState state, ExportRequest request) {
        return state.blocks().globalBlockedException(ClusterBlockLevel.READ);
    }

    @Override
    protected ClusterBlockException checkRequestBlock(ClusterState state, ExportRequest exportRequest, String[] concreteIndices) {
        return state.blocks().indicesBlockedException(ClusterBlockLevel.READ, concreteIndices);
    }

    @Override
    protected ExportResponse newResponse(ExportRequest request, AtomicReferenceArray shardsResponses, ClusterState clusterState) {
        int successfulShards = 0;
        int failedShards = 0;
        List<ShardOperationFailedException> shardFailures = null;
        List<ShardExportResponse> responses = new ArrayList<ShardExportResponse>();
        for (int i = 0; i < shardsResponses.length(); i++) {
            Object shardResponse = shardsResponses.get(i);
            if (shardResponse == null) {
                failedShards++;
            } else if (shardResponse instanceof BroadcastShardOperationFailedException) {
                failedShards++;
                if (shardFailures == null) {
                    shardFailures = newArrayList();
                }
                shardFailures.add(new DefaultShardOperationFailedException((BroadcastShardOperationFailedException) shardResponse));
            } else {
                responses.add((ShardExportResponse) shardResponse);
                successfulShards++;
            }
        }
        return new ExportResponse(responses, shardsResponses.length(), successfulShards, failedShards, shardFailures);
    }


    @Override
    protected ShardExportResponse shardOperation(ShardExportRequest request) throws ElasticsearchException {


        IndexService indexService = indicesService.indexServiceSafe(request.index());
        IndexShard indexShard = indexService.shardSafe(request.shardId());

        SearchShardTarget shardTarget = new SearchShardTarget(clusterService.localNode().id(), request.index(), request.shardId());
        ExportContext context = new ExportContext(0,
                new ShardSearchRequest().types(request.types()).filteringAliases(request.filteringAliases()),
                shardTarget, indexShard.acquireSearcher("crate/inout"), indexService, indexShard,
                scriptService, cacheRecycler, pageRecycler, bigArrays, nodePath);
        ExportContext.setCurrent(context);

        try {
            BytesReference source = request.source();
            exportParser.parseSource(context, source);
            context.preProcess();
            exporter.check(context);
            try {
                if (context.explain()) {
                    return new ShardExportResponse(shardTarget.nodeIdText(), request.index(), request.shardId(), context.outputCmd(), context.outputCmdArray(), context.outputFile());
                } else {
                    Exporter.Result res = exporter.execute(context);
                    return new ShardExportResponse(shardTarget.nodeIdText(), request.index(), request.shardId(), context.outputCmd(), context.outputCmdArray(), context.outputFile(), res.outputResult.stdErr, res.outputResult.stdOut, res.outputResult.exit, res.numExported);
                }

            } catch (Exception e) {
                throw new QueryPhaseExecutionException(context, "failed to execute export", e);
            }
        } finally {
            context.release();
            SearchContext.removeCurrent();
        }
    }
}
