package Classification;

import ExceptionHandlers.ReasonerException;
import org.semanticweb.elk.owl.interfaces.ElkObjectProperty;
import org.semanticweb.elk.owlapi.wrapper.OwlObjectPropertyAxiomConverterVisitor;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.*;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.util.InferredEquivalentClassAxiomGenerator;
import org.semanticweb.owlapi.util.InferredOntologyGenerator;
import org.semanticweb.owlapi.util.InferredSubClassAxiomGenerator;
import org.semanticweb.elk.owlapi.wrapper.OwlConverter;

import java.util.*;

public class OntologyReasoningService {

    private OWLReasoner reasoner;
    private final OWLReasonerConfiguration configuration = new SimpleConfiguration(new ConsoleProgressMonitor());
    //private OWLOntology inputOntology;

    //TODO 03-08-20: add all exceptions to one exception.
    //TODO " "     : check - return OWLOntology, or just precompute inferences and use this class to navigate graph?
    public OntologyReasoningService(OWLOntology inputOntology) throws ReasonerException {
        this(inputOntology, "org.semanticweb.elk.owlapi.ElkReasonerFactory");
    }
    public OntologyReasoningService(OWLOntology inputOntology, String reasonerFactoryName) throws ReasonerException {
        //this.inputOntology = inputOntology;
        reasoner = getReasonerFactory(reasonerFactoryName).createReasoner(inputOntology, configuration);
    }

    public void classifyOntology() {
        //03-09-20 QUESTION: return ontology, or just return precomputed inferences?
        System.out.println("Classifying ontology (precomputing hierarchy).");
        reasoner.flush();
        reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
    }

    public OWLOntology getClassifiedOntology() throws OWLOntologyCreationException {
        InferredOntologyGenerator classifiedOntologyGenerator = new InferredOntologyGenerator(reasoner, Arrays.asList(new InferredSubClassAxiomGenerator(),
                                                                                                                      new InferredEquivalentClassAxiomGenerator()));
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = man.getOWLDataFactory();

        OWLOntology classifiedOntology = man.createOntology();
        classifiedOntologyGenerator.fillOntology(df, classifiedOntology);

        return classifiedOntology;
    }

    private OWLReasonerFactory getReasonerFactory(String reasonerFactoryName) throws ReasonerException {
        Class<?> reasonerFactoryClass = null;
        try {
            reasonerFactoryClass = Class.forName(reasonerFactoryName);
            return (OWLReasonerFactory) reasonerFactoryClass.newInstance();
        } catch (ClassNotFoundException e) {
            throw new ReasonerException(String.format("Requested reasoner class '%s' not found.", reasonerFactoryName), e);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new ReasonerException(String.format("Requested reasoner class '%s' not found.", reasonerFactoryName), e);
        } catch (InstantiationException e) {
            e.printStackTrace();
            throw new ReasonerException("Reasoner instantiation exception.", e);
        }
    }

    public Map<OWLClass, Set<OWLClass>> getEquivalentClassesMap() {
        OWLOntology rootOntology = reasoner.getRootOntology();
        System.out.println("Computing equivalent classes map for ontology: " + rootOntology.getOntologyID().toString());
        Map<OWLClass, Set<OWLClass>> equivalentClassesMap = new HashMap<>();
        for(OWLClass cls:rootOntology.getClassesInSignature()) {
            equivalentClassesMap.computeIfAbsent(cls, s -> this.getEquivalentClassesForClass(s));
        }
        return equivalentClassesMap;
    }

    public Set<OWLClass> getEquivalentClassesForClass(OWLClass cls) {
        Node<OWLClass> clsNode = reasoner.getEquivalentClasses(cls);
        return clsNode.getEntities();
    }

    public Set<OWLClass> getAncestorClasses(OWLClass cls) {
        //System.out.println("RES: " + reasoner.getSuperClasses(cls, false));
        return reasoner.getSuperClasses(cls, false).getFlattened();
    }

    public Set<OWLClass> getParentClasses(OWLClass cls) {
        return reasoner.getSuperClasses(cls, true).getFlattened();
    }

    public Set<OWLClass> getChildClasses(OWLClass cls) {
        return reasoner.getSubClasses(cls, true).getFlattened();
    }

    public Set<OWLClass> getDescendentClasses(OWLClass cls) {
        return reasoner.getSubClasses(cls, false).getFlattened();
    }

    public OWLClass getTopClassForHierarchy() {
        ArrayList<OWLEntity> topEntities = new ArrayList<OWLEntity>(reasoner.getTopClassNode().getEntities());

        //TODO: does it matter which one we use, if equivalent? Add functionality if so.
        OWLClass topClass = topEntities.get(0).asOWLClass();
        return topClass;
    }

    public Set<OWLClass> reduceClassSet(Set<OWLClass> inputClassSet) {
        Set<OWLClass> redundantClasses = new HashSet<OWLClass>();

        Set<OWLClass> otherClasses = new HashSet<OWLClass>(inputClassSet);
        for (OWLClass cls : inputClassSet) {
            otherClasses.remove(cls);
            if (weakerThanAtLeastOneOf(cls, otherClasses)) {
                redundantClasses.add(cls);
            }
            otherClasses.add(cls); //retain redundancies to check against (?)
            // TODO: check, would be problematic if we have equivalent named classes or PVs, since this will mean both are removed. Is this ever the case with SCT?
        }   // TODO:...but if A |= B, then we have B |= C, via this approach we can safely remove them as we identify them? DOUBLE CHECK.

        inputClassSet.removeAll(redundantClasses);
        //inputClassSet.remove(df.getOWLThing());
        //inputClassSet.remove(df.getOWLNothing());
        return (inputClassSet); //TODO: return as list or set?
    }

    //public Set<OWLObjectPropertyExpression> getAncestorProperties(OWLObjectProperty r) {
        //TODO: for some reason this is "not implemented" in ELK? Doesn't ELK return a property graph also?
        //return reasoner.getSuperObjectProperties(prop, true).getFlattened();
   // }

    public boolean isStrongerThan(OWLClass classBeingChecked, OWLClass classCheckedAgainst) {
        if(this.getAncestorClasses(classBeingChecked).contains(classCheckedAgainst)) {
            return true;
        }
        return false;
    }

    public boolean weakerThanAtLeastOneOf(OWLClass classBeingChecked, Set<OWLClass> setCheckedAgainst) {
        for(OWLClass classCheckedAgainst:setCheckedAgainst) {
            //System.out.println("Class being checked: " + classBeingChecked);
           // System.out.println("Class checked against: " + classCheckedAgainst);
            if(this.getAncestorClasses(classCheckedAgainst).contains(classBeingChecked)) {
               // System.out.println("Class: " + classCheckedAgainst + " stronger than: " + classBeingChecked);
                return true;
            }
        }
        //if(!Collections.disjoint(this.getAncestorClasses(classBeingChecked), setCheckedAgainst)) {
        //    return true;
        //}
        return false;
    }

    public boolean isConsistent() {
        return reasoner.isConsistent();
    }

}
