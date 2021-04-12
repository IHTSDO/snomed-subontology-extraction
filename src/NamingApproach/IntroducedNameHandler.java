package NamingApproach;

import ResultsWriters.MapPrinter;
import org.semanticweb.owlapi.model.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class IntroducedNameHandler {

    private Map<OWLObjectSomeValuesFrom, OWLClass> pvNamingMap;
    private Map<OWLClass, OWLObjectSomeValuesFrom> namingPvMap;
    private Map<OWLClass, OWLClassExpression> namedGCIMap;
    private Map<OWLClass, OWLClass> nameAndOriginalGCIConceptMap; //TODO: improve.
    private OWLOntology originalOntology;
    private OWLDataFactory df;
    private OWLOntologyManager man;
    private String IRIName = "http://snomed.info/id/";

    public IntroducedNameHandler(OWLOntology inputOntology) {
        pvNamingMap = new HashMap<OWLObjectSomeValuesFrom, OWLClass>();
        namingPvMap = new HashMap<OWLClass, OWLObjectSomeValuesFrom>();
        namedGCIMap  = new HashMap<OWLClass, OWLClassExpression>();
        nameAndOriginalGCIConceptMap = new HashMap<OWLClass, OWLClass>();
        originalOntology = inputOntology;
        man = originalOntology.getOWLOntologyManager();
        df = man.getOWLDataFactory();
    }

    public OWLOntology returnOntologyWithNamings() throws OWLOntologyCreationException {
        OWLOntology inputOntologyWithNamings = man.createOntology();

        //name property values, add equiv axioms for names
        namePropertyValuesInOntology();

        Set<OWLAxiom> pvNamingAxioms = new HashSet<OWLAxiom>();
        for(Map.Entry<OWLObjectSomeValuesFrom, OWLClass> entry:pvNamingMap.entrySet()) {
            OWLObjectSomeValuesFrom pv = entry.getKey();
            OWLClass pvName = entry.getValue();
            pvNamingAxioms.add(df.getOWLEquivalentClassesAxiom(pv, pvName));
        }

        //name LHS of GCI axioms, add equiv axioms for names
        nameGCIs();

        Set<OWLAxiom> gciNamingAxioms = new HashSet<OWLAxiom>();
        for(Map.Entry<OWLClass, OWLClassExpression> entry:namedGCIMap.entrySet()) {
            gciNamingAxioms.add(df.getOWLEquivalentClassesAxiom(entry.getValue(), entry.getKey()));
        }

        man.addAxioms(inputOntologyWithNamings, originalOntology.getAxioms());
        man.addAxioms(inputOntologyWithNamings, pvNamingAxioms);
        man.addAxioms(inputOntologyWithNamings, gciNamingAxioms);

        return inputOntologyWithNamings;
    }

    private void namePropertyValuesInOntology() {
        //note: this does *not* separate GCIs from non-GCI PVs.
        for (OWLClassExpression exp : originalOntology.getNestedClassExpressions()) {
            if (exp instanceof OWLObjectSomeValuesFrom) {
                OWLObjectSomeValuesFrom pv = (OWLObjectSomeValuesFrom) exp;
                String pvName = producePVName();

                pvNamingMap.putIfAbsent(pv, df.getOWLClass(IRI.create(IRIName, pvName)));
                namingPvMap.putIfAbsent(df.getOWLClass(IRI.create(IRIName, pvName)), pv);
            }
        }
    }

    private void nameGCIs() {
        Set<OWLSubClassOfAxiom> gciAxioms = new HashSet<OWLSubClassOfAxiom>();
        for(OWLClass cls:originalOntology.getClassesInSignature()) {
            //in SCT, GCI axioms are of form B and R some C <= A, i.e., no GCIs with anonymous superclass.
            for(OWLSubClassOfAxiom ax:originalOntology.getSubClassAxiomsForSuperClass(cls)) {
                if(ax.isGCI()) {
                    gciAxioms.add(ax);
                }
            }
        }

        for(OWLSubClassOfAxiom gci:gciAxioms) {
            OWLClassExpression gciExpression = gci.getSubClass();
            String gciName = produceGCIName();
            OWLClass gciClass = (OWLClass) gci.getSuperClass();
            OWLClass gciNameClass = df.getOWLClass(IRI.create(IRIName, gciName));

            namedGCIMap.putIfAbsent(gciNameClass, gciExpression);
            nameAndOriginalGCIConceptMap.putIfAbsent(gciNameClass, gciClass);
        }
    }

   // private void visitPropertyValues() {
   //     OWLObjectVisitor v = new OWLObjectVisitorAdapter() {
   //         public void visit(OWLObjectSomeValuesFrom pv) {
    //
     //       }
    //    };
   // }

    private String producePVName() {
        return "PV_" + pvNamingMap.size();
    }
    private String produceGCIName() {
        return "GCI_" + namedGCIMap.size();
    }

    public boolean isNamedPV(OWLClass cls) {
        return namingPvMap.containsKey(cls);
    }

    public boolean isNamedGCI(OWLClass cls) {
        return namedGCIMap.containsKey(cls);
    }

    public OWLClass retrieveNameForPV(OWLObjectSomeValuesFrom pv) {
        return pvNamingMap.get(pv);
    }

    public OWLObjectSomeValuesFrom retrievePVForName(OWLClass cls) {
        return namingPvMap.get(cls);
    }

    public Set<OWLObjectSomeValuesFrom> retrievePVsFromNames(Set<OWLClass> renamedPVs) {
        Set<OWLObjectSomeValuesFrom> pvs = new HashSet<OWLObjectSomeValuesFrom>();
        for(OWLClass cls : renamedPVs) {
            pvs.add(namingPvMap.get(cls));
        }
        return pvs;
    }

    public Set<OWLClass> retrieveNamesFromPVs(Set<OWLObjectSomeValuesFrom> pvs) {
        Set<OWLClass> names = new HashSet<OWLClass>();
        for(OWLObjectSomeValuesFrom pv : pvs) {
            names.add(pvNamingMap.get(pv));
        }
        return names;
    }

    public Set<OWLClass> retrieveAllNamesForGCIs() {
        return namedGCIMap.keySet();
    }

    public OWLClass retrieveOriginalClassFromNamedGCI(OWLClass gciName) {
        return nameAndOriginalGCIConceptMap.get(gciName);
    }

    public void resetNames() {
        pvNamingMap.clear();
        namingPvMap.clear();
        namedGCIMap.clear();
    }

    /*
    public Map<OWLClass, OWLObjectSomeValuesFrom> getNamingPvMap() {
        return namingPvMap;
    }
    public Map<OWLObjectSomeValuesFrom, OWLClass> getPvNamingMap() {
        return pvNamingMap;
    }
     */
    public void printNameAndPvPairs(String outputPath) throws IOException {
        MapPrinter printer = new MapPrinter(outputPath);
        System.out.println("Printing naming map.");
        printer.printNamingsForPVs(pvNamingMap);
    }
}
