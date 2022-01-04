package org.snomed.ontology.extraction.writers;

import com.google.common.collect.Sets;
import com.google.common.collect.UnmodifiableIterator;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.semanticweb.elk.util.collections.Pair;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLException;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;

//used from content-extraction branch of SNOMED OWL toolkit
public class OWLtoRF2Service {
	public static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");
	private Map<Long, String> conceptDescriptionsFSN;
	private Map<Long, String> conceptDescriptionsPreferredSynonym;
	private Map<Long, Set<OWLAxiom>> conceptAxioms;
	private Set<Long> definedConcepts;

	public OWLtoRF2Service() {
	}

	public void writeToRF2(InputStream owlFileStream, OutputStream rf2ZipOutputStream, Date fileDate) throws OWLException, IOException {
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		OWLOntology owlOntology = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(owlFileStream);
		this.conceptDescriptionsFSN = new HashMap<>();
		this.conceptDescriptionsPreferredSynonym = new HashMap<>();
		this.conceptAxioms = new HashMap<>();
		this.definedConcepts = new HashSet<>();
		Iterator<? extends OWLEntity> var6 = Sets.union(owlOntology.getObjectPropertiesInSignature(), owlOntology.getClassesInSignature()).iterator();

		while (var6.hasNext()) {
			OWLEntity owlEntity = var6.next();
			IRI iri = owlEntity.getIRI();
			Long conceptId = this.getConceptIdFromUri(iri.toString());
			this.getDescriptions(conceptId, iri, owlOntology);
		}

		OWLDataFactory df = OWLManager.createOWLOntologyManager().getOWLDataFactory();
		OWLObjectProperty r = (OWLObjectProperty)(new ArrayList<>(owlOntology.getObjectPropertiesInSignature())).get(0);
		String iriString = r.getIRI().toString();
		OWLObjectProperty topProp = df.getOWLObjectProperty(IRI.create(iriString.substring(0, iriString.lastIndexOf("/") + 1) + "762705008"));
		Iterator var10 = owlOntology.getObjectPropertiesInSignature().iterator();

		while(var10.hasNext()) {
			OWLObjectProperty prop = (OWLObjectProperty)var10.next();
			if (!prop.toString().contains("762705008")) {
				OWLAxiom ax = df.getOWLSubObjectPropertyOfAxiom(prop, topProp);
				man.addAxiom(owlOntology, ax);
			}
		}

		var10 = owlOntology.getAxioms().iterator();

		while(var10.hasNext()) {
			OWLAxiom axiom = (OWLAxiom)var10.next();
			if (axiom instanceof OWLObjectPropertyAxiom) {
				OWLObjectProperty namedConcept = (OWLObjectProperty)axiom.getObjectPropertiesInSignature().iterator().next();
				this.addAxiom(this.getConceptIdFromUri(namedConcept.getNamedProperty().getIRI().toString()), axiom);
			} else if (axiom instanceof OWLSubClassOfAxiom) {
				OWLSubClassOfAxiom subClassOfAxiom = (OWLSubClassOfAxiom)axiom;
				OWLClassExpression subClass = subClassOfAxiom.getSubClass();
				if (subClass.isAnonymous()) {
					OWLClassExpression superClass = subClassOfAxiom.getSuperClass();
					this.addAxiom(this.getFirstConceptIdFromClassList(superClass.getClassesInSignature()), axiom);
				} else {
					this.addAxiom(this.getFirstConceptIdFromClassList(subClass.getClassesInSignature()), axiom);
				}
			} else if (axiom instanceof OWLEquivalentClassesAxiom) {
				OWLEquivalentClassesAxiom equivalentClassesAxiom = (OWLEquivalentClassesAxiom)axiom;
				Set<OWLClassExpression> classExpressions = equivalentClassesAxiom.getClassExpressions();
				OWLClass next = (OWLClass)classExpressions.iterator().next();
				long conceptId = this.getConceptIdFromUri(next.getIRI().toString());
				this.addAxiom(conceptId, axiom);
				this.definedConcepts.add(conceptId);
			}
		}

		String date = SIMPLE_DATE_FORMAT.format(fileDate);
		ZipOutputStream zipOutputStream = new ZipOutputStream(rf2ZipOutputStream);

		try {
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zipOutputStream));

			try {
				zipOutputStream.putNextEntry(new ZipEntry(String.format("SnomedCT/Snapshot/Terminology/sct2_Concept_Snapshot_INT_%s.txt", date)));
				writer.write("id\teffectiveTime\tactive\tmoduleId\tdefinitionStatusId");
				this.newline(writer);
				Iterator var38 = this.conceptAxioms.keySet().iterator();

				while(var38.hasNext()) {
					Long conceptId = (Long)var38.next();
					String definitionStatus = this.definedConcepts.contains(conceptId) ? "900000000000073002" : "900000000000074008";
					writer.write(String.join("\t", conceptId.toString(), "0", "1", "900000000000207008", definitionStatus));
					this.newline(writer);
				}

				writer.write(String.join("\t", "138875005", "0", "1", "900000000000207008", this.definedConcepts.contains(138875005L) ? "900000000000073002" : "900000000000074008"));
				this.newline(writer);
				writer.write(String.join("\t", "900000000000441003", "0", "1", "900000000000207008", this.definedConcepts.contains(900000000000441003L) ? "900000000000073002" : "900000000000074008"));
				this.newline(writer);
				writer.write(String.join("\t", "410662002", "0", "1", "900000000000207008", this.definedConcepts.contains(410662002L) ? "900000000000073002" : "900000000000074008"));
				this.newline(writer);
				writer.write(String.join("\t", "762705008", "0", "1", "900000000000207008", this.definedConcepts.contains(762705008L) ? "900000000000073002" : "900000000000074008"));
				this.newline(writer);
				writer.flush();
				zipOutputStream.putNextEntry(new ZipEntry(String.format("SnomedCT/Snapshot/Terminology/sct2_Description_Snapshot-en_INT_%s.txt", date)));
				writer.write("id\teffectiveTime\tactive\tmoduleId\tconceptId\tlanguageCode\ttypeId\tterm\tcaseSignificanceId");
				this.newline(writer);
				int dummySequence = 100000000;
				Map<Pair<Long, String>, String> conceptTermId = new HashMap();
				Iterator var44 = this.conceptDescriptionsFSN.keySet().iterator();

				String descriptionId;
				while(var44.hasNext()) {
					Long conceptId = (Long)var44.next();
					descriptionId = String.format("%s011", dummySequence++);
					descriptionId = (String)this.conceptDescriptionsFSN.get(conceptId);
					conceptTermId.put(new Pair(conceptId, descriptionId), descriptionId);
					writer.write(String.join("\t", descriptionId, "0", "1", "900000000000207008", conceptId.toString(), "en", "900000000000003001", descriptionId, "900000000000448009"));
					this.newline(writer);
				}

				Map<Pair<Long, String>, String> conceptPreferredSynonymTermId = new HashMap();
				Iterator var46 = this.conceptDescriptionsPreferredSynonym.keySet().iterator();

				String term;
				while(var46.hasNext()) {
					Long conceptId = (Long)var46.next();
					descriptionId = String.format("%s011", dummySequence++);
					term = (String)this.conceptDescriptionsPreferredSynonym.get(conceptId);
					conceptPreferredSynonymTermId.put(new Pair(conceptId, term), descriptionId);
					writer.write(String.join("\t", descriptionId, "0", "1", "900000000000207008", conceptId.toString(), "en", "900000000000013009", term, "900000000000448009"));
					this.newline(writer);
				}

				writer.write(String.join("\t", "517382016", "0", "1", "900000000000207008", "138875005", "en", "900000000000073002", "SNOMED CT Concept (SNOMED RT+CTV3)", "900000000000448009"));
				this.newline(writer);
				writer.write(String.join("\t", "900000000000952015", "0", "1", "900000000000207008", "900000000000441003", "en", "900000000000073002", "SNOMED CT Model Component (metadata)", "900000000000017005"));
				this.newline(writer);
				writer.write(String.join("\t", "2466114012", "0", "1", "900000000000207008", "410662002", "en", "900000000000073002", "Concept model attribute (attribute)", "900000000000448009"));
				this.newline(writer);
				writer.write(String.join("\t", "3635487013", "0", "1", "900000000000207008", "762705008", "en", "900000000000073002", "Concept model object attribute (attribute)", "900000000000448009"));
				this.newline(writer);
				writer.write(String.join("\t", "3635487013", "0", "1", "900000000000207008", "762705008", "en", "900000000000073002", "Concept model object attribute", "900000000000448009"));
				this.newline(writer);
				writer.write(String.join("\t", "680946014", "0", "1", "900000000000207008", "116680003", "en", "900000000000073002", "Is a (attribute)", "900000000000448009"));
				this.newline(writer);
				writer.flush();
				zipOutputStream.putNextEntry(new ZipEntry(String.format("SnomedCT/Snapshot/Terminology/sct2_TextDefinition_Snapshot-en_INT_%s.txt", date)));
				writer.write("id\teffectiveTime\tactive\tmoduleId\tconceptId\tlanguageCode\ttypeId\tterm\tcaseSignificanceId");
				this.newline(writer);
				int dummySequence2 = 100000000;
				Iterator var49 = this.conceptDescriptionsFSN.keySet().iterator();

				label120:
				while(true) {
					//String descriptionId;
					Long conceptId;
					if (!var49.hasNext()) {
						writer.flush();
						zipOutputStream.putNextEntry(new ZipEntry(String.format("SnomedCT/Snapshot/Refset/Language/der2_cRefset_LanguageSnapshot-en_INT_%s.txt", date)));
						writer.write("id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\tacceptabilityId");
						this.newline(writer);
						var49 = this.conceptDescriptionsFSN.keySet().iterator();

						while(var49.hasNext()) {
							conceptId = (Long)var49.next();
							term = (String)this.conceptDescriptionsFSN.get(conceptId);
							descriptionId = (String)conceptTermId.get(new Pair(conceptId, term));
							writer.write(String.join("\t", UUID.randomUUID().toString(), "0", "1", "900000000000207008", "900000000000509007", descriptionId, "900000000000548007"));
							this.newline(writer);
						}

						writer.flush();
						zipOutputStream.putNextEntry(new ZipEntry(String.format("SnomedCT/Snapshot/Terminology/sct2_sRefset_OWLExpressionSnapshot_INT_%s.txt", date)));
						writer.write("id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\towlExpression");
						this.newline(writer);
						var49 = this.conceptAxioms.keySet().iterator();

						label110:
						while(true) {
							if (var49.hasNext()) {
								conceptId = (Long)var49.next();
								Iterator var51 = ((Set)this.conceptAxioms.get(conceptId)).iterator();

								while(true) {
									if (!var51.hasNext()) {
										continue label110;
									}

									OWLAxiom owlAxiom = (OWLAxiom)var51.next();
									String axiomString = owlAxiom.toString();
									axiomString = axiomString.replace("<http://snomed.info/id/", ":");
									axiomString = axiomString.replace(">", "");
									writer.write(String.join("\t", UUID.randomUUID().toString(), "0", "1", "900000000000207008", "733073007", conceptId.toString(), axiomString));
									this.newline(writer);
								}
							}

							writer.write(String.join("\t", UUID.randomUUID().toString(), "0", "1", "900000000000207008", "733073007", "762705008", "SubClassOf(:762705008 :410662002)"));
							this.newline(writer);
							writer.write(String.join("\t", UUID.randomUUID().toString(), "0", "1", "900000000000207008", "733073007", "410662002", "SubClassOf(:410662002 :900000000000441003)"));
							this.newline(writer);
							writer.write(String.join("\t", UUID.randomUUID().toString(), "0", "1", "900000000000207008", "733073007", "900000000000441003", "SubClassOf(:900000000000441003 :138875005)"));
							this.newline(writer);
							writer.flush();
							zipOutputStream.putNextEntry(new ZipEntry(String.format("SnomedCT/Snapshot/Terminology/sct2_Relationship_Snapshot_INT_%s.txt", date)));
							writer.write("id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\towlExpression");
							this.newline(writer);
							writer.flush();
							break label120;
						}
					}

					conceptId = (Long)var49.next();
					term = String.format("%s011", dummySequence2++);
					descriptionId = (String)this.conceptDescriptionsFSN.get(conceptId);
					conceptTermId.put(new Pair(conceptId, descriptionId), term);
					writer.write(String.join("\t", term, "0", "1", "900000000000207008", conceptId.toString(), "en", "900000000000003001", descriptionId, "900000000000448009"));
					this.newline(writer);
				}
			} catch (Throwable var24) {
				try {
					writer.close();
				} catch (Throwable var23) {
					var24.addSuppressed(var23);
				}

				throw var24;
			}

			writer.close();
		} catch (Throwable var25) {
			try {
				zipOutputStream.close();
			} catch (Throwable var22) {
				var25.addSuppressed(var22);
			}

			throw var25;
		}

		zipOutputStream.close();
	}

	private void newline(BufferedWriter writer) throws IOException {
		writer.write("\r\n");
	}

	private void getDescriptions(Long conceptId, IRI iri, OWLOntology owlOntology) {
		Set<OWLAnnotationAssertionAxiom> annotationAssertionAxioms = owlOntology.getAnnotationAssertionAxioms(iri);
		Iterator var5 = annotationAssertionAxioms.iterator();

		while(var5.hasNext()) {
			OWLAnnotationAssertionAxiom annotationAssertionAxiom = (OWLAnnotationAssertionAxiom)var5.next();
			String value;
			if ("rdfs:label".equals(annotationAssertionAxiom.getProperty().toString())) {
				value = annotationAssertionAxiom.getValue().toString();
				if (value.startsWith("\"")) {
					value = value.substring(1, value.lastIndexOf("\""));
				}

				this.conceptDescriptionsFSN.put(conceptId, value);
			} else if ("skos:prefLabel".equals(annotationAssertionAxiom.getProperty().toString())) {
				value = annotationAssertionAxiom.getValue().toString();
				if (value.startsWith("\"")) {
					value = value.substring(1, value.lastIndexOf("\""));
				}

				this.conceptDescriptionsPreferredSynonym.put(conceptId, value);
			}
		}

	}

	private Long getFirstConceptIdFromClassList(Set<OWLClass> classesInSignature) {
		return this.getConceptIdFromUri(((OWLClass)classesInSignature.iterator().next()).getIRI().toString());
	}

	private long getConceptIdFromUri(String uri) {
		return Long.parseLong(uri.substring(uri.lastIndexOf("/") + 1));
	}

	private boolean addAxiom(Long conceptId, OWLAxiom axiom) {
		return ((Set)this.conceptAxioms.computeIfAbsent(conceptId, (id) -> {
			return new HashSet();
		})).add(axiom);
	}

	private boolean addAxiom(Long conceptId, Collection<? extends OWLAxiom> axioms) {
		return ((Set)this.conceptAxioms.computeIfAbsent(conceptId, (id) -> {
			return new HashSet();
		})).addAll(axioms);
	}
}
