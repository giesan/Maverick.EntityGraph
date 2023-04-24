package org.av360.maverick.graph.services.transformers;

import org.av360.maverick.graph.services.EntityServices;
import org.av360.maverick.graph.services.QueryServices;
import org.av360.maverick.graph.store.rdf.fragments.TripleModel;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface Transformer {

    Mono<? extends TripleModel> handle(TripleModel model, Map<String, String> parameters);


    default void registerEntityService(EntityServices entityServices) {}

    default void registerQueryService(QueryServices queryServices) {}
}