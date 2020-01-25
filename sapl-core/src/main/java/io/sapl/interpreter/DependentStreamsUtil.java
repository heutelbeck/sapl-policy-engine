/**
 * Copyright © 2020 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.interpreter;

import java.util.List;

import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

@UtilityClass
public class DependentStreamsUtil {

	/**
	 * Combines a list of dependent fluxes by sequentially applying the {@code switchMap}
	 * operator. E.g. given the four fluxes f1, f2, f3, and f4, a flux that results from
	 * calling <pre>
	 * f1.switchMap(f1Item -&gt; f2)
	 *   .switchMap(f2Item -&gt; f3)
	 *   .switchMap(f3Item -&gt; f4);
	 * </pre> is returned.
	 *
	 * Because the fluxes may not only depend on the fact that the preceding flux has
	 * emitted a new value but also on the emitted value itself, they cannot be directly
	 * passed in as a list of fluxes, but have to be indirectly retrieved from flux
	 * providers accepting the emitted value of the preceding flux. E.g. given the four
	 * flux providers fp1, fp2, fp3 and fp4 providing the fluxes f1, f2, f3, and f4 this
	 * method returns a flux that results from calling <pre>
	 * fp1.getFlux(input).switchMap(f1Item -&gt; fp2.getFlux(f1Item))
	 *                   .switchMap(f2Item -&gt; fp3.getFlux(f2item))
	 *                   .switchMap(f3Item -&gt; fp4.getFlux(f3Item));
	 * </pre>
	 *
	 * If no flux providers are passed in, a flux emitting just the given {@code input}
	 * element is returned.
	 *
	 * The difference between this method and {@link #nestedSwitchMap(Object, List, int)}
	 * is subtle. Both methods return a flux resulting from the combination of multiple
	 * given fluxes using the switchMap operator. The combination used in this method
	 * causes subsequent fluxes to stop emitting items as soon as the directly preceding
	 * flux emits a new item, whereas with {@link #nestedSwitchMap(Object, List, int)}
	 * this is the case if any preceding flux emits a new element. For more detailed
	 * information see the thesis "Reactive Policy Decision Point für die SAPL Policy
	 * Engine" by Felix Siegrist.
	 * @param input the element to be used to retrieve the flux from the first flux
	 * provider. If no flux providers are given, a flux emitting just this {@code input}
	 * element is returned.
	 * @param fluxProviders a list of objects providing the fluxes to be combined
	 * @param <T> type parameter for the items emitted by the returned flux
	 * @return a flux of {@code T} items which is the result of sequentially applying the
	 * {@code switchMap} operator on the fluxes provided by the given
	 * {@code fluxProviders}.
	 */
	public static <T> Flux<T> sequentialSwitchMap(T input, List<FluxProvider<T>> fluxProviders) {
		if (fluxProviders == null || fluxProviders.isEmpty()) {
			return Flux.just(input);
		}
		else {
			Flux<T> flux = fluxProviders.get(0).getFlux(input);
			for (int i = 1; i < fluxProviders.size(); i++) {
				final int idx = i;
				flux = flux.switchMap(result -> fluxProviders.get(idx).getFlux(result));
			}
			return flux;
		}
	}

	/**
	 * Combines a list of dependent fluxes by recursively applying the {@code switchMap}
	 * operator. E.g. given the four fluxes f1, f2, f3, and f4, a flux that results from
	 * calling <pre>
	 * f1.switchMap(f1Item -&gt;
	 *              f2.switchMap(f2Item -&gt;
	 *                           f3.switchMap(f3Item -&gt; f4)));
	 * </pre> is returned.
	 *
	 * Because the fluxes may not only depend on the fact that the preceding flux has
	 * emitted a new value but also on the emitted value itself, they cannot be directly
	 * passed in as a list of fluxes, but have to be indirectly retrieved from flux
	 * providers accepting the emitted value of the preceding flux. E.g. given the four
	 * flux providers fp1, fp2, fp3 and fp4 providing the fluxes f1, f2, f3, and f4 this
	 * method returns a flux that results from calling <pre>
	 * fp1.getFlux(input).switchMap(f1Item -&gt; fp2.getFlux(f1Item)
	 *                              .switchMap(f2Item -&gt; fp3.getFlux(f2item)
	 *                                         .switchMap(f3Item -&gt; fp4.getFlux(f3Item))));
	 * </pre>
	 *
	 * If no flux providers are passed in, a flux emitting just the given {@code input}
	 * element is returned.
	 *
	 * The difference between this method and {@link #sequentialSwitchMap(Object, List)}
	 * is subtle. Both methods return a flux resulting from the combination of multiple
	 * given fluxes using the switchMap operator. The combination used in this method
	 * causes subsequent fluxes to stop emitting items as soon as any preceding flux emits
	 * a new item, whereas with {@link #sequentialSwitchMap(Object, List)} this is only
	 * the case if the directly preceding flux emits a new element. For more detailed
	 * information see the thesis "Reactive Policy Decision Point für die SAPL Policy
	 * Engine" by Felix Siegrist.
	 * @param input the element to be used to retrieve the flux from the first flux
	 * provider. If no flux providers are given, a flux emitting just this {@code input}
	 * element is returned.
	 * @param fluxProviders a list of objects providing the fluxes to be combined
	 * @param <T> type parameter for the items emitted by the returned flux
	 * @return a flux of {@code T} items which is the result of recursively applying the
	 * {@code switchMap} operator on the fluxes provided by the given
	 * {@code fluxProviders}.
	 */
	public static <T> Flux<T> nestedSwitchMap(T input, List<FluxProvider<T>> fluxProviders) {
		if (fluxProviders == null || fluxProviders.isEmpty()) {
			return Flux.just(input);
		}
		else if (fluxProviders.size() == 1) {
			return fluxProviders.get(0).getFlux(input);
		}
		else {
			return DependentStreamsUtil.nestedSwitchMap(input, fluxProviders, 0);
		}
	}

	private static <T> Flux<T> nestedSwitchMap(T input, List<FluxProvider<T>> fluxProviders, int idx) {
		if (idx < fluxProviders.size() - 2) {
			return fluxProviders.get(idx).getFlux(input)
					.switchMap(result -> DependentStreamsUtil.nestedSwitchMap(result, fluxProviders, idx + 1));
		}
		else { // idx == fluxProviders.size() - 2
			return fluxProviders.get(idx).getFlux(input)
					.switchMap(result -> fluxProviders.get(idx + 1).getFlux(result));
		}
	}

}
