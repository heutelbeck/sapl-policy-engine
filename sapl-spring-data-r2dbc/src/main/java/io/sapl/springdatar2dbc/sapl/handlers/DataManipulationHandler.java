package io.sapl.springdatar2dbc.sapl.handlers;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.spring.constraints.providers.ContentFilterPredicateProvider;
import io.sapl.spring.constraints.providers.ContentFilteringProvider;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

import java.util.function.Function;
import java.util.function.Predicate;

import static io.sapl.springdatar2dbc.sapl.utils.ConstraintHandlerUtils.getConstraintHandlerByTypeIfResponsible;
import static io.sapl.springdatar2dbc.sapl.utils.Utilities.FILTER_JSON_CONTENT;
import static io.sapl.springdatar2dbc.sapl.utils.Utilities.FILTER_JSON_CONTENT_PREDICATE;

/**
 * This class takes care of manipulating the received database objects. In
 * detail, it is about
 *
 * @param <T> is the type of the domain object.
 * @see ContentFilteringProvider and
 * @see ContentFilterPredicateProvider
 */
@RequiredArgsConstructor
public class DataManipulationHandler<T> {
    private final Class<T>                 domainType;
    private ContentFilteringProvider       contentFilteringProvider;
    private ContentFilterPredicateProvider contentFilterPredicateProvider;
    private final ObjectMapper             objectMapper = JsonMapper.builder()
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build();

    /**
     * Initiates the manipulation of the database objects. It checks which providers
     * are present in the decision's obligation and applies them to the data
     * accordingly.
     *
     * @param obligations are the obligations of the decision.
     * @return the manipulated database objects.
     */
    public Function<Flux<T>, Flux<T>> manipulate(JsonNode obligations) {
        return data -> {
            this.contentFilteringProvider       = new ContentFilteringProvider(objectMapper);
            this.contentFilterPredicateProvider = new ContentFilterPredicateProvider(objectMapper);

            var filterJsonContentObligation = getConstraintHandlerByTypeIfResponsible(obligations, FILTER_JSON_CONTENT);
            var isContentFilterResponsible  = filterJsonContentObligation != JsonNodeFactory.instance.nullNode();

            var jsonContentFilterPredicateObligation    = getConstraintHandlerByTypeIfResponsible(obligations,
                    FILTER_JSON_CONTENT_PREDICATE);
            var isJsonContentFilterPredicateResponsible = jsonContentFilterPredicateObligation != JsonNodeFactory.instance
                    .nullNode();

            if (isContentFilterResponsible && isJsonContentFilterPredicateResponsible) {
                return data.filter(handleFilter(jsonContentFilterPredicateObligation))
                        .map(handleTransformation(filterJsonContentObligation)).map(toDomainObject());
            }

            if (!isContentFilterResponsible && isJsonContentFilterPredicateResponsible) {
                return data.filter(handleFilter(jsonContentFilterPredicateObligation)).map(toDomainObject());
            }

            if (isContentFilterResponsible) {
                return data.map(handleTransformation(filterJsonContentObligation)).map(toDomainObject());
            }

            return data.map(toDomainObject()); // <- toDomainObject necessary?
        };
    }

    /**
     * Takes over the transformation of the database objects. For this purpose, the
     * corresponding
     * {@link io.sapl.spring.constraints.providers.ContentFilteringProvider#getHandler(JsonNode)}
     * is called from the Sapl engine.
     *
     * @param contentFilterObligation is the corresponding obligation from the
     *                                decision.
     * @return the corresponding handler.
     */
    private Function<Object, Object> handleTransformation(JsonNode contentFilterObligation) {
        return contentFilteringProvider.getHandler(contentFilterObligation);
    }

    /**
     * Takes over the filtering of the database objects. For this purpose, the
     * corresponding
     * {@link io.sapl.spring.constraints.providers.ContentFilterPredicateProvider#getHandler(JsonNode)}
     * is called from the Sapl engine.
     *
     * @param contentFilterPredicateObligation is the corresponding obligation from
     *                                         the decision.
     * @return the corresponding handler.
     */
    private Predicate<Object> handleFilter(JsonNode contentFilterPredicateObligation) {
        return contentFilterPredicateProvider.getHandler(contentFilterPredicateObligation);
    }

    /**
     * Converts an unknown object to {@link #domainType}
     *
     * @return the converted object.
     */
    public Function<Object, T> toDomainObject() {
        return databaseObject -> objectMapper.convertValue(databaseObject, domainType);
    }
}
