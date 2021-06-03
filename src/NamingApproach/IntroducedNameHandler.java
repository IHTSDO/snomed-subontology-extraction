package NamingApproach;

import ResultsWriters.MapPrinter;
import org.semanticweb.owlapi.model.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/*
Data structures: (to be updated / improved)
-pvNamingMap: key is each (unique) OWLClassExpression corresponding to R some C, value is associated name
-namingPvMap: swap above
----
-namedGCIMap: key is a name in the form of OWLClass, value is the original anonymous expression on left-hand side (LHS) of GCI that is associated with the name
-nameAndSuperGCIConceptMap: given a name for an anonymous LHS GCI expression, get the atomic concept present on the RHS of the GCI
-superGCIConceptAndNameMap: given a concept on the RHS of a GCI, get all names for associated anonymous (LHS) GCI concepts
 */

public class IntroducedNameHandler {

    private Map<OWLObjectSomeValuesFrom, OWLClass> pvNamingMap;
    private Map<OWLClass, OWLObjectSomeValuesFrom> namingPvMap;
    private Map<OWLClass, OWLClassExpression> namedGCIMap;
    private Map<OWLClass, OWLClass> nameAndSuperGCIConceptMap; //TODO: improve.
    private Map<OWLClass, Set<OWLClass>> superGCIConceptAndNameMap;
    private OWLOntology originalOntology;
    private OWLDataFactory df;
    private OWLOntologyManager man;
    private String IRIName = "http://snomed.info/id/";

    public IntroducedNameHandler(OWLOntology inputOntology) {
        pvNamingMap = new HashMap<OWLObjectSomeValuesFrom, OWLClass>();
        namingPvMap = new HashMap<OWLClass, OWLObjectSomeValuesFrom>();
        namedGCIMap  = new HashMap<OWLClass, OWLClassExpression>();
        nameAndSuperGCIConceptMap = new HashMap<OWLClass, OWLClass>();
        superGCIConceptAndNameMap = new HashMap<OWLClass, Set<OWLClass>>();
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
       // Set<OWLSubClassOfAxiom> gciAxioms = new HashSet<OWLSubClassOfAxiom>();
        Set<OWLClass> gciSuperClasses = new HashSet<OWLClass>();

        for(OWLClass cls:originalOntology.getClassesInSignature()) {
            //in SCT, GCI axioms are of form B and R some C <= A, i.e., no GCIs with anonymous superclass.
            //assumes equivalence axioms are not flattened.
            for(OWLSubClassOfAxiom ax:originalOntology.getSubClassAxiomsForSuperClass(cls)) {
                if(ax.isGCI()) {
                    gciSuperClasses.add(cls);
                    break;
                }
            }
        }

        for(OWLClass gciSuperCls:gciSuperClasses) {
            Set<OWLClass> gciNames = new HashSet<OWLClass>();
            for(OWLSubClassOfAxiom ax:originalOntology.getSubClassAxiomsForSuperClass(gciSuperCls)) {
                if(ax.isGCI()) {
                    OWLClassExpression gciExpression = ax.getSubClass();
                    String gciNameString = produceGCIName();
                    OWLClass gciClass = (OWLClass) ax.getSuperClass();
                    OWLClass gciNameClass = df.getOWLClass(IRI.create(IRIName, gciNameString));

                    namedGCIMap.putIfAbsent(gciNameClass, gciExpression);
                    nameAndSuperGCIConceptMap.putIfAbsent(gciNameClass, gciClass);
                    gciNames.add(gciNameClass);
                }
            }
            superGCIConceptAndNameMap.putIfAbsent(gciSuperCls, gciNames);
        }
    }

    //PV naming methods
    private String producePVName() {
        return "PV_" + pvNamingMap.size();
    }
    public boolean isNamedPV(OWLClass cls) {
        return namingPvMap.containsKey(cls);
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

    public void printNameAndPvPairs(String outputPath) throws IOException {
        MapPrinter printer = new MapPrinter(outputPath);
        System.out.println("Printing naming map.");
        printer.printNamingsForPVs(pvNamingMap);
    }

    //GCI naming methods //TODO: split classes
    private String produceGCIName() {
        return "GCI_" + namedGCIMap.size();
    }

    public boolean isNamedGCI(OWLClass cls) {
        return namedGCIMap.containsKey(cls);
    }

    public boolean hasAssociatedGCIs(OWLClass cls) {
        return superGCIConceptAndNameMap.containsKey(cls);
    }

    public Set<OWLClass> returnNamesOfGCIsForSuperConcept(OWLClass cls) {
        if(!superGCIConceptAndNameMap.containsKey(cls)) {
            return new HashSet<OWLClass>();
        }
        return superGCIConceptAndNameMap.get(cls);
    }

    public Set<OWLClass> retrieveAllNamesForGCIs() {
        return namedGCIMap.keySet();
    }

    public OWLClass retrieveSuperClassFromNamedGCI(OWLClass gciName) {
        return nameAndSuperGCIConceptMap.get(gciName);
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

}
