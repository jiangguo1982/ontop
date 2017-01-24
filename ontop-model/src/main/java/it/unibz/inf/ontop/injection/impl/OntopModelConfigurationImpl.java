package it.unibz.inf.ontop.injection.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import it.unibz.inf.ontop.executor.ProposalExecutor;
import it.unibz.inf.ontop.injection.InvalidOntopConfigurationException;
import it.unibz.inf.ontop.injection.OntopModelConfiguration;
import it.unibz.inf.ontop.injection.OntopModelSettings;
import it.unibz.inf.ontop.pivotalrepr.utils.ExecutorRegistry;
import it.unibz.inf.ontop.pivotalrepr.utils.impl.StandardExecutorRegistry;
import it.unibz.inf.ontop.pivotalrepr.proposal.QueryOptimizationProposal;
import it.unibz.inf.ontop.utils.ImmutableCollectors;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OntopModelConfigurationImpl implements OntopModelConfiguration {

    private final OntopModelConfigurationOptions options;
    private final OntopModelSettings settings;
    private ExecutorRegistry executorRegistry;
    private Injector injector;

    protected OntopModelConfigurationImpl(OntopModelSettings settings, OntopModelConfigurationOptions options) {
        this.settings = settings;
        this.options = options;

        // Will be built on-demand
        this.executorRegistry = null;
        this.injector = null;
    }

    @Override
    public ExecutorRegistry getExecutorRegistry() {
        if (executorRegistry == null) {
            executorRegistry = new StandardExecutorRegistry(getInjector(), generateOptimizationConfigurationMap());
        }
        return executorRegistry;
    }

    @Override
    public final Injector getInjector() {
        if (injector == null) {

            Set<Class> moduleClasses = new HashSet();

            // Only keeps the first instance of a module class
            ImmutableList<Module> modules = buildGuiceModules()
                    .filter(m -> moduleClasses.add(m.getClass()))
                    .collect(ImmutableCollectors.toList());

            injector = Guice.createInjector(modules);
        }
        return injector;
    }

    /**
     * Can be overloaded by sub-classes
     */
    protected ImmutableMap<Class<? extends QueryOptimizationProposal>, Class<? extends ProposalExecutor>>
        generateOptimizationConfigurationMap() {
        return ImmutableMap.of();
    }

    /**
     * To be overloaded
     *
     */
    protected Stream<Module> buildGuiceModules() {
        return Stream.of(new OntopModelModule(this));
    }

    /**
     * To be overloaded
     */
    @Override
    public void validate() throws InvalidOntopConfigurationException {
    }

    @Override
    public OntopModelSettings getSettings() {
        return settings;
    }


    /**
     * Groups all the options required by the OntopModelConfiguration.
     *
     */
    public static class OntopModelConfigurationOptions {

        public OntopModelConfigurationOptions() {
        }
    }

    protected static class DefaultOntopModelBuilderFragment<B extends Builder<B>> implements OntopModelBuilderFragment<B> {

        private final B builder;
        private Optional<Boolean> testMode = Optional.empty();

        /**
         * To be called when NOT INHERITING
         */
        protected DefaultOntopModelBuilderFragment(B builder) {
            this.builder = builder;
        }

        /**
         * To be called ONLY by sub-classes
         */
        protected DefaultOntopModelBuilderFragment() {
            this.builder = (B) this;
        }


        private Optional<Properties> inputProperties = Optional.empty();

        /**
         * Have precedence over other parameters
         */
        @Override
        public final B properties(@Nonnull Properties properties) {
            this.inputProperties = Optional.of(properties);
            return builder;
        }

        @Override
        public final B propertyFile(String propertyFilePath) {
            try {
                URI fileURI = new URI(propertyFilePath);
                String scheme = fileURI.getScheme();
                if (scheme == null) {
                    return propertyFile(new File(fileURI.getPath()));
                }
                else if (scheme.equals("file")) {
                    return propertyFile(new File(fileURI));
                }
                else {
                    throw new InvalidOntopConfigurationException("Currently only local property files are supported.");
                }
            } catch (URISyntaxException e) {
                throw new InvalidOntopConfigurationException("Invalid property file path: " + e.getMessage());
            }
        }

        @Override
        public final B propertyFile(File propertyFile) {
            try {
                Properties p = new Properties();
                p.load(new FileReader(propertyFile));
                return properties(p);

            } catch (IOException e) {
                throw new InvalidOntopConfigurationException("Cannot reach the property file: " + propertyFile);
            }
        }

        @Override
        public B enableTestMode() {
            testMode = Optional.of(true);
            return builder;
        }

        /**
         *
         * Derived properties have the highest precedence over input properties.
         *
         * Can be overloaded. Don't forget to call the parent!
         *
         */
        protected Properties generateProperties() {
            Properties properties = new Properties();
            inputProperties.ifPresent(properties::putAll);
            testMode.ifPresent(isEnabled -> properties.put(OntopModelSettings.TEST_MODE, isEnabled));
            return properties;
        }

        protected final OntopModelConfigurationOptions generateModelOptions() {
            return new OntopModelConfigurationOptions();
        }

    }

    /**
     * Builder
     *
     */
    public final static class BuilderImpl<B extends Builder<B>> extends DefaultOntopModelBuilderFragment<B>
            implements Builder<B> {

        @Override
        public final OntopModelConfiguration build() {
            Properties p = generateProperties();

            return new OntopModelConfigurationImpl(
                    new OntopModelSettingsImpl(p),
                    generateModelOptions());
        }
    }
}
