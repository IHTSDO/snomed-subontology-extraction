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
        return reasoner.getSuperClasses(cls, false).getFlattened();
    }

    public Set<OWLClass> getParentClasses(OWLClass cls) {
        return reasoner.getSuperClasses(cls, true).getFlattened();
    }

    public Set<OWLClass> getDirectSubClasses(OWLClass cls) {
        return reasoner.getSubClasses(cls, true).getFlattened();
    }

    public Set<OWLClass> getDescendantClasses(OWLClass cls) {
        return reasoner.getSubClasses(cls, false).getFlattened();
    }

    public Set<OWLClass> getDescendantClasses(OWLClass cls, boolean getSelf) {
        return reasoner.getSubClasses(cls, getSelf).getFlattened();
    }

    public OWLClass getTopClassForHierarchy() {
        ArrayList<OWLEntity> topEntities = new ArrayList<OWLEntity>(reasoner.getTopClassNode().getEntities());

        //TODO: does it matter which one we use, if equivalent? Add functionality if so.
        OWLClass topClass = topEntities.get(0).asOWLClass();
        return topClass;
    }

    public Set<OWLClass> eliminateWeakerClasses(Set<OWLClass> inputClassSet) {
        Set<OWLClass> redundantClasses = new HashSet<OWLClass>();

        Set<OWLClass> otherClasses = new HashSet<OWLClass>(inputClassSet);
        for (OWLClass cls : inputClassSet) {
            otherClasses.remove(cls);
            if (weakerThanAtLeastOneOf(cls, otherClasses)) {
                redundantClasses.add(cls);
            }
            otherClasses.add(cls); //retain redundancies to check against (?)
            // TODO: check, would be problematic if we have equivalent named classes or PVs, since this will mean both are removed. Is this ever the case with SCT?
        }   // TODO:...but if A |= B, then we have B |= C, via this approach we can safely remove them as we identify them? Check.

        inputClassSet.removeAll(redundantClasses);
        return (inputClassSet);
    }

    public Set<OWLClass> eliminateStrongerClasses(Set<OWLClass> inputClassSet) {
        Set<OWLClass> redundantClasses = new HashSet<OWLClass>();

        Set<OWLClass> otherClasses = new HashSet<OWLClass>(inputClassSet);
        for (OWLClass cls : inputClassSet) {
            otherClasses.remove(cls);
            if (strongerThanAtLeastOneOf(cls, otherClasses)) {
                redundantClasses.add(cls);
            }
            otherClasses.add(cls); //retain redundancies to check against (?)
            // TODO: check, would be problematic if we have equivalent named classes or PVs, since this will mean both are removed. Is this ever the case with SCT?
        }   // TODO:...but if A |= B, then we have B |= C, via this approach we can safely remove them as we identify them? Check.

        inputClassSet.removeAll(redundantClasses);
        return (inputClassSet);
    }

    public boolean isPrimitive(OWLClass cls) {
        //TODO: for full SCT, could do this using fullyDefined IDs as in toolkit? Quicker?
        if(reasoner.getRootOntology().getEquivalentClassesAxioms(cls).isEmpty()) {
            return true;
        }
        return false;
    }

    public boolean isStrongerThan(OWLClass classBeingChecked, OWLClass classCheckedAgainst) {
        if(this.getAncestorClasses(classBeingChecked).contains(classCheckedAgainst)) {
            return true;
        }
        return false;
    }

    public boolean weakerThanAtLeastOneOf(OWLClass classBeingChecked, Set<OWLClass> setCheckedAgainst) {
        for(OWLClass classCheckedAgainst:setCheckedAgainst) {
            if(this.getAncestorClasses(classCheckedAgainst).contains(classBeingChecked)) {
                return true;
            }
        }
        return false;
    }
    public boolean strongerThanAtLeastOneOf(OWLClass classBeingChecked, Set<OWLClass> setCheckedAgainst) {
        for(OWLClass classCheckedAgainst:setCheckedAgainst) {
            if(this.getDescendantClasses(classCheckedAgainst).contains(classBeingChecked)) {
                return true;
            }
        }
        return false;
    }

    //public Set<OWLObjectPropertyExpression> getAncestorProperties(OWLObjectProperty r) {
    //"not implemented" in ELK
    //return reasoner.getSuperObjectProperties(prop, true).getFlattened();
    // }

    public boolean isConsistent() {
        return reasoner.isConsistent();
    }

    public boolean entails(OWLAxiom ax) { return reasoner.isEntailed(ax);}

}
