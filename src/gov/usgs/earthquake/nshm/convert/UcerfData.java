package gov.usgs.earthquake.nshm.convert;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.opensha2.internal.Parsing;
import org.opensha2.internal.Parsing.Delimiter;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;

/**
 * UCERF3 parent fault mfd fetcher and converter
 *
 * @author Peter Powers
 */
public class UcerfData {

  private static final Path SRC_DIR = Paths.get("tmp", "UC3", "src");
  private static final Path OUT_DIR = Paths.get("tmp", "UC3", "out");

  static void process() throws IOException {
    Files.walkFileTree(SRC_DIR, new MfdVisitor());
  }

  static class MfdVisitor extends SimpleFileVisitor<Path> {

    // PathMatcher matcher =
    // FileSystems.getDefault().getPathMatcher("glob:*Lake_nucleation.txt");
    PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:*.txt");

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
        throws IOException {
      String dirName = dir.getFileName().toString();
      if (dirName.equals("src")) return FileVisitResult.CONTINUE;
      if (dirName.contains("MFDs")) {
        Files.createDirectories(OUT_DIR.resolve("mfds"));
        return FileVisitResult.CONTINUE;
      }
      return FileVisitResult.SKIP_SUBTREE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
        throws IOException {
      if (matcher.matches(file.getFileName())) {
        Map<String, List<Double>> ratesMap = readMfds(file);
        Path out = OUT_DIR.resolve("mfds").resolve(createMfdFileName(file));
        writeMfds(out, ratesMap);
      }
      return FileVisitResult.CONTINUE;
    }

  }

  static final Map<Integer, String> MFD_ID_MAP;
  static final Map<Integer, Integer> MAG_COUNT_MAP;
  static final List<Double> MAGS = Doubles.asList(new double[] {
      5.05, 5.15, 5.25, 5.35, 5.45, 5.55, 5.65, 5.75, 5.85, 5.95,
      6.05, 6.15, 6.25, 6.35, 6.45, 6.55, 6.65, 6.75, 6.85, 6.95,
      7.05, 7.15, 7.25, 7.35, 7.45, 7.55, 7.65, 7.75, 7.85, 7.95,
      8.05, 8.15, 8.25, 8.35, 8.45, 8.55 });

  static {
    // iteration order = insertion order
    Builder<Integer, String> idMapBuilder = ImmutableMap.builder();
    idMapBuilder.put(1, "UC3-SupraSeisMean");
    idMapBuilder.put(2, "UC3-SupraSeisMin");
    idMapBuilder.put(3, "UC3-SupraSeisMax");
    idMapBuilder.put(4, "UC3-SubSeisMean");
    idMapBuilder.put(5, "UC3-SubSeisMin");
    idMapBuilder.put(6, "UC3-SubSeisMax");
    idMapBuilder.put(10, "UC2-SupraSeisMean");
    idMapBuilder.put(11, "UC2-SupraSeisMin");
    idMapBuilder.put(12, "UC2-SupraSeisMax");
    MFD_ID_MAP = idMapBuilder.build();

    Builder<Integer, Integer> countMapBuilder = ImmutableMap.builder();
    countMapBuilder.put(1, 41);
    countMapBuilder.put(2, 41);
    countMapBuilder.put(3, 41);
    countMapBuilder.put(4, 90);
    countMapBuilder.put(5, 90);
    countMapBuilder.put(6, 90);
    countMapBuilder.put(7, 90);
    countMapBuilder.put(8, 90);
    countMapBuilder.put(9, 90);
    countMapBuilder.put(10, 40);
    countMapBuilder.put(11, 40);
    countMapBuilder.put(12, 40);
    MAG_COUNT_MAP = countMapBuilder.build();
  }

  private static final class MFDLineFilter implements Predicate<String> {
    @Override
    public boolean apply(String line) {
      if (line.isEmpty()) return false;
      if (line.startsWith("     Name:")) return false;
      if (line.startsWith("   Points:")) return false;
      if (line.startsWith("     Info:")) return false;
      if (line.startsWith("Data[")) return false;
      if (line.startsWith("X-Axis:")) return false;
      if (line.startsWith("Y-Axis:")) return false;
      if (line.startsWith("Number of")) return false;
      return true;
    }
  }

  static final Map<String, List<Double>> readMfds(Path mfdIn) throws IOException {
    List<String> lines = Files.readAllLines(mfdIn, StandardCharsets.US_ASCII);
    Iterator<String> it = Iterators.filter(lines.iterator(), new MFDLineFilter());

    Builder<String, List<Double>> ratesMap = ImmutableMap.builder();

    while (it.hasNext()) {
      String dataSetIdLine = it.next();
      int dataSetIdIndex = dataSetIdLine.indexOf('#') + 1;
      int id = Integer.valueOf(dataSetIdLine.substring(dataSetIdIndex).trim());

      int mfdSize = MAG_COUNT_MAP.get(id);
      if (MFD_ID_MAP.containsKey(id)) {
        List<Double> rates = new ArrayList<>();
        for (int i = 0; i < mfdSize; i++) {
          String mfdLine = it.next();
          List<Double> xy = Parsing.splitToDoubleList(mfdLine, Delimiter.SPACE);
          double mag = xy.get(0);
          if (mag < 5.05 || mag > 8.55) continue;
          rates.add(xy.get(1));
        }
        ratesMap.put(MFD_ID_MAP.get(id), rates);
      } else {
        Iterators.advance(it, mfdSize);
      }
    }
    return ratesMap.build();
  }

  // TODO this is going to need to come from a lookup table once
  // Jarry/Kathy have established IDs for UCERF3 parent fault sections
  static Map<String, Integer> faultIdMap = new HashMap<>();

  static final Path createMfdFileName(Path in) {
    String nameIn = in.getFileName().toString();

    int typeStart = nameIn.lastIndexOf('_');
    String type = nameIn.substring(typeStart + 1, typeStart + 5);

    String name = nameIn.substring(0, typeStart).replace('_', ' ').trim();
    if (!faultIdMap.containsKey(name)) {
      faultIdMap.put(name, 1000 + faultIdMap.size());
    }
    int index = faultIdMap.get(name);
    String fullName = index + "-" + name + "-" + type + ".csv";
    return Paths.get(fullName);
  }

  static final void writeMfds(Path mfdOut, Map<String, List<Double>> rates) throws IOException {
    List<String> lines = new ArrayList<>();
    String line = "magnitude," + Parsing.join(MAGS, Delimiter.COMMA);
    lines.add(line);
    for (Entry<String, List<Double>> entry : rates.entrySet()) {
      line = entry.getKey() + "," + Parsing.join(entry.getValue(), Delimiter.COMMA);
      lines.add(line);
    }
    Files.write(mfdOut, lines, StandardCharsets.US_ASCII);
  }

  // TODO rename maps for use as additional resource in unified hazard tool
  static class MapVisitor extends SimpleFileVisitor<Path> {

    PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:*.pdf");

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
        throws IOException {
      String dirName = dir.getFileName().toString();
      if (dirName.equals("src")) return FileVisitResult.CONTINUE;
      if (dirName.contains("Maps")) {
        Files.createDirectories(OUT_DIR.resolve("maps"));
        return FileVisitResult.CONTINUE;
      }
      return FileVisitResult.SKIP_SUBTREE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
        throws IOException {
      if (matcher.matches(file)) System.out.println(file);
      return FileVisitResult.CONTINUE;
    }

  }

  public static void main(String[] args) throws IOException {
    process();
  }
}
