/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.server.ce.security;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

/**
 * HttpSessionRequestCache that avoids saving internal framework requests.
 */
class CustomRequestCache extends HttpSessionRequestCache {
	/**
	 * {@inheritDoc}
	 *
	 * If the method is considered an internal request from the framework, we skip
	 * saving it.
	 */
	@Override
	public void saveRequest(HttpServletRequest request, HttpServletResponse response) {
		if (!SecurityUtils.isFrameworkInternalRequest(request)) {
			super.saveRequest(request, response);
		}
	}

}
