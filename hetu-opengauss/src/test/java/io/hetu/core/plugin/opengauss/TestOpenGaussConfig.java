/*
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

package io.hetu.core.plugin.opengauss;

import com.google.common.collect.ImmutableMap;
import io.airlift.configuration.testing.ConfigAssertions;
import io.prestosql.plugin.postgresql.PostgreSqlConfig;
import org.testng.annotations.Test;

import java.util.Map;

public class TestOpenGaussConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(PostgreSqlConfig.class)
                .setArrayMapping(PostgreSqlConfig.ArrayMapping.DISABLED));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("postgresql.experimental.array-mapping", "AS_ARRAY")
                .build();

        PostgreSqlConfig expected = new PostgreSqlConfig()
                .setArrayMapping(PostgreSqlConfig.ArrayMapping.AS_ARRAY);

        ConfigAssertions.assertFullMapping(properties, expected);
    }
}
