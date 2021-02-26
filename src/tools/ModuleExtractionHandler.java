package tools;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import uk.ac.manchester.cs.owlapi.modularity.ModuleType;
import uk.ac.manchester.cs.owlapi.modularity.SyntacticLocalityModuleExtractor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ModuleExtractionHandler {


    public static OWLOntology extractSingleModule(OWLOntology inputOntology, Set<OWLEntity> inputSignature, ModuleType moduleType) throws OWLOntologyCreationException {
        OWLOntologyManager inputOntologyManager = inputOntology.getOWLOntologyManager();
        SyntacticLocalityModuleExtractor extractor = new SyntacticLocalityModuleExtractor(inputOntologyManager, inputOntology, moduleType);

        System.out.println("Extracting " + moduleType.toString() + " module for ontology using " + inputSignature.size() + " signature size.");

        return inputOntologyManager.createOntology(extractor.extract(inputSignature));
    }

    public static Map<ModuleType, OWLOntology> extractMultipleModuleTypes(OWLOntology inputOntology, Set<OWLEntity> inputSignature, Set<ModuleType> moduleTypes) throws OWLOntologyCreationException {
        Map<ModuleType, OWLOntology> modulesComputed = new HashMap<ModuleType, OWLOntology>();

        for(ModuleType type:moduleTypes) {
            modulesComputed.put(type, extractSingleModule(inputOntology, inputSignature, type));
        }

        return modulesComputed;
    }
}
