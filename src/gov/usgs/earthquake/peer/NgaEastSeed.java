package gov.usgs.earthquake.peer;

import static gov.usgs.earthquake.nshmp.gmm.Imt.PGA;
import static gov.usgs.earthquake.nshmp.gmm.Imt.PGV;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P075;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P15;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P3;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P75;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA1P5;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA3P0;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA7P5;
import static gov.usgs.earthquake.nshmp.internal.Parsing.Delimiter.COMMA;

import gov.usgs.earthquake.nshmp.gmm.Imt;
import gov.usgs.earthquake.nshmp.internal.Parsing;

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
public class NgaEastSeed {

  static final String DIRBASE =
      "../../../Documents/NSHMP/GMPE/NGA-East/NGA-East_Final_Report_Rev0/seeds/D.4_Selected_Seed_Models/";

  static final Path SRC_DIR = Paths.get(DIRBASE, "csv");

  static final Path OUT_DIR = Paths.get(DIRBASE, "csv-combined");
  static final String OUT_FILEBASE = "nga-east-";

  static final String[] FILENAMES = { "1CCSP", "1CVSP", "2CCSP", "2CVSP", "ANC15", "B_a04", "B_ab14", "B_ab95", "B_bca10d", "B_bs11", "B_sgd02", "Frankel", "Graizer", "HA15", "PEER_EX", "PEER_GP", "PZCT15_M1SS", "PZCT15_M2ES", "SP15", "YA15" };
  
  static final List<Double> R =
      Doubles.asList(0, 1, 5, 10, 15, 20, 25, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140,
          150, 175, 200, 250, 300, 350, 400, 450, 500, 600, 700, 800, 1000, 1200, 1500);

  static final List<Double> M =
      Doubles.asList(4.0, 4.5, 5.0, 5.5, 6.0, 6.5, 7.0, 7.5, 7.8, 8.0, 8.2);

  public static void main(String[] args) throws IOException {
    for (String filename : FILENAMES) {
      combine(filename);
    }
  }

  static void combine(String id) throws IOException {

    Map<Imt, Table<Double, Double, Double>> μTables = Maps.newEnumMap(Imt.class);

    String glob = id + (id.equals("ANC15") ? "_as_is_" : "_adjusted_") + "*.csv";
    System.out.println(glob);
    DirectoryStream<Path> stream = Files.newDirectoryStream(SRC_DIR, glob);

    for (Path p : stream) {
      String fName = p.getFileName().toString();
      String freqStr = fName.substring(fName.lastIndexOf('_') + 1, fName.lastIndexOf('.'));
      Imt imt = toImt(freqStr);
      Table<Double, Double, Double> μTable = loadFile(p);
      μTables.put(imt, μTable);
    }

    writeTables(id, μTables);
  }

  static Table<Double, Double, Double> loadFile(Path csvFile) throws IOException {
    List<String> lines = Files.readAllLines(csvFile, StandardCharsets.US_ASCII);
    Table<Double, Double, Double> t = ArrayTable.create(R, M);
    for (String line : Iterables.skip(lines, 1)) {
      List<Double> v = Parsing.splitToDoubleList(line, COMMA);
      /* 
       * For whatever reason ANC15 has a greater discretization in both r and m;
       * might be what the '_as_is_' refers to. If row or column is missing, skip
       * populating table.
       */
      double r = v.get(1);
      double m = v.get(0);
      if (t.containsRow(r) && t.containsColumn(m)) {
        t.put(r, m, v.get(2));
      }
    }
    return t;
  }

  static void writeTables(String model, Map<Imt, Table<Double, Double, Double>> μTables)
      throws IOException {

    List<String> lines = new ArrayList<>();
    for (Imt imt : μTables.keySet()) {
      lines.addAll(toLines(imt, μTables.get(imt)));
    }
    Files.createDirectories(OUT_DIR);
    Path out = OUT_DIR.resolve(OUT_FILEBASE + model + ".dat");
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
