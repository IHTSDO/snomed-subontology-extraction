package tools;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.base.Optional;
import org.semanticweb.owlapi.model.*;

public class OntologyStringExtractor {

    public OntologyStringExtractor() {}

    public static String extractIRI(OWLOntology inputOntology) {
        Optional<IRI> iriOptional = inputOntology.getOntologyID().getOntologyIRI();

        if(!iriOptional.isPresent()) {
            return extractAbsentIRI(inputOntology);
        }

        return iriOptional.get().toString();
    }

    public static String extractAbsentIRI(OWLOntology ontology) {
        List<OWLClass> conc = new ArrayList<OWLClass>(ontology.getClassesInSignature());
        String IRIName;
        /*
		if(conc.size() == 0) {
			System.out.println("No classes in ontology. Checking IRI of object property instead.");
			List<OWLObjectProperty> concProps = new ArrayList<OWLObjectProperty>(ontology.getObjectPropertiesInSignature());
			concString = concProps.get(0).toString();
		}
		else {
			concString = conc.get(0).toString();
		}
         */
        if(conc.size() != 0) {
            String concString = conc.get(0).toString();
            System.out.println("concString: " + concString);
            int i=0;

            while(!concString.contains("#") && i < conc.size()) {
                concString = conc.get(i).toString();
                i++;
                //System.out.println(concString);
            }

            concString = concString.replace("<", "");
            concString = concString.replace(">", "");
            //System.out.println("CONC: " + concString);
            if(concString.contains("#")) {
                IRIName = concString.substring(0, concString.lastIndexOf('#')+1);
            }
            else {
                IRIName = concString.substring(0, concString.lastIndexOf('/')+1);
            }
            //System.out.println("HERE IS THE IRI USED: " + IRIName);
        }
        else if(ontology.getObjectPropertiesInSignature().size() != 0){
            List<OWLObjectProperty> obj = new ArrayList<OWLObjectProperty>(ontology.getObjectPropertiesInSignature());
            String objString = obj.get(0).toString();
            int j=0;
            while(!objString.contains("#") && j < obj.size()) {
                objString = obj.get(j).toString();
                j++;
            }
            objString = objString.replace("<", "");
            objString = objString.replace("<", "");

            if(objString.contains("#")) {
                IRIName = objString.substring(0,  objString.lastIndexOf('#')+1);
            }
            else {
                IRIName = objString.substring(0, objString.lastIndexOf('/')+1);
            }
        }
        else {
            IRIName = "Empty ontology, no IRI returned by string extractor.";
        }

        System.out.println("IRI NAME RETURNED: " + IRIName);
        return IRIName;

    }

    public static Set<String> extractConceptNames(OWLOntology ontology) {
        Set<String> conceptNames = new HashSet<String>();

        for(OWLEntity ent:ontology.getClassesInSignature()) {
            conceptNames.add(ent.toString().replaceAll(".*#", "").replaceAll(">", ""));
        }

        return conceptNames;
    }

}
