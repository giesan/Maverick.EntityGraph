package io.av360.maverick.graph.tests.util;

import io.av360.maverick.graph.model.security.Authorities;
import io.av360.maverick.graph.store.EntityStore;
import io.av360.maverick.graph.store.RepositoryType;
import io.av360.maverick.graph.store.TransactionsStore;
import io.av360.maverick.graph.tests.config.TestSecurityConfig;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.io.StringWriter;

@Slf4j
public abstract class TestsBase {

    protected static ValueFactory vf = SimpleValueFactory.getInstance();


    private EntityStore entityStore;

    private TransactionsStore transactionsStore;



    @Autowired
    public void setStores(EntityStore entityStore, TransactionsStore transactionsStore) {
        this.entityStore = entityStore;
        this.transactionsStore = transactionsStore;
    }


    public void print(Model md, RDFFormat rdfFormat) {
       String m = this.dumpModel(md, rdfFormat);
       log.trace("Current model: \n {}", m);
    }

    public String dumpModel(Model md, RDFFormat rdfFormat) {
        StringWriter sw = new StringWriter();
        RDFWriter writer = Rio.createWriter(rdfFormat, sw);
        writer.startRDF();
        md.forEach(writer::handleStatement);
        writer.endRDF();
        return sw.toString();
    }

    protected void resetRepository() {
        TestingAuthenticationToken token = TestSecurityConfig.createAuthenticationToken();
        this.entityStore.reset(token, RepositoryType.ENTITIES, Authorities.SYSTEM);
        this.entityStore.reset(token, RepositoryType.TRANSACTIONS, Authorities.SYSTEM);
        this.entityStore.reset(token, RepositoryType.APPLICATION, Authorities.SYSTEM);
    }


}
