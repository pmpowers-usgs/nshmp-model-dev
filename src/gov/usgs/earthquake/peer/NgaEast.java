package gov.usgs.earthquake.peer;

import static org.opensha2.gmm.Imt.PGA;
import static org.opensha2.gmm.Imt.PGV;
import static org.opensha2.gmm.Imt.SA0P075;
import static org.opensha2.gmm.Imt.SA0P15;
import static org.opensha2.gmm.Imt.SA0P3;
import static org.opensha2.gmm.Imt.SA0P75;
import static org.opensha2.gmm.Imt.SA1P5;
import static org.opensha2.gmm.Imt.SA3P0;
import static org.opensha2.gmm.Imt.SA7P5;
import static org.opensha2.internal.Parsing.Delimiter.COMMA;

import org.opensha2.gmm.Imt;
import org.opensha2.internal.Parsing;

import com.google.common.collect.ArrayTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.primitives.Doubles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Combines individual *.csv tables for NGA-East models (1 file per model per
 * Imt) into 1 file per model.
 *
 * @author Peter Powers
 */
public class NgaEast {

  static final String DIRBASE =
      "../../../Documents/NSHMP/GMPE/NGA-East/NGA-East_Final_Report_Rev0/E.1.1_Final_Median_NGA-East_GMM_Tables/";

  static final Path SRC_DIR = Paths.get(DIRBASE, "csv");
  static final String SRC_FILEBASE = "NGA-East_Model_";

  static final Path OUT_DIR = Paths.get(DIRBASE, "csv-combined");
  static final String OUT_FILEBASE = "nga-east-";

  static final List<Double> R =
      Doubles.asList(0, 1, 5, 10, 15, 20, 25, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140,
          150, 175, 200, 250, 300, 350, 400, 450, 500, 600, 700, 800, 1000, 1200, 1500);

  static final List<Double> M =
      Doubles.asList(4.0, 4.5, 5.0, 5.5, 6.0, 6.5, 7.0, 7.5, 7.8, 8.0, 8.2);

  public static void main(String[] args) throws IOException {
    for (int i = 1; i <= 29; i++) {
      combine(i);
    }
  }

  static void combine(int model) throws IOException {

    Map<Imt, Table<Double, Double, Double>> μTables = Maps.newEnumMap(Imt.class);

    String id = ((model < 10) ? "0" : "") + model;
    String glob = SRC_FILEBASE + id + "_" + "*.csv";
    DirectoryStream<Path> stream = Files.newDirectoryStream(SRC_DIR, glob);

    for (Path p : stream) {
      String fName = p.getFileName().toString();
      String freqStr = fName.substring(fName.lastIndexOf('_') + 1, fName.lastIndexOf('.'));
      Imt imt = toImt(freqStr);
      Table<Double, Double, Double> μTable = loadFile(p);
      μTables.put(imt, μTable);
    }

    writeTables(model, μTables);
  }

  static Table<Double, Double, Double> loadFile(Path csvFile) throws IOException {
    List<String> lines = Files.readAllLines(csvFile, StandardCharsets.US_ASCII);
    Table<Double, Double, Double> t = ArrayTable.create(R, M);
    for (String line : Iterables.skip(lines, 1)) {
      List<Double> v = Parsing.splitToDoubleList(line, COMMA);
      t.put(v.get(1), v.get(0), v.get(2));
    }
    return t;
  }

  static void writeTables(int model, Map<Imt, Table<Double, Double, Double>> μTables)
      throws IOException {

    List<String> lines = new ArrayList<>();
    for (Imt imt : μTables.keySet()) {
      lines.addAll(toLines(imt, μTables.get(imt)));
    }
    Files.createDirectories(OUT_DIR);
    Path out = OUT_DIR.resolve(OUT_FILEBASE + model + ".csv");
    Files.write(out, lines, StandardCharsets.US_ASCII);
  }

  static List<String> toLines(Imt imt, Table<Double, Double, Double> t) {
    List<String> lines = new ArrayList<>();
    lines.add(imt.name());
    String header = "r\\m," + Parsing.join(t.columnKeySet(), COMMA);
    lines.add(header);
    for (Entry<Double, Map<Double, Double>> rEntry : t.rowMap().entrySet()) {
      double r = rEntry.getKey();
      String line = r + "," + Parsing.join(rEntry.getValue().values(), COMMA);
      lines.add(line);
    }
    return lines;
  }

  static Imt toImt(String fStr) {
    if (fStr.equals("PGA")) return PGA;
    if (fStr.equals("PGV")) return PGV;
    if (fStr.equals("F0.133")) return SA7P5;
    if (fStr.equals("F0.333")) return SA3P0;
    if (fStr.equals("F0.667")) return SA1P5;
    if (fStr.equals("F1.333")) return SA0P75;
    if (fStr.equals("F3.333")) return SA0P3;
    if (fStr.equals("F13.333")) return SA0P075;
    if (fStr.equals("F6.667")) return SA0P15;
    double period = 1.0 / Double.parseDouble(fStr.substring(1));
    return Imt.fromPeriod(period);
  }
}
