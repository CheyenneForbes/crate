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

package io.crate.analyze;

import io.crate.test.integration.CrateDummyClusterServiceUnitTest;
import io.crate.testing.SQLExecutor;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.test.ClusterServiceUtils;
import org.junit.Before;
import org.junit.Test;

import static io.crate.metadata.FulltextAnalyzerResolver.CustomType.ANALYZER;
import static io.crate.metadata.FulltextAnalyzerResolver.CustomType.CHAR_FILTER;
import static io.crate.metadata.FulltextAnalyzerResolver.CustomType.TOKENIZER;
import static io.crate.metadata.FulltextAnalyzerResolver.CustomType.TOKEN_FILTER;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;

public class DropAnalyzerTest extends CrateDummyClusterServiceUnitTest {

    private SQLExecutor e;

    @Before
    public void setUpExecutor() throws Exception {
        Settings settings = Settings.builder()
            .put(ANALYZER.buildSettingName("a1"),
                Settings.builder()
                    .put(ANALYZER.buildSettingChildName("a1", "type"), "custom")
                    .put(ANALYZER.buildSettingChildName("a1", TOKENIZER.getName()), "lowercase")
                    .build().toString())

            .put(ANALYZER.buildSettingName("a2"),
                Settings.builder()
                    .put(ANALYZER.buildSettingChildName("a2", "type"), "custom")
                    .put(ANALYZER.buildSettingChildName("a2", TOKENIZER.getName()), "a2_mypattern")
                    .build().toString())
            .put(TOKENIZER.buildSettingName("a2_mypattern"),
                Settings.builder()
                    .put(TOKENIZER.buildSettingChildName("a2_mypattern", "type"), "pattern")
                    .put(TOKENIZER.buildSettingChildName("a2_mypattern", "pattern"), ".*")
                    .build().toString())

            .put(ANALYZER.buildSettingName("a3"),
                Settings.builder()
                    .put(ANALYZER.buildSettingChildName("a3", "type"), "custom")
                    .put(ANALYZER.buildSettingChildName("a3", TOKEN_FILTER.getName()), "a3_lowercase_german, kstem")
                    .build().toString())
            .put(TOKEN_FILTER.buildSettingName("a3_lowercase_german"),
                Settings.builder()
                    .put(TOKEN_FILTER.buildSettingChildName("a3_lowercase_german", "type"), "lowercase")
                    .put(TOKEN_FILTER.buildSettingChildName("a3_lowercase_german", "language"), "german")
                    .build().toString())

            .put(ANALYZER.buildSettingName("a4"),
                Settings.builder()
                    .put(ANALYZER.buildSettingChildName("a4", "type"), "custom")
                    .put(ANALYZER.buildSettingChildName("a4", CHAR_FILTER.getName()), "a4_mymapping, html_strip]")
                    .build().toString())
            .put(CHAR_FILTER.buildSettingName("a4_mymapping"),
                Settings.builder()
                    .put(CHAR_FILTER.buildSettingChildName("a4_mymapping", "type"), "mapping")
                    .put(CHAR_FILTER.buildSettingChildName("a4_mymapping", "mappings"), "\"foo=>bar\"")
                    .build().toString())
            .build();

        ClusterState clusterState = ClusterState.builder(clusterService.state())
            .metaData(MetaData.builder(clusterService.state().metaData())
                .persistentSettings(settings))
            .build();
        ClusterServiceUtils.setState(clusterService, clusterState);

        e = SQLExecutor.builder(clusterService).build();
    }

    private void assertIsMarkedToBeRemove(Settings settings, String settingName) {
        assertThat(settings.keySet(), hasItem(settingName));
        assertThat(settings.get(settingName), nullValue());
    }

    @Test
    public void testDropAnalyzer() {
        DropAnalyzerStatement analyzedStatement = e.analyze("drop analyzer a1");
        assertIsMarkedToBeRemove(analyzedStatement.settingsForRemoval(), ANALYZER.buildSettingName("a1"));
    }

    @Test
    public void testDropAnalyzerWithCustomTokenizer() {
        DropAnalyzerStatement analyzedStatement = e.analyze("drop analyzer a2");
        assertIsMarkedToBeRemove(analyzedStatement.settingsForRemoval(), ANALYZER.buildSettingName("a2"));
        assertIsMarkedToBeRemove(analyzedStatement.settingsForRemoval(), TOKENIZER.buildSettingName("a2_mypattern"));
    }

    @Test
    public void testDropAnalyzerWithCustomTokenFilter() {
        DropAnalyzerStatement analyzedStatement = e.analyze("drop analyzer a3");
        assertIsMarkedToBeRemove(analyzedStatement.settingsForRemoval(), ANALYZER.buildSettingName("a3"));
        assertIsMarkedToBeRemove(analyzedStatement.settingsForRemoval(), TOKEN_FILTER.buildSettingName("a3_lowercase_german"));
    }

    @Test
    public void testDropAnalyzerWithCustomCharFilter() {
        DropAnalyzerStatement analyzedStatement = e.analyze("drop analyzer a4");
        assertIsMarkedToBeRemove(analyzedStatement.settingsForRemoval(), ANALYZER.buildSettingName("a4"));
        assertIsMarkedToBeRemove(analyzedStatement.settingsForRemoval(), CHAR_FILTER.buildSettingName("a4_mymapping"));
    }
}
