package io.sapl.springdatar2dbc.sapl;

import io.sapl.springdatar2dbc.sapl.queryTypes.filterEnforcement.ProceededDataFilterEnforcementPoint;
import io.sapl.springdatar2dbc.sapl.queryTypes.annotationEnforcement.R2dbcAnnotationQueryManipulationEnforcementPoint;
import io.sapl.springdatar2dbc.sapl.queryTypes.methodNameEnforcement.R2dbcMethodNameQueryManipulationEnforcementPoint;
import org.springframework.stereotype.Service;

@Service
public class QueryManipulationEnforcementPointFactory {

    public <T> QueryManipulationEnforcementPoint<T> createR2dbcAnnotationQueryManipulationEnforcementPoint(
            QueryManipulationEnforcementData<T> enforcementData) {
        return new R2dbcAnnotationQueryManipulationEnforcementPoint<>(enforcementData);
    }

    public <T> QueryManipulationEnforcementPoint<T> createR2dbcMethodNameQueryManipulationEnforcementPoint(
            QueryManipulationEnforcementData<T> enforcementData) {
        return new R2dbcMethodNameQueryManipulationEnforcementPoint<>(enforcementData);
    }

    public <T> QueryManipulationEnforcementPoint<T> createProceededDataFilterEnforcementPoint(
            QueryManipulationEnforcementData<T> enforcementData) {
        return new ProceededDataFilterEnforcementPoint<>(enforcementData);
    }

}
