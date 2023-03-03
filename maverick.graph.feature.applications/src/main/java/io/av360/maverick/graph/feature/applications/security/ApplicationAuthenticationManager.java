package io.av360.maverick.graph.feature.applications.security;

import io.av360.maverick.graph.feature.applications.domain.ApplicationsService;
import io.av360.maverick.graph.feature.applications.domain.model.Application;
import io.av360.maverick.graph.model.security.ApiKeyAuthenticationToken;
import io.av360.maverick.graph.model.security.Authorities;
import io.av360.maverick.graph.model.security.RequestDetails;
import io.av360.maverick.graph.model.security.SystemAuthentication;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Optional;

/**
 * This authentication manager augments the default admin authentication manager (which assumes only one api key exists).
 * <p>
 * It checks whether the given api key is part of an active application. Each application has its own repositories, you
 * can only access the data linked to the api key.
 * <p>
 * It does not check for the validity of the admin key (this is still responsibility of the admin authentication manager).
 * This class must not give Admin Authority.
 */
@Component
@Slf4j(topic = "graph.feat.apps.sec.mgr")
public class ApplicationAuthenticationManager implements ReactiveAuthenticationManager {


    private static final String SUBSCRIPTION_KEY_HEADER = "X-SUBSCRIPTION-KEY";

    private final ApplicationsService subscriptionsService;

    public ApplicationAuthenticationManager(ApplicationsService subscriptionsService) {
        log.trace("Activated Application Authentication Manager (checking subscription api keys)");
        this.subscriptionsService = subscriptionsService;
    }

    //

    /*

        Steps:
        1. check if specific application is requested either through header or through request path,
            yes: grab requested application config from store
        2. check if api key header is a valid subscription key

        if 1 & 2:

          no:
            1.1 check if api key header is a valid subscription key
             yes:
               1.1.1 grant authority according to configuration for subscription
             no:
               1.1.2 do nothing, return unmodified authentication
          yes:
            1.2
            1.3 check if api key header is a valid subscription key


     */
    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {

        try {
            Assert.notNull(authentication, "Authentication is null in Authentication Manager");
            Assert.notNull(authentication.getDetails(), "Authentication is missing request details.");
            Assert.isTrue(authentication.getDetails() instanceof RequestDetails, "Authentication details of wrong type " + authentication.getDetails().getClass());
            log.trace("Handling authentication of type '{}' and authentication status '{}' in application authentication manager ", authentication.getClass().getSimpleName(), authentication.isAuthenticated());


            // check if specific application is requested either through header or through request path,
            RequestDetails requestDetails = (RequestDetails) authentication.getDetails();
            Optional<String> requestedApplicationStr = getScopeFromPath(requestDetails);


            if (requestedApplicationStr.isPresent()) {
                return this.subscriptionsService.getApplicationByLabel(requestedApplicationStr.get(), new SystemAuthentication())
                        .flatMap(requestedApplication -> {
                            if (authentication instanceof SystemAuthentication token) {
                                log.trace("Authentication has system authority and application scope '{}' requested.", requestedApplication.label());
                                return Mono.just(SubscriptionToken.fromSystemAuthentication(token, requestedApplication));

                            } else if (authentication instanceof ApiKeyAuthenticationToken token) {
                                log.trace("Key in header, checking if it is subscription token.");
                                return checkSubscriptionKeyForRequestedApplication(token, requestedApplication)
                                        .map(auth -> auth);
                            } else {
                                return Mono.just(authentication);
                            }
                        });
            }

            // no requested application. We ignore the system auth and try to check if the api key is a valid subscription key
            else {
                if (authentication instanceof SystemAuthentication token) {
                    log.trace("Authentication has system authority, but not application scope requested.");
                    return Mono.just(authentication);
                } else if (authentication instanceof ApiKeyAuthenticationToken token) {
                    log.trace("Key in header and no requested application, checking if it is subscription token.");
                    return this.checkSubscriptionKey(token).map(auth -> auth);

                } else if (authentication instanceof AnonymousAuthenticationToken token) {
                    log.trace("Ignoring anonymous access without requested application in application authentication manager.");
                    return Mono.just(token);
                } else {
                    return Mono.just(authentication);
                }
            }


        } catch (Exception e) {
            log.error("Failed to handle application authentication with error: '{}'", e.getMessage());
            return Mono.error(e);
        }


    }

    /**
     * Tries to find a subscription for the given api key. If found, we assign the matching authority.
     *
     * @param token the token in the header
     * @return
     */
    protected Mono<? extends ApiKeyAuthenticationToken> checkSubscriptionKey(ApiKeyAuthenticationToken token) {
        if (token instanceof SystemAuthentication) return Mono.just(token);

        return this.subscriptionsService.getSubscription(token.getApiKey().orElseThrow(), token)
                .map(subscription -> {
                    SubscriptionToken subscriptionToken = SubscriptionToken.fromApiKeyAuthentication(token, subscription, subscription.application());
                    subscriptionToken.setRequestedApplication(subscription.application());
                    grant(subscriptionToken);
                    return subscriptionToken;
                })
                .flatMap(subscriptionToken -> Mono.just((ApiKeyAuthenticationToken) subscriptionToken))
                .switchIfEmpty(Mono.just(token));
    }

    protected Mono<? extends ApiKeyAuthenticationToken> checkSubscriptionKeyForRequestedApplication(ApiKeyAuthenticationToken token, Application requestedApplication) {
        if (token instanceof SystemAuthentication) return Mono.just(token);

        return this.subscriptionsService.getSubscription(token.getApiKey().orElseThrow(), token)
                .map(subscription -> {

                    SubscriptionToken st = SubscriptionToken.fromApiKeyAuthentication(token, subscription, subscription.application());
                    st.setRequestedApplication(requestedApplication);

                    // requested application matches subscription application
                    if (requestedApplication.label().equalsIgnoreCase(subscription.application().label())) {
                        grant(st);
                    }
                    // requested application does not match application for subscription
                    else {
                        // else we grant read access if public
                        if (st.getRequestedApplication().flags().isPublic()) {
                            log.trace("Valid subscription key for application '{}' unrelated to requested public application '{}' provided in header '{}'.", st.getApplication().label(), st.getRequestedApplication().label(), ApiKeyAuthenticationToken.API_KEY_HEADER);

                            st.grantAuthority(Authorities.READER);
                        } else {
                            log.debug("Valid subscription key for application '{}' unrelated to private application '{}' provided in header '{}'.", st.getApplication().label(), st.getRequestedApplication().label(), ApiKeyAuthenticationToken.API_KEY_HEADER);
                            st.purgeAuthorities();
                        }
                    }
                    return st;
                })
                .flatMap(subscriptionToken -> Mono.just((ApiKeyAuthenticationToken) subscriptionToken))
                .switchIfEmpty(Mono.just(token));

    }

    protected void grant(SubscriptionToken subscriptionToken) {
        if (subscriptionToken.getSubscription().active()) {
            subscriptionToken.setAuthenticated(true);

            // TODO: extract role from application key
            subscriptionToken.grantAuthority(Authorities.APPLICATION);
            log.trace("Valid subscription key for application '{}' provided in header '{}'.", subscriptionToken.getRequestedApplication().label(), ApiKeyAuthenticationToken.API_KEY_HEADER);
        } else {
            if (subscriptionToken.getRequestedApplication().flags().isPublic()) {
                subscriptionToken.setAuthenticated(true);
                subscriptionToken.grantAuthority(Authorities.READER);
                log.debug("Inactive subscription key for public application '{}' provided in header '{}'.", subscriptionToken.getRequestedApplication().label(), ApiKeyAuthenticationToken.API_KEY_HEADER);
            } else {
                subscriptionToken.setAuthenticated(false);
                subscriptionToken.purgeAuthorities();
                log.warn("Inactive subscription key for private application '{}' provided in header '{}'.", subscriptionToken.getRequestedApplication().label(), ApiKeyAuthenticationToken.API_KEY_HEADER);
            }
        }
    }


    /**
     * Returns the scope
     *
     * @param requestDetails request details such as path or headers
     * @return scope if available
     * @throws IOException if path is invalid
     */
    private Optional<String> getScopeFromPath(RequestDetails requestDetails) throws IOException {
        Assert.isTrue(StringUtils.hasLength(requestDetails.path()), "Empty path in request details");

        String[] split = requestDetails.path().split("/");
        for (int i = 0; i < split.length; i++) {
            if (split[i].equalsIgnoreCase("sc")) {
                if (split.length > i + 1) {
                    return Optional.of(split[i + 1]);
                } else {
                    throw new IOException("Invalid path in request, missing scope label: " + requestDetails.path());
                }
            }
        }

        return Optional.empty();
    }




}
