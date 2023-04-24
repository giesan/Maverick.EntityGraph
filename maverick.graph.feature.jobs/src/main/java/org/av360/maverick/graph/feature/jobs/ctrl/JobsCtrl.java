package org.av360.maverick.graph.feature.jobs.ctrl;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.av360.maverick.graph.api.controller.AbstractController;
import org.av360.maverick.graph.feature.jobs.DetectDuplicatesJob;
import org.av360.maverick.graph.feature.jobs.ReplaceExternalIdentifiersJob;
import org.av360.maverick.graph.feature.jobs.TypeCoercionJob;
import org.av360.maverick.graph.services.SchemaServices;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(path = "/api/admin/jobs")
//@Api(tags = "Admin Operations")
@Slf4j(topic = "graph.feat.admin.ctrl.api")
@SecurityRequirement(name = "api_key")
public class JobsCtrl extends AbstractController {


    private final ApplicationEventPublisher eventPublisher;
    final DetectDuplicatesJob detectDuplicatesJob;
    final ReplaceExternalIdentifiersJob replaceExternalIdentifiersService;

    final TypeCoercionJob typeCoercionService;

    final SchemaServices schemaServices;

    public JobsCtrl(ApplicationEventPublisher eventPublisher, DetectDuplicatesJob detectDuplicatesJob, ReplaceExternalIdentifiersJob replaceExternalIdentifiersJob, TypeCoercionJob typeCoercionService, SchemaServices schemaServices) {
        this.eventPublisher = eventPublisher;

        this.detectDuplicatesJob = detectDuplicatesJob;
        this.replaceExternalIdentifiersService = replaceExternalIdentifiersJob;
        this.typeCoercionService = typeCoercionService;
        this.schemaServices = schemaServices;
    }


    //@ApiOperation(value = "Empty repository", tags = {})
    @PostMapping(value = "/execute/deduplication")
    @ResponseStatus(HttpStatus.OK)
    Mono<Void> execDeduplicationJob(
            @RequestParam(name = "property", required = true)
            @Parameter(
                    schema = @Schema(type = "string",
                            allowableValues = {"dc.identifier", "dcterms.identifier", "sdo.identifier", "rdfs.label", "skos.pref_label"})
                    )
            String property) {


        return Mono.zip(
                super.getAuthentication(),
                schemaServices.resolvePrefixedName(property)
        ).flatMap(tuple -> this.detectDuplicatesJob.checkForDuplicates(tuple.getT2(), tuple.getT1()));

    }

    @PostMapping(value = "/execute/normalize")
    @ResponseStatus(HttpStatus.ACCEPTED)
    Mono<Void> execSkolemizationJob() {


        return super.getAuthentication()
                .flatMap(this.replaceExternalIdentifiersService::run);

    }


    @PostMapping(value = "/execute/coercion")
    @ResponseStatus(HttpStatus.OK)
    Mono<Void> execCoercionJob() {
        return super.getAuthentication()
                .flatMapMany(this.typeCoercionService::run).then();

    }


}