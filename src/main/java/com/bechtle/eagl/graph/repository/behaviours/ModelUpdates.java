package com.bechtle.eagl.graph.repository.behaviours;

import com.bechtle.eagl.graph.domain.model.enums.Activity;
import com.bechtle.eagl.graph.domain.model.wrapper.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Collection;


public interface ModelUpdates extends RepositoryBehaviour {

    /**
     * Deletes the triples directly in the model (without transaction context)
     * @param model
     * @return
     */
    default Mono<Void> delete(Model model) {
        return getRepository().flatMap(repository -> Mono.create(c -> {
            try (RepositoryConnection connection = repository.getConnection()) {
                try {
                    Resource[] contexts = model.contexts().toArray(new Resource[model.contexts().size()]);
                    connection.add(model, contexts);
                    connection.commit();
                    c.success();
                } catch (Exception e) {
                    connection.rollback();
                    c.error(e);
                }
            } catch (RepositoryException e) {
                c.error(e);
            }
        }));
    }

    default Mono<Transaction> delete(Collection<Statement> statements, Transaction transaction) {
        Assert.notNull(transaction, "Transaction cannot be null");


        return transaction
                .remove(statements, Activity.REMOVED)
                .asMono();
    }


    /**
     * Adds the triples in the model to the transaction. Don't forget to commit the transaction.
     *
     * @param model the statements to store
     * @return  Returns the transaction statements
     */
    default Mono<Transaction> insert(Model model, Transaction transaction) {
        Assert.notNull(transaction, "Transaction cannot be null");

        transaction = transaction
                .insert(model, Activity.INSERTED)
                .affected(model);


        return transaction.asMono();
    }

    /**
     *  Stores the triples directly (without transaction context)
     */
    default Mono<Void> insert(Model model) {
        return getRepository().map(repository -> {
            try (RepositoryConnection connection = repository.getConnection()) {
                try {
                    Resource[] contexts = model.contexts().toArray(new Resource[model.contexts().size()]);
                    connection.add(model, contexts);
                    connection.commit();
                    return null;
                } catch (Exception e) {
                    connection.rollback();
                    throw e;
                }
            } catch (RepositoryException e) {
                throw e;
            }
        }).then();

    }
}
