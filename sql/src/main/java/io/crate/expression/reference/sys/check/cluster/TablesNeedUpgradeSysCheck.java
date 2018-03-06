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

package io.crate.expression.reference.sys.check.cluster;

import io.crate.action.sql.ResultReceiver;
import io.crate.action.sql.SQLOperations;
import io.crate.action.sql.Session;
import io.crate.data.Row;
import io.crate.data.Row1;
import io.crate.expression.reference.sys.check.AbstractSysCheck;
import io.crate.sql.parser.SqlParser;
import io.crate.sql.tree.Statement;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Version;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.Provider;
import org.elasticsearch.common.inject.Singleton;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;

@Singleton
public class TablesNeedUpgradeSysCheck extends AbstractSysCheck {

    public static final int ID = 3;
    public static final String DESCRIPTION =
        "The following tables need to be upgraded for compatibility with future versions of CrateDB: ";

    private static final String STMT = "select schema_name || '.' || table_name, min_lucene_version " +
                                       "from sys.shards where min_lucene_version not like '" +
                                       Version.LATEST.major + ".%.%' " +
                                       "AND (NOT schema_name || '.' || table_name = ANY (?)) " +
                                       "order by 1";
    private static final Statement PARSED_STMT = SqlParser.createStatement(STMT);

    private final Logger logger;
    private final ClusterService clusterService;
    private final Provider<SQLOperations> sqlOperationsProvider;
    private volatile Collection<String> tablesNeedUpgrade;
    private Session session;

    @Inject
    public TablesNeedUpgradeSysCheck(ClusterService clusterService,
                                     Provider<SQLOperations> sqlOperationsProvider,
                                     Settings settings) {
        super(ID, DESCRIPTION, Severity.MEDIUM);
        this.clusterService = clusterService;
        this.sqlOperationsProvider = sqlOperationsProvider;

        this.logger = Loggers.getLogger(TablesNeedUpgradeSysCheck.class, settings);
    }

    @Override
    public BytesRef description() {
        String linkedDescriptionBuilder = DESCRIPTION + tablesNeedUpgrade + ' ' + LINK_PATTERN + ID;
        return new BytesRef(linkedDescriptionBuilder);
    }

    @Override
    public CompletableFuture<?> computeResult() {
        Collection<String> tablesNeedRecreation =
            TablesNeedRecreationSysCheck.tablesNeedRecreation(clusterService.state().metaData());

        final CompletableFuture<Collection<String>> result = new CompletableFuture<>();
        Object[] sqlParam = tablesNeedRecreation.toArray(new String[]{});
        try {
            session().quickExec(STMT, stmt -> PARSED_STMT, new SycCheckResultReceiver(result), new Row1(sqlParam));
        } catch (Throwable t) {
            result.completeExceptionally(t);
        }
        return result.handle((tableNames, throwable) -> {
            if (throwable == null) {
                tablesNeedUpgrade = tableNames;
            } else {
                logger.error("error while checking for tables that need upgrade", throwable);
            }
            // `select * from sys.checks` should not fail if an error occurred here, so swallow exception
            return null;
        });
    }

    private Session session() {
        if (session == null) {
            session = sqlOperationsProvider.get().newSystemSession();
        }
        return session;
    }

    @Override
    public boolean validate() {
        return tablesNeedUpgrade == null || tablesNeedUpgrade.isEmpty();
    }

    private class SycCheckResultReceiver implements ResultReceiver {

        private final CompletableFuture<Collection<String>> result;
        private final Collection<String> tables;

        private SycCheckResultReceiver(CompletableFuture<Collection<String>> result) {
            this.result = result;
            this.tables = new HashSet<>();
        }

        @Override
        public void setNextRow(Row row) {
            // Row[0] = table_name, Row[1] = min_lucene_version
            if (LuceneVersionChecks.isUpgradeRequired(((BytesRef) row.get(1)).utf8ToString())) {
                tables.add(((BytesRef) row.get(0)).utf8ToString());
            }
        }

        @Override
        public void batchFinished() {
        }

        @Override
        public void allFinished(boolean interrupted) {
            result.complete(tables);
        }

        @Override
        public void fail(@Nonnull Throwable t) {
            result.completeExceptionally(t);
        }

        @Override
        public CompletableFuture<Collection<String>> completionFuture() {
            return result;
        }
    }
}
