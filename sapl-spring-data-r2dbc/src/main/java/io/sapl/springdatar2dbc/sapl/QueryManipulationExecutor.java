package io.sapl.springdatar2dbc.sapl;

import lombok.experimental.UtilityClass;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import reactor.core.publisher.Flux;

import java.util.Map;

@UtilityClass
public class QueryManipulationExecutor {

    public <T> Flux<Map<String, Object>> execute(String query, BeanFactory beanFactory, Class<T> domainType) {
        var r2dbcEntityTemplate = beanFactory.getBean(R2dbcEntityTemplate.class);

        if (query.toLowerCase().contains("where")) {
            return r2dbcEntityTemplate.getDatabaseClient().sql(query).fetch().all();
        } else {
            var tableName           = r2dbcEntityTemplate.getDataAccessStrategy().getTableName(domainType)
                    .getReference();
            var queryWithSelectPart = "SELECT * FROM %s WHERE %s".formatted(tableName, query);

            return r2dbcEntityTemplate.getDatabaseClient().sql(queryWithSelectPart).fetch().all();
        }
    }
}
