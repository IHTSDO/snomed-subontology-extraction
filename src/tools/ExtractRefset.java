package tools;

import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;

public class ExtractRefset {
    public void extractRefsetFromDescendents(OWLOntology inputOntology, OWLClass rootClass) {
        /*
        OntologyReasoningService service = new OntologyReasoningService(inputOntology);
        service.classifyOntology();
        for(OWLClass cls:inputOntology.getClassesInSignature()) {
            if(cls.toString().contains("763158003")) {
                System.out.println("Medicinal product: " + cls.toString());
                conceptsToDefine.add(cls);
                for(OWLClass childCls:service.getDescendentClasses(cls)) {
                    if(!service.isPrimitive(childCls)) {
                        System.out.println("Class: " + childCls);
                        conceptsToDefine.add(childCls);
                    }
                }
            }
        }
        Charset UTF_8_CHARSET = Charset.forName("UTF-8");
        String outputFilePath = "E:/Users/warren/Documents/aPostdoc/code/~test-code/example-refsets/medicinal-products-demo-refset.txt";
        BufferedWriter writer = new BufferedWriter (new OutputStreamWriter(new FileOutputStream(outputFilePath), UTF_8_CHARSET));
        for(OWLClass cls:conceptsToDefine) {
            System.out.println("cls: " + cls.toString());
            writer.write(cls.toString().replace("http://snomed.info/id/",""));
            writer.newLine();
        }

         */
    }
}
