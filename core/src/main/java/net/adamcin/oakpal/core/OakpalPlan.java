package net.adamcin.oakpal.core;

import org.apache.jackrabbit.spi.PrivilegeDefinition;
import org.apache.jackrabbit.spi.QNodeTypeDefinition;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceMapping;
import org.apache.jackrabbit.util.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static net.adamcin.oakpal.core.Fun.compose;
import static net.adamcin.oakpal.core.Fun.inferTest1;
import static net.adamcin.oakpal.core.Fun.result1;
import static net.adamcin.oakpal.core.Fun.uncheck1;
import static net.adamcin.oakpal.core.JavaxJson.hasNonNull;

/**
 * A plan is a reproducible execution plan, similar in design to a Checklist, but with the following differences:
 * 1. Identified by URL, supporting retrieval on the classpath.
 * 2. No support for referencing external CND files.
 * 3. Can reference pre-install packages by URL.
 * 4. Can not aggregate multiple plans per execution.
 */
public final class OakpalPlan implements JavaxJson.ObjectConvertible {
    private static final Logger LOGGER = LoggerFactory.getLogger(OakpalPlan.class);
    /**
     * Preferred default plan when a custom classpath is specified without specifying a plan name. This is also
     * the preferred plan when a user indicates that NO plan should be used.
     * <p>
     * This should not be used as a default when a plan name is specified by a user, but is not found.
     */
    public static final URL EMPTY_PLAN_URL = OakpalPlan.class.getResource("empty-plan.json");
    /**
     * Preferred default plan when no custom classpath is specified, and no plan name is specified.
     * <p>
     * This should not be used as a default when a plan name is specified by a user, but is not found.
     */
    public static final URL BASIC_PLAN_URL = OakpalPlan.class.getResource("basic-plan.json");

    /**
     * When otherwise unspecified, the default name for a plan should be plan.json.
     */
    public static final String DEFAULT_PLAN_NAME = "plan.json";

    public static final String KEY_CHECKLISTS = "checklists";
    public static final String KEY_CHECKS = "checks";
    public static final String KEY_FORCED_ROOTS = "forcedRoots";
    public static final String KEY_JCR_NAMESPACES = "jcrNamespaces";
    public static final String KEY_JCR_NODETYPES = "jcrNodetypes";
    public static final String KEY_JCR_PRIVILEGES = "jcrPrivileges";
    public static final String KEY_PREINSTALL_URLS = "preInstallUrls";
    public static final String KEY_ENABLE_PRE_INSTALL_HOOKS = "enablePreInstallHooks";
    public static final String KEY_INSTALL_HOOK_POLICY = "installHookPolicy";

    private final URL base;
    private final String name;
    private final JsonObject originalJson;
    private final List<String> checklists;
    private final List<URL> preInstallUrls;
    private final List<JcrNs> jcrNamespaces;
    private final List<QNodeTypeDefinition> jcrNodetypes;
    private final List<PrivilegeDefinition> jcrPrivileges;
    private final List<ForcedRoot> forcedRoots;
    private final List<CheckSpec> checks;
    private final boolean enablePreInstallHooks;
    private final InstallHookPolicy installHookPolicy;

    private OakpalPlan(final @Nullable URL base,
                       final @Nullable JsonObject originalJson,
                       final @NotNull String name,
                       final @NotNull List<String> checklists,
                       final @NotNull List<URL> preInstallUrls,
                       final @NotNull List<JcrNs> jcrNamespaces,
                       final @NotNull List<QNodeTypeDefinition> jcrNodetypes,
                       final @NotNull List<PrivilegeDefinition> jcrPrivileges,
                       final @NotNull List<ForcedRoot> forcedRoots,
                       final @NotNull List<CheckSpec> checks,
                       final boolean enablePreInstallHooks,
                       final @Nullable InstallHookPolicy installHookPolicy) {
        this.base = base;
        this.originalJson = originalJson;
        this.name = name;
        this.checklists = checklists;
        this.preInstallUrls = preInstallUrls;
        this.jcrNamespaces = jcrNamespaces;
        this.jcrNodetypes = jcrNodetypes;
        this.jcrPrivileges = jcrPrivileges;
        this.forcedRoots = forcedRoots;
        this.checks = checks;
        this.enablePreInstallHooks = enablePreInstallHooks;
        this.installHookPolicy = installHookPolicy;
    }

    public URL getBase() {
        return this.base;
    }

    public String getName() {
        return this.name;
    }

    public JsonObject getOriginalJson() {
        return originalJson;
    }

    public List<String> getChecklists() {
        return checklists;
    }

    public List<URL> getPreInstallUrls() {
        return preInstallUrls;
    }

    public List<JcrNs> getJcrNamespaces() {
        return jcrNamespaces;
    }

    public List<QNodeTypeDefinition> getJcrNodetypes() {
        return jcrNodetypes;
    }

    public List<PrivilegeDefinition> getJcrPrivileges() {
        return jcrPrivileges;
    }

    public List<ForcedRoot> getForcedRoots() {
        return forcedRoots;
    }

    public List<CheckSpec> getChecks() {
        return checks;
    }

    public boolean isEnablePreInstallHooks() {
        return enablePreInstallHooks;
    }

    public InstallHookPolicy getInstallHookPolicy() {
        return installHookPolicy;
    }

    static URI relativizeToBaseParent(final @NotNull URI baseUri, final @NotNull URI uri) throws URISyntaxException {
        if (baseUri.isOpaque() || uri.isOpaque()) {
            return uri;
        } else if (baseUri.getPath().contains("/") && baseUri.getPath().endsWith(".json")) {
            final String basePath = baseUri.getPath().substring(0, baseUri.getPath().lastIndexOf("/") + 1);
            final URI newBase = new URI(baseUri.getScheme(), baseUri.getUserInfo(), baseUri.getHost(),
                    baseUri.getPort(), basePath, baseUri.getQuery(), baseUri.getFragment());
            return newBase.relativize(uri);
        } else {
            return baseUri.relativize(uri);
        }
    }

    @Override
    public JsonObject toJson() {
        if (this.originalJson != null) {
            return this.originalJson;
        }

        final List<String> preInstallStrings = new ArrayList<>();

        if (!preInstallUrls.isEmpty()) {
            preInstallStrings.addAll(Optional.ofNullable(base)
                    .map(result1(URL::toURI))
                    .map(baseUriResult -> baseUriResult.flatMap(baseUri ->
                            preInstallUrls.stream()
                                    .map(result1(URL::toURI))
                                    .map(uriResult -> uriResult
                                            .map(compose(uncheck1(uri -> relativizeToBaseParent(baseUri, uri)),
                                                    URI::toString)))
                                    .collect(Result.tryCollect(Collectors.toList()))
                    ))
                    .orElseGet(() -> Result.failure("Plan base URI is empty. preInstallUrls will not be relativized"))
                    .getOrElse(() -> preInstallUrls.stream().map(URL::toExternalForm).collect(Collectors.toList())));
        }

        final NamespaceMapping mapping = JsonCnd.toNamespaceMapping(jcrNamespaces);
        return JavaxJson.obj()
                .key(KEY_PREINSTALL_URLS).opt(preInstallStrings)
                .key(KEY_CHECKLISTS).opt(checklists)
                .key(KEY_CHECKS).opt(checks)
                .key(KEY_FORCED_ROOTS).opt(forcedRoots)
                .key(KEY_JCR_NODETYPES).opt(JsonCnd.toJson(jcrNodetypes, mapping))
                .key(KEY_JCR_PRIVILEGES).opt(JsonCnd.privilegesToJson(jcrPrivileges, mapping))
                .key(KEY_JCR_NAMESPACES).opt(jcrNamespaces)
                .key(KEY_ENABLE_PRE_INSTALL_HOOKS).opt(enablePreInstallHooks, false)
                .key(KEY_INSTALL_HOOK_POLICY).opt(installHookPolicy)
                .get();
    }

    InitStage toInitStage() {
        LOGGER.debug("[Plan#toInitStage] json={}", this.toJson());
        InitStage.Builder builder = new InitStage.Builder();

        for (JcrNs ns : jcrNamespaces) {
            builder = builder.withNs(ns.getPrefix(), ns.getUri());
        }

        builder.withQNodeTypes(jcrNodetypes);

        for (PrivilegeDefinition privilege : jcrPrivileges) {
            builder = builder.withPrivilegeDefinition(privilege);
        }

        for (ForcedRoot forcedRoot : forcedRoots) {
            builder = builder.withForcedRoot(forcedRoot);
        }

        return builder.build();
    }

    public OakMachine.Builder toOakMachineBuilder(final @Nullable ErrorListener errorListener,
                                                  final @NotNull ClassLoader classLoader) throws Exception {
        final ChecklistPlanner checklistPlanner = new ChecklistPlanner(checklists);
        checklistPlanner.discoverChecklists(classLoader);

        final List<ProgressCheck> allChecks;
        try {
            allChecks = new ArrayList<>(Locator.loadFromCheckSpecs(
                    checklistPlanner.getEffectiveCheckSpecs(checks), classLoader));
        } catch (final Exception e) {
            throw new Exception("Error while loading progress checks.", e);
        }

        return new OakMachine.Builder()
                .withErrorListener(errorListener)
                .withProgressChecks(allChecks)
                .withInitStages(checklistPlanner.getInitStages())
                .withInitStage(toInitStage())
                .withPreInstallUrls(preInstallUrls)
                .withInstallHookPolicy(installHookPolicy)
                .withInstallHookClassLoader(classLoader)
                .withEnablePreInstallHooks(enablePreInstallHooks);
    }


    private static OakpalPlan fromJson(final @NotNull Builder builder,
                                       final @NotNull JsonObject json) {
        if (hasNonNull(json, KEY_PREINSTALL_URLS) && builder.base != null) {
            builder.withPreInstallUrls(JavaxJson.mapArrayOfStrings(json.getJsonArray(KEY_PREINSTALL_URLS),
                    uncheck1(url -> new URL(builder.base, url))));
        }
        if (hasNonNull(json, KEY_CHECKLISTS)) {
            builder.withChecklists(JavaxJson.mapArrayOfStrings(json.getJsonArray(KEY_CHECKLISTS)));
        }
        if (hasNonNull(json, KEY_CHECKS)) {
            builder.withChecks(JavaxJson.mapArrayOfObjects(json.getJsonArray(KEY_CHECKS), CheckSpec::fromJson));
        }
        if (hasNonNull(json, KEY_FORCED_ROOTS)) {
            builder.withForcedRoots(JavaxJson.mapArrayOfObjects(json.getJsonArray(KEY_FORCED_ROOTS), ForcedRoot::fromJson));
        }
        final NamespaceMapping mapping;
        if (hasNonNull(json, KEY_JCR_NAMESPACES)) {
            final List<JcrNs> jcrNsList = JavaxJson.mapArrayOfObjects(json.getJsonArray(KEY_JCR_NAMESPACES),
                    JcrNs::fromJson);
            mapping = JsonCnd.toNamespaceMapping(jcrNsList);
            builder.withJcrNamespaces(jcrNsList);
        } else {
            mapping = JsonCnd.BUILTIN_MAPPINGS;
        }
        if (hasNonNull(json, KEY_JCR_NODETYPES)) {
            builder.withJcrNodetypes(JsonCnd.getQTypesFromJson(json.getJsonObject(KEY_JCR_NODETYPES), mapping));
        }
        if (hasNonNull(json, KEY_JCR_PRIVILEGES)) {
            builder.withJcrPrivileges(JsonCnd.getPrivilegesFromJson(json.get(KEY_JCR_PRIVILEGES), mapping));
        }
        if (hasNonNull(json, KEY_ENABLE_PRE_INSTALL_HOOKS)) {
            builder.withEnablePreInstallHooks(json.getBoolean(KEY_ENABLE_PRE_INSTALL_HOOKS));
        }
        if (hasNonNull(json, KEY_INSTALL_HOOK_POLICY)) {
            builder.withInstallHookPolicy(InstallHookPolicy.forName(
                    json.getString(KEY_INSTALL_HOOK_POLICY)));
        }
        return builder.build(json);
    }

    /**
     * Constructs an OakpalPlan without a base url or a name.
     *
     * @param json the json object to read.
     * @return an OakpalPlan, guaranteed
     */
    public static OakpalPlan fromJson(final @NotNull JsonObject json) {
        return fromJson(new Builder(null, null), json);
    }

    public static Result<OakpalPlan> fromJson(final @NotNull URL jsonUrl) {
        try (JsonReader reader = Json.createReader(jsonUrl.openStream())) {
            final JsonObject json = reader.readObject();
            final Builder builder = new Builder(jsonUrl, Text.getName(jsonUrl.getPath()));
            return Result.success(fromJson(builder, json));
        } catch (Exception e) {
            return Result.failure(e);
        }
    }

    public static final class Builder {
        private final URL base;
        private final String name;
        private List<String> checklists = Collections.emptyList();
        private List<URL> preInstallUrls = Collections.emptyList();
        private List<JcrNs> jcrNamespaces = Collections.emptyList();
        private List<QNodeTypeDefinition> jcrNodetypes = Collections.emptyList();
        private List<PrivilegeDefinition> jcrPrivileges = Collections.emptyList();
        private List<ForcedRoot> forcedRoots = Collections.emptyList();
        private List<CheckSpec> checks = Collections.emptyList();
        private boolean enablePreInstallHooks;
        private InstallHookPolicy scanInstallHookPolicy;

        public Builder(final @Nullable URL base, final @Nullable String name) {
            this.base = base;
            this.name = Optional.ofNullable(name)
                    .orElseGet(() -> Optional.ofNullable(base)
                            .map(compose(URL::getPath, Text::getName))
                            .filter(inferTest1(String::isEmpty).negate())
                            .orElse(DEFAULT_PLAN_NAME));
        }

        public Builder startingWithPlan(final @NotNull OakpalPlan plan) {
            return this.withChecklists(plan.getChecklists())
                    .withChecks(plan.getChecks())
                    .withForcedRoots(plan.getForcedRoots())
                    .withJcrNamespaces(plan.getJcrNamespaces())
                    .withJcrNodetypes(plan.getJcrNodetypes())
                    .withJcrPrivileges(plan.getJcrPrivileges())
                    .withEnablePreInstallHooks(plan.isEnablePreInstallHooks())
                    .withInstallHookPolicy(plan.getInstallHookPolicy())
                    .withPreInstallUrls(plan.getPreInstallUrls());
        }

        public Builder withChecklists(final @NotNull List<String> checklists) {
            this.checklists = new ArrayList<>(checklists);
            return this;
        }

        public Builder withPreInstallUrls(final @NotNull List<URL> preInstallUrls) {
            this.preInstallUrls = new ArrayList<>(preInstallUrls);
            return this;
        }

        public Builder withJcrNamespaces(final @NotNull List<JcrNs> jcrNamespaces) {
            this.jcrNamespaces = new ArrayList<>(jcrNamespaces);
            return this;
        }

        public Builder withJcrNodetypes(final @NotNull List<QNodeTypeDefinition> jcrNodetypes) {
            this.jcrNodetypes = new ArrayList<>(jcrNodetypes);
            return this;
        }

        public Builder withJcrPrivileges(final @NotNull List<PrivilegeDefinition> jcrPrivileges) {
            this.jcrPrivileges = new ArrayList<>(jcrPrivileges);
            return this;
        }

        public Builder withForcedRoots(final @NotNull List<ForcedRoot> forcedRoots) {
            this.forcedRoots = new ArrayList<>(forcedRoots);
            return this;
        }

        public Builder withChecks(final @NotNull List<CheckSpec> checks) {
            this.checks = new ArrayList<>(checks);
            return this;
        }

        public Builder withEnablePreInstallHooks(final boolean skipInstallHooks) {
            this.enablePreInstallHooks = skipInstallHooks;
            return this;
        }

        public Builder withInstallHookPolicy(final InstallHookPolicy scanInstallHookPolicy) {
            this.scanInstallHookPolicy = scanInstallHookPolicy;
            return this;
        }

        private OakpalPlan build(final @Nullable JsonObject originalJson) {
            return new OakpalPlan(base, originalJson, name, checklists, preInstallUrls, jcrNamespaces,
                    jcrNodetypes, jcrPrivileges, forcedRoots, checks, enablePreInstallHooks, scanInstallHookPolicy);
        }

        public OakpalPlan build() {
            return build(null);
        }
    }
}
