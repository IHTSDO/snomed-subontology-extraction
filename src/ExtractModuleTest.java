import ResultsWriters.OntologySaver;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;
import tools.ModuleExtractionHandler;
import tools.RefsetHandler;
import uk.ac.manchester.cs.owlapi.modularity.ModuleType;
import uk.ac.manchester.cs.owlapi.modularity.SyntacticLocalityModuleExtractor;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ExtractModuleTest {

    public static void main(String[] args) throws OWLOntologyCreationException, OWLOntologyStorageException {
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLOntology inputOntology = man.loadOntologyFromOntologyDocument(new File("E:/Users/warren/Documents/aPostdoc/SCT-files/TM_demo.owl"));
        String outputPath = "E:/Users/warren/Documents/aPostdoc/";

        File inputRefset = new File("E:/Users/warren/Documents/aPostdoc/IAA-content-extraction/refsets/traditional-medicine/tm_input_signatures.txt");

        Set<OWLEntity> signature = new HashSet<OWLEntity>(RefsetHandler.readRefset(inputRefset));

        Set<ModuleType> typesToExtract = new HashSet<ModuleType>(Arrays.asList(ModuleType.BOT, ModuleType.STAR));
        Map<ModuleType, OWLOntology> moduleMap = ModuleExtractionHandler.extractMultipleModuleTypes(inputOntology, signature, typesToExtract);


        String refsetName = inputRefset.getName().substring(0, inputRefset.getName().lastIndexOf("."));
        for(Map.Entry<ModuleType, OWLOntology> entry: moduleMap.entrySet()) {
            OntologySaver.saveOntology(entry.getValue(), outputPath+entry.getKey()+"_"+refsetName);
        }
                //new ModuleExtractionHandler(inputOntology, )
    }
}
