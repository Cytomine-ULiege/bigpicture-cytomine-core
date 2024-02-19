package be.cytomine.service.search;

/*
 * Copyright (c) 2009-2023. Authors: see NOTICE file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import be.cytomine.domain.meta.Property;
import be.cytomine.utils.StringUtils;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.json.JsonData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class MetadataSearchService {

    private final ElasticsearchOperations operations;

    public MetadataSearchService(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    private Query buildRangeQuery(List<String> bounds, String value) {
        Query matchKey = MatchQuery.of(mq -> mq
                .field("key")
                .query(value))
            ._toQuery();

        Query byRange = RangeQuery.of(rq -> rq
                .field("value")
                .gte(JsonData.of(bounds.get(0)))
                .lte(JsonData.of(bounds.get(1))))
            ._toQuery();

        return BoolQuery.of(bq -> bq
                .must(matchKey)
                .must(byRange))
            ._toQuery();
    }

    private Query buildStringQuery(String key, String value, Query byDomainId) {
        Query byValue = MatchPhrasePrefixQuery.of(q -> q
                .field("value")
                .query(StringUtils.encodeString(value)))
            ._toQuery();

        Query byKey = QueryStringQuery.of(qsq -> qsq
                .query(key.replace(".", "*."))
                .defaultField("key"))
            ._toQuery();

        return BoolQuery.of(b -> b
            .must(byValue)
            .must(byDomainId)
            .must(byKey)
        )._toQuery();
    }

    private Query buildQuery(List<Long> ids, Map<String, Object> filters) {
        List<FieldValue> imageIDs = ids
            .stream()
            .map(FieldValue::of)
            .collect(Collectors.toList());
        TermsQueryField termsQueryField = TermsQueryField.of(tqf -> tqf.value(imageIDs));
        Query byDomainId = TermsQuery.of(ts -> ts.field("domain_ident").terms(termsQueryField))._toQuery();

        if (filters.isEmpty()) {
            return BoolQuery.of(b -> b
                .must(byDomainId)
                .must(MatchAllQuery.of(maq -> maq)._toQuery())
            )._toQuery();
        }

        List<Query> subqueries = new ArrayList<>();
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof List<?>) {
                subqueries.add(buildRangeQuery((List<String>) value, key));
            } else {
                subqueries.add(buildStringQuery(key, (String) value, byDomainId));
            }
        }

        return BoolQuery.of(b -> b.should(subqueries))._toQuery();
    }

    private Set<Long> executeQuery(int size, Query subQuery) {
        NativeQuery query = NativeQuery.builder()
            .withAggregation("domain_id", Aggregation.of(a -> a.terms(ta -> ta.field("domain_ident"))))
            .withPageable(PageRequest.of(0, size))
            .withQuery(subQuery)
            .build();
        log.debug(String.format("Elasticsearch %s", query.getQuery()));

        SearchHits<Property> searchHits = operations.search(
            query,
            Property.class,
            IndexCoordinates.of("properties")
        );
        log.debug(String.format("Total hits: %d", searchHits.getTotalHits()));

        ElasticsearchAggregations aggregations = (ElasticsearchAggregations) searchHits.getAggregations();
        Map<String, Long> buckets = aggregations
            .aggregations()
            .get(0)
            .aggregation()
            .getAggregate()
            .lterms()
            .buckets()
            .array()
            .stream()
            .collect(Collectors.toMap(LongTermsBucket::key, LongTermsBucket::docCount));

        return buckets
            .keySet()
            .stream()
            .map(Long::valueOf)
            .collect(Collectors.toSet());
    }

    public List<Long> search(Map<String, List<Long>> ids, Map<String, Map<String, Object>> filters) {
        Map<String, Query> queries = ids
            .entrySet()
            .stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                it -> buildQuery(it.getValue(), filters.getOrDefault(it.getKey(), new HashMap<>()))
            ));

        Set<Long> foundIds = new HashSet<>();
        for (Map.Entry<String, Query> entry : queries.entrySet()) {
            foundIds.addAll(executeQuery(ids.getOrDefault(entry.getKey(), List.of()).size(), entry.getValue()));
        }

        return ids
            .values()
            .stream()
            .flatMap(Collection::stream)
            .filter(foundIds::contains)
            .toList();
    }

    public List<String> searchAutoCompletion(String key, String search) {
        Query byKeyword = QueryStringQuery.of(qsq -> qsq
                .query(key.replace(".", "*."))
                .defaultField("key"))
            ._toQuery();

        Query autocomplete = QueryStringQuery.of(qsq -> qsq
                .defaultField("value")
                .query(String.format("%s*", StringUtils.encodeString(search))))
            ._toQuery();

        NativeQuery query = NativeQuery.builder()
            .withQuery(q -> q.bool(b -> b
                .must(byKeyword)
                .must(autocomplete)))
            .build();

        SearchHits<Property> searchHits = operations.search(
            query,
            Property.class,
            IndexCoordinates.of("properties")
        );

        return searchHits
            .stream()
            .map(hit -> hit.getContent().getValue())
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }
}
