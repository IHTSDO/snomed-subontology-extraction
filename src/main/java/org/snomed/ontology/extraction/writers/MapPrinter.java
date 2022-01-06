package org.snomed.ontology.extraction.writers;

import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLRestriction;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class MapPrinter extends Printer {

	private final File outputDirectory;

	public MapPrinter(File outputDirectory) {
		this.outputDirectory = outputDirectory;
	}

	public void printGeneralMap(Map<?, ?> map, String mapName) throws IOException {
		File outputFile = new File(outputDirectory, mapName + ".txt");
		System.out.println("Printing map to: " + outputFile.getAbsolutePath());
		FileWriter fw = new FileWriter(outputFile);
		BufferedWriter writer =  new BufferedWriter(fw);

		StringBuilder sb = new StringBuilder();
		for (Map.Entry<?, ?> currentEntry : map.entrySet()) {
			if(currentEntry.getValue() instanceof Set) {
				@SuppressWarnings("unchecked")
				Set<OWLClass> equivSet = (Set<OWLClass>) currentEntry.getValue();
				equivSet.remove(currentEntry.getKey());
				if(equivSet.size() > 0) { //Equivalences set always includes the class itself.
					sb.append(currentEntry.getKey());
					sb.append("\t");
					sb.append(currentEntry.getValue());
					writer.write(sb.toString());
					newline(writer);
					sb.setLength(0);
					writer.flush();
				}
		   } else {
				sb.append(currentEntry.getKey());
				sb.append("\t");
				sb.append(currentEntry.getValue());
				writer.write(sb.toString());
				newline(writer);
				sb.setLength(0);
				writer.flush();
			}
		}
	}

	public void printNamingsForPVs(Map<OWLRestriction, OWLClass> pvNamingsMap) throws IOException {
		File outputFile = new File(outputDirectory, "pvNamingMap.txt");
		System.out.println("Printing map to: " + outputFile.getAbsolutePath());
		try (BufferedWriter writer =  new BufferedWriter(new FileWriter(outputFile))) {
			writer.write("PV Expression: \tName: ");
			writer.newLine();
			for (Map.Entry<OWLRestriction, OWLClass> entry : pvNamingsMap.entrySet()) {
				writer.write(entry.getKey().toString());
				writer.write("\t");
				writer.write(entry.getValue().toString());
				writer.newLine();
			}
		}
	}

}
