package it.unibz.inf.ontop.docker.dreamio;

import com.google.common.collect.ImmutableList;
import it.unibz.inf.ontop.docker.AbstractLeftJoinProfTest;
import it.unibz.inf.ontop.owlapi.OntopOWLReasoner;
import it.unibz.inf.ontop.owlapi.connection.OntopOWLConnection;
import it.unibz.inf.ontop.owlapi.connection.OntopOWLStatement;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.semanticweb.owlapi.model.OWLException;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

public class LeftJoinProfDremioMySQLTest extends AbstractLeftJoinProfTest {


    private static final String owlFileName = "/redundant_join/redundant_join_fk_test.owl";
    private static final String obdaFileName = "/redundant_join/dremio/redundant_join_fk_test_mysql.obda";
    private static final String propertyFileName = "/dremio/redundant_join_fk_test.properties";

    private static OntopOWLReasoner REASONER;
    private static OntopOWLConnection CONNECTION;

    @BeforeClass
    public static void before() throws OWLOntologyCreationException {
        REASONER = createReasoner(owlFileName, obdaFileName, propertyFileName);
        CONNECTION = REASONER.getConnection();
    }

    @Override
    protected OntopOWLStatement createStatement() throws OWLException {
        return CONNECTION.createStatement();
    }

    @AfterClass
    public static void after() throws OWLException {
        CONNECTION.close();
        REASONER.dispose();
    }

    @Override
    protected ImmutableList<String> getExpectedValuesMultitypedAvg1() {
        return ImmutableList.of("15.5", "16.0", "18.875");
    }

    // TODO: investigate with a more recent version of MySQL
    @Test
    @Ignore("MySQL 5.7 seems to have issues with the encoding of MINUS as a LJ and FILTER IS NULL")
    @Override
    public void testMinusMultitypedSum() throws Exception {
        super.testMinusMultitypedSum();
    }

    // TODO: investigate with a more recent version of MySQL
    @Test
    @Ignore("MySQL 5.7 seems to have issues with the encoding of MINUS as a LJ and FILTER IS NULL")
    @Override
    public void testMinusMultitypedAvg() throws Exception {
        super.testMinusMultitypedAvg();
    }

    @Override
    protected ImmutableList<String> getExpectedValuesDuration1() {
        return ImmutableList.of("0", "0", "0", "0", "0", "18.000", "20.000", "54.500");
    }

    @Override
    protected ImmutableList<String> getExpectedValuesMultitypedSum1(){
        return ImmutableList.of("31.000", "32.000", "75.500");
    }


}