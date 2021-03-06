/*
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
package io.prestosql.server.security;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.NewCookie;

import java.util.Optional;

public class NoOpWebUIAuthenticator
        implements WebUIAuthenticator
{
    @Override
    public Optional<String> getAuthenticatedUsername(HttpServletRequest request)
    {
        return Optional.empty();
    }

    @Override
    public Optional<NewCookie> checkLoginCredentials(String username, String password, boolean secure)
    {
        return Optional.empty();
    }
}
