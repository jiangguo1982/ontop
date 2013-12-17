package it.unibz.krdb.obda.obda.quest.dag;

import it.unibz.krdb.obda.ontology.Description;
import it.unibz.krdb.obda.owlrefplatform.core.dagjgrapht.DAGImpl;
import it.unibz.krdb.obda.owlrefplatform.core.dagjgrapht.NamedDAGBuilder;
import it.unibz.krdb.obda.owlrefplatform.core.dagjgrapht.SemanticIndexEngineImpl;
import it.unibz.krdb.obda.owlrefplatform.core.dagjgrapht.SemanticIndexRange;
import it.unibz.krdb.obda.owlrefplatform.core.dagjgrapht.TBoxReasonerImpl;

import java.util.ArrayList;
import java.util.Map;

import junit.framework.TestCase;

import org.jgrapht.Graphs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class S_Indexes_TestNewDAG extends TestCase {
	
	ArrayList<String> input= new ArrayList<String>();

	Logger log = LoggerFactory.getLogger(S_HierarchyTestNewDAG.class);

	public S_Indexes_TestNewDAG (String name){
		super(name);
	}
	
public void setUp(){
		
	
		/** C -> B  -> A  C->A*/
		input.add("src/test/resources/test/newDag/transitive.owl");
		/** C -> B  -> A  C->D ->A C->A */
		input.add("src/test/resources/test/newDag/transitive2.owl");

		/** C = B -> ER -> A*/
		input.add("src/test/resources/test/newDag/equivalents1.owl");
		/** B -> A -> ER=C */
		input.add("src/test/resources/test/newDag/equivalents2.owl");
		/** C->B = ER -> A*/
		input.add("src/test/resources/test/newDag/equivalents3.owl");
		/** ER-> A=B=C */
		input.add("src/test/resources/test/newDag/equivalents4.owl");
		/** C=ER=A->B */
		input.add("src/test/resources/test/newDag/equivalents5.owl");
		/** D-> ER=C=B -> A*/
		input.add("src/test/resources/test/newDag/equivalents6.owl");
		/** P-> ER=B -> A  C=L ->ES-> ER */
		input.add("src/test/resources/test/newDag/equivalents7.owl");
		/** B->A=ET->ER C->ES=D->A*/
		input.add("src/test/resources/test/newDag/equivalents8.owl");

		/** C = B -> ER- -> A*/
		input.add("src/test/resources/test/newDag/inverseEquivalents1.owl");
		/** B -> A -> ER- = C */
		input.add("src/test/resources/test/newDag/inverseEquivalents2.owl");
		/** C->B = ER- -> A*/
		input.add("src/test/resources/test/newDag/inverseEquivalents3.owl");
		/** ER- -> A=B=C */
		input.add("src/test/resources/test/newDag/inverseEquivalents4.owl");
		/** C=ER- =A->B */
		input.add("src/test/resources/test/newDag/inverseEquivalents5.owl");
		/** D-> ER- =C=B -> A*/
		input.add("src/test/resources/test/newDag/inverseEquivalents6.owl");
		/** D->  ER- =C=B -> A*/
		input.add("src/test/resources/test/newDag/inverseEquivalents6b.owl");
		/** P-> ER- =B -> A  C=L ->ES- -> ER- */
		input.add("src/test/resources/test/newDag/inverseEquivalents7.owl");
		/** B->A=ET- ->ER- C->ES- = D->A*/
		input.add("src/test/resources/test/newDag/inverseEquivalents8.owl");

}

public void testIndexes() throws Exception{
	//for each file in the input
	for (int i=0; i<input.size(); i++){
		String fileInput=input.get(i);

		DAGImpl dag= S_InputOWL.createDAG(fileInput);


		//add input named graph
		DAGImpl namedDag= NamedDAGBuilder.getNamedDAG(dag);

		
		log.debug("Input number {}", i+1 );
		log.info("named graph {}", namedDag);
		
		
		assertTrue(testIndexes(namedDag));


	}
}

private boolean testIndexes( DAGImpl dag){
	boolean result=true;
		
	//create semantic index
	SemanticIndexEngineImpl engine= new SemanticIndexEngineImpl(dag);
	Map<Description, Integer> indexes=engine.getIndexes();
	Map<Description, SemanticIndexRange> ranges=engine.getIntervals();
	
	//check that the index of the node is contained in the intervals of the parent node
	for(Description vertex: dag.vertexSet()){
		int index= indexes.get(vertex);
		for(Description parent: Graphs.successorListOf(dag.getDag(), vertex)){
			result=ranges.get(parent).contained(new SemanticIndexRange(index,index));
			
			if(!result)
				break;
		}
		
		
	}
	
	log.info("indexes {}", indexes);
	log.info("ranges {}", ranges);
	
	
	return result;
	

}






}
