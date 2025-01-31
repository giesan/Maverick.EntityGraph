/*
 * Copyright (c) 2023-2024.
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.av360.maverick.graph.model.context.Environment;
import org.av360.maverick.graph.model.entities.Transaction;
import org.av360.maverick.graph.model.enums.RepositoryType;
import org.av360.maverick.graph.model.vocabulary.DC;
import org.av360.maverick.graph.store.IndividualsStore;
import org.av360.maverick.graph.store.behaviours.Fragmentable;
import org.av360.maverick.graph.store.postgres.dao.EntityDAO;
import org.av360.maverick.graph.store.postgres.repositories.EntityRepository;
import org.av360.maverick.graph.store.rdf.fragments.RdfFragment;
import org.av360.maverick.graph.store.rdf.fragments.RdfTransaction;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.RDFWriterFactory;
import org.eclipse.rdf4j.rio.RDFWriterRegistry;
import org.slf4j.Logger;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.StringWriter;

@Component
@Slf4j
public class EntityStoreImpl implements IndividualsStore, Fragmentable {

    private final R2dbcEntityTemplate template;

    private final ObjectMapper objectMapper;

    public EntityStoreImpl(EntityRepository entityRepository, R2dbcEntityTemplate template) {
        this.template = template;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Mono<RdfFragment> getFragment(Resource subject, int includeNeighborsLevel, boolean includeDetails, Environment environment) {
        return null;
    }

    @Override
    public Flux<RdfFragment> listFragments(IRI type, int limit, int offset, Environment environment) {
        return null;
    }


    @Override
    public Mono<Transaction> insertFragment(RdfFragment fragment, Environment environment) {

        Resource identifier = fragment.getIdentifier();
        Validate.isTrue(identifier.isIRI());

        JsonNode valuesNode =objectMapper.createObjectNode();
        JsonNode relationsNode = objectMapper.createObjectNode();

        StringWriter valuesWriter = new StringWriter();
        StringWriter relationsWriter = new StringWriter();

        RDFWriterFactory rdfWriterFactory = RDFWriterRegistry.getInstance().get(RDFFormat.JSONLD).orElseThrow();

        RDFWriter valueStatementsWriter = rdfWriterFactory.getWriter(valuesWriter);
        RDFWriter relationsStatementsWriter = rdfWriterFactory.getWriter(relationsWriter);


        valueStatementsWriter.startRDF();
        valueStatementsWriter.handleNamespace("dc", DC.NAMESPACE);
        relationsStatementsWriter.startRDF();
        fragment.listStatements(identifier, null, null).forEach(statement -> {
            log.info("Handling statement: {}", statement.toString());
            if(statement.getObject().isLiteral()) valueStatementsWriter.handleStatement(statement);
            if(statement.getObject().isIRI()) relationsStatementsWriter.handleStatement(statement);


        });
        valueStatementsWriter.endRDF();
        relationsStatementsWriter.endRDF();

        log.info("Values JSON: {}", valuesWriter.toString());
        log.info("Relations JSON: {}", relationsWriter.toString());

        try {
            valuesNode = objectMapper.readTree(valuesWriter.toString());
            relationsNode = objectMapper.readTree(relationsWriter.toString());
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }

        EntityDAO fragmentEntity = new EntityDAO();
        fragmentEntity.setPublicId(identifier.stringValue());
        // fragmentEntity.setRelations(relationsNode);
        // fragmentEntity.setValues(valuesNode);

        return this.template.insert(fragmentEntity).then(Mono.just(new RdfTransaction()));
    }

    @Override
    public Mono<Boolean> exists(Resource subj, Environment environment) {
        return Mono.just(Boolean.TRUE);
    }

    @Override
    public Mono<Long> countFragments(Environment environment) {
        return null;
    }

    @Override
    public RepositoryType getRepositoryType() {
        return RepositoryType.ENTITIES;
    }

    @Override
    public Logger getLogger() {
        return log;
    }


}
