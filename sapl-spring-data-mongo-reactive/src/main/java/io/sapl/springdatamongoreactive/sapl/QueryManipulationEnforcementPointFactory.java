package io.sapl.springdatamongoreactive.sapl;

import io.sapl.springdatamongoreactive.sapl.queryTypes.filterEnforcement.ProceededDataFilterEnforcementPoint;
import io.sapl.springdatamongoreactive.sapl.queryTypes.annotationEnforcement.MongoAnnotationQueryManipulationEnforcementPoint;
import io.sapl.springdatamongoreactive.sapl.queryTypes.methodNameEnforcement.MongoMethodNameQueryManipulationEnforcementPoint;
import org.springframework.stereotype.Service;

@Service
public class QueryManipulationEnforcementPointFactory {

    public <T> QueryManipulationEnforcementPoint<T> createMongoAnnotationQueryManipulationEnforcementPoint(
            QueryManipulationEnforcementData<T> enforcementData) {
        return new MongoAnnotationQueryManipulationEnforcementPoint<>(enforcementData);
    }

    public <T> QueryManipulationEnforcementPoint<T> createMongoMethodNameQueryManipulationEnforcementPoint(
            QueryManipulationEnforcementData<T> enforcementData) {
        return new MongoMethodNameQueryManipulationEnforcementPoint<>(enforcementData);
    }

    public <T> QueryManipulationEnforcementPoint<T> createProceededDataFilterEnforcementPoint(
            QueryManipulationEnforcementData<T> enforcementData) {
        return new ProceededDataFilterEnforcementPoint<>(enforcementData);
    }

}
