package io.sapl.spring;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import io.sapl.api.pip.PolicyInformationPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class AnnotationScanPipProvider implements PIPProvider {

	final String[] basePackages;

	@Override
	public Collection<Class<? extends Object>> getPIPClasses() {
		List<Class<? extends Object>> result = new ArrayList<>();
		for (String basePack : basePackages) {
			ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(
					false);
			scanner.addIncludeFilter(new AnnotationTypeFilter(PolicyInformationPoint.class));

			for (BeanDefinition bd : scanner.findCandidateComponents(basePack)) {
				try {
					result.add(Class.forName(bd.getBeanClassName()));
				} catch (ClassNotFoundException e) {
					LOGGER.warn("could not load class for name {}", bd.getBeanClassName());
				}
			}
		}
		return result;
	}

}
