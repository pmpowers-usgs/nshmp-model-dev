package gov.usgs.earthquake.nshm.ak;

import gov.usgs.earthquake.nshmp.util.Maths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/*
 * Conversion of large grid files (back) to negative longitude values
 */
class GridLonConversion {

  static final Path FILEBASE = Paths.get("models/AK2007/GridLonConversion/");
  static final Path IN_DIR = FILEBASE.resolve("in");
  static final Path OUT_DIR = FILEBASE.resolve("out");

  public static void main(String[] args) throws IOException {
    convertLons("50 to 80 km.xml");
    convertLons("80 to 120 km.xml");
    convertLons("Crustal.xml");
  }

  static void convertLons(String filename) throws IOException {

    Path inPath = IN_DIR.resolve(filename);
    List<String> linesIn = Files.readAllLines(inPath, StandardCharsets.UTF_8);
    List<String> linesOut = new ArrayList<>(linesIn.size());

    for (String lineIn : linesIn) {
      if (lineIn.startsWith("    <Node")) {
        int start = lineIn.indexOf('>') + 1;
        int end = lineIn.indexOf(',');
        double oldLon = Double.parseDouble(lineIn.substring(start, end));
        double newLon = Maths.round(oldLon - 360.0, 1);
        String newLine = lineIn.substring(0, start) + newLon + lineIn.substring(end);
        linesOut.add(newLine);
      } else {
        linesOut.add(lineIn);
      }
    }

    Path outPath = OUT_DIR.resolve(filename);
    Files.write(outPath, linesOut, StandardCharsets.UTF_8);
  }
}
