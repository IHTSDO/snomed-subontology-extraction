package SubOntologyExtraction;

import org.semanticweb.owlapi.model.*;

import java.util.*;

public class DefinitionExpansionChecker {
    private static SubOntologyExtractionHandler extractor;

    public DefinitionExpansionChecker(SubOntologyExtractionHandler extractionHandler) {
        this.extractor = extractionHandler;
    }

    public static boolean supportingDefinitionRequired(OWLClass cls) {
        return !Collections.disjoint(extractor.getSourceOntologyReasoner().getDescendantClasses(cls), extractor.getFocusClasses());
    }

    //TODO: make clear, only checks cases with class as filler, not complex filler.
    public static boolean supportingDefinitionRequired(OWLObjectSomeValuesFrom pv) {
        boolean definitionRequired = false;
        if(pv.getFiller() instanceof OWLClass) {
            Set<OWLSubPropertyChainOfAxiom> chainAxioms = extractor.getSourceOntology().getAxioms(AxiomType.SUB_PROPERTY_CHAIN_OF);

        }
        else {
            System.out.println("Filler must be decomposed. Returning false.");
        }

        return definitionRequired;
    }

}
