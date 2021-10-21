package org.snomed.ontology.extraction.writers;

import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class MapPrinter extends Printer {

    private static String outputDirectory;
    public MapPrinter(String outputPath) {
        if(!outputPath.substring(outputPath.length() - 1).equals("/")) {
            this.outputDirectory = outputPath + "/";
            return;
        }
        this.outputDirectory = outputPath;
    }

    public void printGeneralMap(Map map, String mapName) throws IOException {
        String outputFilePath = outputDirectory + mapName + ".txt";
        System.out.println("Printing map to: " + outputFilePath);
        FileWriter fw = new FileWriter(new File(outputFilePath));
        BufferedWriter writer =  new BufferedWriter(fw);

        Iterator<Map.Entry> iter = map.entrySet().iterator();
        StringBuilder sb = new StringBuilder();
        while(iter.hasNext()) {
            Map.Entry currentEntry = iter.next();
            if(currentEntry.getValue() instanceof Set) {
                Set<OWLClass> equivSet = (HashSet) currentEntry.getValue();
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
            continue;
           }
            else {
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

    public void printNamingsForPVs(Map<OWLObjectSomeValuesFrom, OWLClass> pvNamingsMap) throws IOException {
        String outputFilePath = outputDirectory + "pvNamingMap.txt";
        System.out.println("Printing map to: " + outputFilePath);
        FileWriter fw = new FileWriter(new File(outputFilePath));
        BufferedWriter writer =  new BufferedWriter(fw);

        Iterator<Map.Entry<OWLObjectSomeValuesFrom, OWLClass>> iter = pvNamingsMap.entrySet().iterator();

        StringBuilder sb = new StringBuilder();
        sb.append("PV Expression: " + "\t" + "Name: ");
        newline(writer);
        sb.setLength(0);
        writer.flush();
        while(iter.hasNext()) {
            Map.Entry currentEntry = iter.next();
            sb.append(currentEntry.getKey());
            sb.append("\t");
            sb.append(currentEntry.getValue());
            writer.write(sb.toString());
            newline(writer);
            sb.setLength(0);
            writer.flush();
        }
    }

    //public void printEquivalentClassesMap(Map map) throws IOException {
    //    this.printMap(map, "Equivalent classes");
    //}

    public String getDirectoryPath() {
        return outputDirectory;
    }

}
