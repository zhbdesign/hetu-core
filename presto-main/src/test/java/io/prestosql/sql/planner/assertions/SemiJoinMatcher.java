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
package io.prestosql.sql.planner.assertions;

import io.prestosql.Session;
import io.prestosql.cost.StatsProvider;
import io.prestosql.metadata.Metadata;
import io.prestosql.sql.DynamicFilters;
import io.prestosql.sql.planner.Symbol;
import io.prestosql.sql.planner.plan.FilterNode;
import io.prestosql.sql.planner.plan.PlanNode;
import io.prestosql.sql.planner.plan.SemiJoinNode;

import java.util.List;
import java.util.Optional;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.prestosql.sql.DynamicFilters.extractDynamicFilters;
import static io.prestosql.sql.planner.ExpressionExtractor.extractExpressions;
import static io.prestosql.sql.planner.assertions.MatchResult.NO_MATCH;
import static io.prestosql.sql.planner.assertions.MatchResult.match;
import static io.prestosql.sql.planner.optimizations.PlanNodeSearcher.searchFrom;
import static java.util.Objects.requireNonNull;

final class SemiJoinMatcher
        implements Matcher
{
    private final String sourceSymbolAlias;
    private final String filteringSymbolAlias;
    private final String outputAlias;
    private final Optional<SemiJoinNode.DistributionType> distributionType;
    private final Optional<Boolean> hasDynamicFilter;

    SemiJoinMatcher(String sourceSymbolAlias, String filteringSymbolAlias, String outputAlias, Optional<SemiJoinNode.DistributionType> distributionType,
                    Optional<Boolean> hasDynamicFilter)
    {
        this.sourceSymbolAlias = requireNonNull(sourceSymbolAlias, "sourceSymbolAlias is null");
        this.filteringSymbolAlias = requireNonNull(filteringSymbolAlias, "filteringSymbolAlias is null");
        this.outputAlias = requireNonNull(outputAlias, "outputAlias is null");
        this.distributionType = requireNonNull(distributionType, "distributionType is null");
        this.hasDynamicFilter = requireNonNull(hasDynamicFilter, "hasDynamicFilter is null");
    }

    @Override
    public boolean shapeMatches(PlanNode node)
    {
        return node instanceof SemiJoinNode;
    }

    @Override
    public MatchResult detailMatches(PlanNode node, StatsProvider stats, Session session, Metadata metadata, SymbolAliases symbolAliases)
    {
        checkState(shapeMatches(node), "Plan testing framework error: shapeMatches returned false in detailMatches in %s", this.getClass().getName());

        SemiJoinNode semiJoinNode = (SemiJoinNode) node;
        if (!(symbolAliases.get(sourceSymbolAlias).equals(semiJoinNode.getSourceJoinSymbol().toSymbolReference()) &&
                symbolAliases.get(filteringSymbolAlias).equals(semiJoinNode.getFilteringSourceJoinSymbol().toSymbolReference()))) {
            return NO_MATCH;
        }

        if (distributionType.isPresent() && !distributionType.equals(semiJoinNode.getDistributionType())) {
            return NO_MATCH;
        }

        if (hasDynamicFilter.isPresent()) {
            if (hasDynamicFilter.get()) {
                if (!semiJoinNode.getDynamicFilterId().isPresent()) {
                    return NO_MATCH;
                }
                String dynamicFilterId = semiJoinNode.getDynamicFilterId().get();
                List<DynamicFilters.Descriptor> matchingDescriptors = searchFrom(semiJoinNode.getSource())
                        .where(FilterNode.class::isInstance)
                        .findAll()
                        .stream()
                        .flatMap(filterNode -> extractExpressions(filterNode).stream())
                        .flatMap(expression -> extractDynamicFilters(expression).getDynamicConjuncts().stream())
                        .filter(descriptor -> descriptor.getId().equals(dynamicFilterId))
                        .collect(toImmutableList());
                boolean sourceSymbolsMatch = matchingDescriptors.stream()
                        .map(descriptor -> Symbol.from(descriptor.getInput()))
                        .allMatch(sourceSymbol -> symbolAliases.get(sourceSymbolAlias).equals(sourceSymbol.toSymbolReference()));

                if (!matchingDescriptors.isEmpty() && sourceSymbolsMatch) {
                    return match(outputAlias, semiJoinNode.getSemiJoinOutput().toSymbolReference());
                }
                return NO_MATCH;
            }
            if (semiJoinNode.getDynamicFilterId().isPresent()) {
                return NO_MATCH;
            }
        }

        return match(outputAlias, semiJoinNode.getSemiJoinOutput().toSymbolReference());
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("filteringSymbolAlias", filteringSymbolAlias)
                .add("sourceSymbolAlias", sourceSymbolAlias)
                .add("outputAlias", outputAlias)
                .add("distributionType", distributionType)
                .add("hasDynamicFilter", hasDynamicFilter)
                .toString();
    }
}
