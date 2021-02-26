package Verification;

import org.semanticweb.owlapi.model.OWLOntology;

public class VerificationChecker {

    public boolean satisfiesEquivalenceForFocusConcepts(OWLOntology sourceOntology, OWLOntology subOntology) {
        boolean satisfiesRequirement = true;
        //Takes as input the source ontology and a computed subontology.
        //TODO: rename the focus concepts in the subontology, store lookup for original name vs rename
        //TODO: add the renamed content to the source ontology, then check equivalence between original concepts and their subontology (renamed) versions
        //TODO: question -- is it sufficient to just add focus concept definitions from subontology to source?
        return satisfiesRequirement;
    }

    public boolean satisfiesTransitiveClosureRequirement(OWLOntology sourceOntology, OWLOntology subOntology) {
        //TODO: here, need to compute the transitive closure wrt subsumption for all concepts within the subontology signature. Result wrt source and wrt sub should then be equal.
        //TODO: main question, how best to do this for the source ontology?
        boolean satisfiesRequirement = true;
        return satisfiesRequirement;
    }
}
