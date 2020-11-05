import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;
import uk.ac.manchester.cs.owlapi.modularity.SyntacticLocalityModuleExtractor;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class ExtractModuleTest {

    public static void main(String[] args) throws OWLOntologyCreationException, OWLOntologyStorageException {
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLOntology inputOntology = man.loadOntologyFromOntologyDocument(new File("E:/Users/warren/Documents/aPostdoc/code/~test-code/abstract-definitions-test/sct/sct-july-2020.owl"));

        SyntacticLocalityModuleExtractor moduleExtractor = new SyntacticLocalityModuleExtractor(man,
                inputOntology, uk.ac.manchester.cs.owlapi.modularity.ModuleType.TOP);

        Set<OWLEntity> classesForModule = new HashSet<OWLEntity>();
        for(OWLClass cls:inputOntology.getClassesInSignature()) {
            if(cls.toString().contains("782980002")) {
                System.out.println("Class: " + cls);
                classesForModule.add(cls);
                break;
            }
        }

        IRI inputIRI = inputOntology.getOntologyID().getOntologyIRI().get();

        OWLOntology module = moduleExtractor.extractAsOntology(classesForModule, IRI.generateDocumentIRI());
        man.saveOntology(module, new OWLXMLDocumentFormat(),
                IRI.create(new File("E:/Users/warren/Documents/aPostdoc/code/~test-code/abstract-definitions-test/comparison-with-toolkit/test-modules/" +
                        "Emapalumab_module.owl")));
    }
}
