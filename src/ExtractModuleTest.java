import ResultsWriters.OntologySaver;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import tools.ModuleExtractionHandler;
import tools.InputSignatureHandler;
import uk.ac.manchester.cs.owlapi.modularity.ModuleType;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ExtractModuleTest {

    public static void main(String[] args) throws OWLOntologyCreationException, OWLOntologyStorageException {
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLOntology inputOntology = man.loadOntologyFromOntologyDocument(new File("E:/Users/warren/Documents/aPostdoc/SCT-files/sct-july-2018.owl"));
        String outputPath = "E:/Users/warren/Documents/aPostdoc/modules/";

        File signatureFile = new File("E:/Users/warren/Documents/aPostdoc/IAA-content-extraction/refsets/nursing/nursing_full_refset.txt");
        Set<OWLEntity> signature = new HashSet<OWLEntity>(InputSignatureHandler.readRefset(signatureFile));

        Set<ModuleType> typesToExtract = new HashSet<ModuleType>(Arrays.asList(ModuleType.STAR));// ModuleType.TOP));
        Map<ModuleType, OWLOntology> moduleMap = ModuleExtractionHandler.extractMultipleModuleTypes(inputOntology, signature, typesToExtract);


        String refsetName = signatureFile.getName().substring(0, signatureFile.getName().lastIndexOf("."));
        for(Map.Entry<ModuleType, OWLOntology> entry: moduleMap.entrySet()) {
            OntologySaver.saveOntology(entry.getValue(), outputPath+entry.getKey()+"_"+refsetName);
        }
                //new ModuleExtractionHandler(inputOntology, )
    }
}
