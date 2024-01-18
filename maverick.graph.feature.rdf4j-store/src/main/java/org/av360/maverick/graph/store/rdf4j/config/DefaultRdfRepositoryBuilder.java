package org.av360.maverick.graph.store.rdf4j.config;

import com.github.benmanes.caffeine.cache.*;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.av360.maverick.graph.model.context.Environment;
import org.av360.maverick.graph.model.enums.RepositoryType;
import org.av360.maverick.graph.model.errors.store.InvalidStoreConfiguration;
import org.av360.maverick.graph.store.FragmentsStore;
import org.av360.maverick.graph.store.RepositoryBuilder;
import org.av360.maverick.graph.store.rdf.LabeledRepository;
import org.av360.maverick.graph.store.rdf4j.repository.util.AbstractRdfRepository;
import org.checkerframework.checker.index.qual.NonNegative;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.base.RepositoryWrapper;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.lmdb.LmdbStore;
import org.eclipse.rdf4j.sail.lmdb.config.LmdbStoreConfig;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

@Component
@Slf4j(topic = "graph.repo.cfg.builder")
@ConfigurationProperties(prefix = "application")
public class DefaultRdfRepositoryBuilder implements RepositoryBuilder {


    private final Cache<String, LabeledRepository> cache;

    protected MeterRegistry meterRegistry;

    @PreDestroy
    public void shutdownRepositories() {
        if(cache != null) {
            cache.asMap().values().forEach(RepositoryWrapper::shutDown);
        }

    }



    @Autowired
    public void setMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }


    @PostConstruct
    private void registerCacheGauges() {
        if(Objects.nonNull(this.meterRegistry)) {
            Gauge.builder("graph.store.repository.cache_size", cache, Cache::estimatedSize)
                    .register(this.meterRegistry);

            Gauge.builder("graph.store.repository.cache_evictions", cache, c -> c.stats().evictionCount())
                    .register(this.meterRegistry);

            Gauge.builder("graph.store.repository.cache_loads", cache, c -> c.stats().loadCount())
                    .register(this.meterRegistry);

            Gauge.builder("graph.store.repository.cache_hits", cache, c -> c.stats().hitCount())
                    .register(this.meterRegistry);

            Gauge.builder("graph.store.repository.cache_weight", cache, c -> c.stats().evictionWeight())
                    .register(this.meterRegistry);
        }
    }

    public DefaultRdfRepositoryBuilder() {

        cache = Caffeine.newBuilder()
                .maximumWeight(900)
                .weigher(new Weigher<String, LabeledRepository>() {
                    @Override
                    public @NonNegative int weigh(String key, LabeledRepository value) {
                        if(value.getConnectionsCount() > 0) {
                            return 0; // ignore repositories with active connections
                        } else if(! value.isInitialized()) {
                            return 600;
                        } else {
                            return 200;
                        }
                    }
                })
                .scheduler(Scheduler.systemScheduler())
                .evictionListener((String key, LabeledRepository labeledRepository, RemovalCause cause) -> {
                    log.debug("Repository {} shutting down due to reason: {}", key, cause);
                    if(Objects.nonNull(labeledRepository) && labeledRepository.isInitialized()) {
                        try {
                            labeledRepository.shutDown();
                        } catch (RepositoryException exception) {
                            log.warn("Exception while shutting down for repository {}: {}", key, exception.getMessage());
                        }
                    }
                } )
                .recordStats()
                .build();


    }


    /**
     * Initializes the connection to a repository. The repositories are cached
     *
     * @param store Store
     * @param authentication Current authentication information
     * @return The repository object
     * @throws IOException If repository cannot be found
     */
    @Override
    public Mono<LabeledRepository> getRepository(FragmentsStore store, Environment target) {
        if(store instanceof AbstractRdfRepository repository) {
            Validate.isTrue(target.isAuthorized(), "Unauthorized status in repository builder");
            Validate.notNull(target.getRepositoryType(), "Missing repository type in repository builder");
            Validate.notBlank(target.getRepositoryType().toString(), "Empty repository type in repository builder");

            try {
                LabeledRepository labeledRepository = this.buildRepository(repository, target);
                return this.validateRepository(labeledRepository, repository, target);
            } catch (IOException e) {
                return Mono.error(e);
            }
        } else return Mono.error(new InvalidStoreConfiguration("Store of type %s not supported by for building a RDF repository.".formatted(store.getClass().getSimpleName())));



    }

    @Override
    public Mono<Void> shutdownRepository(FragmentsStore store, Environment environment) {
        if(cache != null) {
            String key = formatRepositoryLabel(environment);
            LabeledRepository repository = cache.getIfPresent(key);
            if(!Objects.isNull(repository)) {
                repository.shutDown();
                cache.invalidate(key);
            }

        }
        return Mono.empty();
    }

    protected Mono<LabeledRepository> validateRepository(@Nullable LabeledRepository repository, FragmentsStore store, Environment environment) throws IOException {
        return Mono.create(sink -> {
            if(!Objects.isNull(repository)) {
                if(! repository.isInitialized() && repository.getConnectionsCount() == 0) {
                    log.warn("Validation error: Repository of type '{}' is not initialized", repository);
                    sink.error(new IOException(String.format("Repository %s not initialized", environment.getRepositoryType())));
                } else {
                    sink.success(repository);
                }
            } else {
                sink.error(new IOException(String.format("Cannot resolve repository of type '%s' for environment '%s'", environment.getRepositoryType(), environment)));
            }
        });
    }

    protected LabeledRepository buildRepository(AbstractRdfRepository store, Environment environment) {
        if(! environment.hasConfiguration(Environment.RepositoryConfigurationKey.FLAG_PERSISTENT)) {
            log.warn("Repository configuration for persistence not present, default to false");
            environment.setConfiguration(Environment.RepositoryConfigurationKey.FLAG_PERSISTENT, false);
        }

        if(! environment.hasConfiguration(Environment.RepositoryConfigurationKey.FLAG_PUBLIC)) {
            log.warn("Repository configuration for persistence not present, default to false");
            environment.setConfiguration(Environment.RepositoryConfigurationKey.FLAG_PUBLIC, false);
        }


        if (Boolean.parseBoolean(environment.getConfiguration(Environment.RepositoryConfigurationKey.FLAG_PERSISTENT).get())) {
            // TODO: either default path is set, or application has a configuration set for the path. For now we expect the default path
            Validate.notBlank(store.getDirectory(), "No default storage directory defined for persistent application");
        }


        log.trace("Resolving repository for environment: {}", environment);

        String label = formatRepositoryLabel(environment);
        if(Objects.nonNull(this.meterRegistry)) {
            meterRegistry.counter("graph.store.repository", "method", "access", "label", label).increment();
        }


        if (environment.getConfiguration(Environment.RepositoryConfigurationKey.FLAG_PERSISTENT).map(Boolean::parseBoolean).orElse(false)) {
            Path path;
            if(environment.hasConfiguration(Environment.RepositoryConfigurationKey.KEY)) {
                path = Paths.get(store.getDirectory(), environment.getConfiguration(Environment.RepositoryConfigurationKey.KEY).get());
            } else {
                path = Paths.get(store.getDirectory());
            }

            return getCache().get(label, s -> initializePersistentRepository(path, label));
        } else {
            return getCache().get(label, s -> initializeVolatileRepository(label));
        }
    }





    protected LabeledRepository initializePersistentRepository(Path path, String label) {
        try {
            log.debug("Initializing persistent repository in path '{}' for label '{}'", path, label);



            Resource file = new FileSystemResource(path);
            LmdbStoreConfig config = new LmdbStoreConfig();

            config.setTripleIndexes("spoc,ospc,psoc");


            if (!file.exists() && !file.getFile().mkdirs())
                throw new IOException("Failed to create path: " + file.getFile());

            LabeledRepository labeledRepository = new LabeledRepository(label, new SailRepository(new LmdbStore(file.getFile(), config)));
            labeledRepository.init();

            this.registerMetrics(label, labeledRepository); 



            return labeledRepository;


        } catch (RepositoryException e) {
            log.error("Failed to initialize persistent repository in path '{}'.", path, e);
            throw e;
        } catch (IOException e) {
            log.error("Failed to initialize persistent repository in path '{}'.", path, e);
            return this.initializeVolatileRepository(label);
        }
    }

    private void registerMetrics(String label, LabeledRepository labeledRepository) {
        if(Objects.nonNull(this.meterRegistry)) {
            meterRegistry.counter("graph.store.repository", "method", "init", "mode", "persistent", "label", label).increment();
            meterRegistry.gauge("graph.store.repository.connections", Tags.of("label", label), labeledRepository, LabeledRepository::getConnectionsCount);
        }
    }

    protected LabeledRepository initializeVolatileRepository(String label) {
        log.debug("Initializing in-memory repository for label '{}'", label);




        LabeledRepository labeledRepository = new LabeledRepository(label, new SailRepository(new MemoryStore()));
        labeledRepository.init();

        this.registerMetrics(label, labeledRepository);

        return labeledRepository;
    }

    public Cache<String, LabeledRepository> getCache() {
        return cache;
    }

    protected String formatRepositoryLabel(RepositoryType rt, String... details) {
        StringBuilder label = new StringBuilder(rt.toString());
        for (String appendix : details) {
            label.append("_").append(appendix);
        }
        return label.toString();
    }

    protected String formatRepositoryLabel(Environment environment) {
        StringBuilder label = new StringBuilder(environment.getRepositoryType().toString());
        if(Objects.nonNull(environment.getScope())) label.append("_").append(environment.getScope().label());
        if(StringUtils.hasLength(environment.getStage())) label.append("_").append(environment.getStage());

        return label.toString();
    }

}
