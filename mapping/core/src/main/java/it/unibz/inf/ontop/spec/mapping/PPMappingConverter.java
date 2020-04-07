package it.unibz.inf.ontop.spec.mapping;

import com.google.common.collect.ImmutableList;
import it.unibz.inf.ontop.dbschema.DBMetadataBuilder;
import it.unibz.inf.ontop.exception.InvalidMappingSourceQueriesException;
import it.unibz.inf.ontop.iq.tools.ExecutorRegistry;
import it.unibz.inf.ontop.spec.mapping.pp.PreProcessedMapping;
import it.unibz.inf.ontop.spec.mapping.pp.PreProcessedTriplesMap;

/**
 * TODO: explain
 */
public interface PPMappingConverter<T extends PreProcessedTriplesMap, M extends PreProcessedMapping<T>, D extends DBMetadataBuilder> {

    ImmutableList<MappingAssertion> convert(M ppMapping, D dbMetadata, ExecutorRegistry executorRegistry) throws InvalidMappingSourceQueriesException;

}
