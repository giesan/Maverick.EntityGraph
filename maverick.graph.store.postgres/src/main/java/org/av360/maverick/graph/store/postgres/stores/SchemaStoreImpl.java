/*
 * Copyright (c) 2024.
 *
 *  Licensed under the EUPL, Version 1.2 or – as soon they will be approved by the
 *  European Commission - subsequent versions of the EUPL (the "Licence");
 *
 *  You may not use this work except in compliance with the Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  https://joinup.ec.europa.eu/software/page/eupl5
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the Licence is distributed on an "AS IS" basis,  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the Licence for the specific language governing permissions and limitations under the Licence.
 *
 */

package org.av360.maverick.graph.store.postgres.stores;

import lombok.extern.slf4j.Slf4j;
import org.av360.maverick.graph.store.SchemaStore;
import org.eclipse.rdf4j.model.Namespace;
import org.eclipse.rdf4j.model.ValueFactory;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Optional;

@Slf4j(topic = "graph.repo.schema")
@Component
public class SchemaStoreImpl implements SchemaStore {
    @Override
    public ValueFactory getValueFactory() {
        return null;
    }

    @Override
    public Flux<Namespace> listNamespaces() {
        return null;
    }

    @Override
    public Optional<Namespace> getNamespaceForPrefix(String key) {
        return Optional.empty();
    }

    @Override
    public Optional<String> getPrefixForNamespace(String name) {
        return Optional.empty();
    }

    @Override
    public Logger getLogger() {
        return null;
    }
}
