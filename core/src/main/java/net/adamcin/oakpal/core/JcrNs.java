/*
 * Copyright 2018 Mark Adamcin
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

package net.adamcin.oakpal.core;

import static net.adamcin.oakpal.core.JavaxJson.key;

import javax.json.JsonObject;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

/**
 * Config DTO for JCR Namespace Prefix to URI Mappings.
 */
public final class JcrNs implements JavaxJson.ObjectConvertible, Comparable<JcrNs> {
    static final String KEY_PREFIX = "prefix";
    static final String KEY_URI = "uri";

    private String prefix;
    private String uri;

    /**
     * The namespace prefix.
     *
     * @return the namespace prefix
     */
    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    /**
     * The namespace URI.
     *
     * @return the namespace URI
     */
    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    /**
     * Map a JSON object to a {@link JcrNs}.
     *
     * @param json JSON object
     * @return a new JCR NS mapping
     */
    @Deprecated
    static JcrNs fromJSON(@NotNull final JSONObject json) {
        JcrNs jcrNs = new JcrNs();
        jcrNs.setPrefix(json.getString(KEY_PREFIX));
        jcrNs.setUri(json.getString(KEY_URI));
        return jcrNs;
    }

    /**
     * Map a JSON object to a {@link JcrNs}.
     *
     * @param json JSON object
     * @return a new JCR NS mapping
     */
    public static JcrNs fromJson(@NotNull final JsonObject json) {
        if (!json.containsKey(KEY_PREFIX) || !json.containsKey(KEY_URI)) {
            return null;
        }
        JcrNs jcrNs = new JcrNs();
        jcrNs.setPrefix(json.getString(KEY_PREFIX, ""));
        jcrNs.setUri(json.getString(KEY_URI, ""));
        return jcrNs;
    }

    /**
     * Create a new JcrNs with both values set.
     *
     * @param prefix the namespace prefix
     * @param uri    the namespace uri
     * @return a new JCR namespace mapping
     */
    public static JcrNs create(@NotNull final String prefix, @NotNull final String uri) {
        final JcrNs ns = new JcrNs();
        ns.setPrefix(prefix);
        ns.setUri(uri);
        return ns;
    }

    @Override
    public JsonObject toJson() {
        return key(KEY_PREFIX, getPrefix()).key(KEY_URI, getUri()).get();
    }

    @Override
    public String toString() {
        return toJson().toString();
    }

    @Override
    public int compareTo(@NotNull final JcrNs o) {
        return getPrefix().compareTo(o.getPrefix());
    }
}