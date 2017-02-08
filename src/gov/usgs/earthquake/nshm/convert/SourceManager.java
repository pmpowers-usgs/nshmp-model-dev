package gov.usgs.earthquake.nshm.convert;

import static gov.usgs.earthquake.nshm.util.SourceRegion.CA;
import static gov.usgs.earthquake.nshm.util.SourceRegion.CASC;
import static gov.usgs.earthquake.nshm.util.SourceRegion.WUS;
import static org.opensha2.eq.model.SourceType.CLUSTER;
import static org.opensha2.eq.model.SourceType.FAULT;
import static org.opensha2.eq.model.SourceType.GRID;
import static org.opensha2.eq.model.SourceType.INTERFACE;
import static org.opensha2.eq.model.SourceType.SLAB;
import gov.usgs.earthquake.nshm.util.SourceRegion;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.opensha2.eq.model.SourceType;
import org.opensha2.internal.Parsing;
import org.opensha2.internal.Parsing.Delimiter;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

/*
 * NSHMP source file manager
 * 
 * @author Peter Powers
 */
abstract class SourceManager {

  protected List<SourceFile> files;

  // no instantiation - use subclasses that have populated the file list
  SourceManager() {
    files = Lists.newArrayList();
    init();
  }

  /* Returns an immutable list of all NSHHMP source files. */
  List<SourceFile> getAll() {
    return ImmutableList.copyOf(files);
  }

  /*
   * Returns an immutable list of the source files that match the supplied
   * SourceRegion and SourceType. If either argument is null or empty, method
   * will match all elements of the argument class.
   */
  List<SourceFile> get(SourceRegion region, SourceType type) {
    return get(region, type, null);
  }

  /*
   * Returns an immutable list of the source files that match the supplied
   * SourceRegion, SourceType, and file name. If either argument is null, method
   * will match all elements of the argument class.
   */
  List<SourceFile> get(SourceRegion region, SourceType type,
      String name) {
    return get((region != null) ? EnumSet.of(region) : null, (type != null)
        ? EnumSet.of(type) : null, (name != null && !name.trim().isEmpty())
            ? ImmutableSet.of(name) : null);
  }

  /*
   * Returns an immutable list of the source files that match the supplied
   * SourceRegions, SourceTypes, and file names. If any argument is null or
   * empty, method will match all elements of the argument class.
   */
  List<SourceFile> get(EnumSet<SourceRegion> regions,
      EnumSet<SourceType> types, Set<String> names) {
    List<Predicate<SourceFile>> pList = generateFilters(regions, types,
        names);
    Predicate<SourceFile> p = Predicates.and(pList);
    return ImmutableList.copyOf(Collections2.filter(files, p));
  }

  private static List<Predicate<SourceFile>> generateFilters(
      EnumSet<SourceRegion> regions, EnumSet<SourceType> types,
      Set<String> names) {
    Predicate<SourceFile> rp = Predicates.alwaysTrue();
    if (regions != null && !regions.isEmpty()) {
      rp = Predicates.alwaysFalse();
      for (SourceRegion region : regions) {
        rp = Predicates.or(rp, new SourceFileRegion(region));
      }
    }
    Predicate<SourceFile> tp = Predicates.alwaysTrue();
    if (types != null && !types.isEmpty()) {
      tp = Predicates.alwaysFalse();
      for (SourceType type : types) {
        tp = Predicates.or(tp, new SourceFileType(type));
      }
    }
    Predicate<SourceFile> np = Predicates.alwaysTrue();
    if (names != null && !names.isEmpty()) {
      np = Predicates.alwaysFalse();
      for (String name : names) {
        np = Predicates.or(np, new SourceFileName(name));
      }
    }
    return ImmutableList.of(rp, tp, np);
  }

  // @formatter:off

	private static class SourceFileType implements Predicate<SourceFile> {
		SourceType type;
		SourceFileType(SourceType type) { this.type = type; }
		@Override public boolean apply(SourceFile input) {
			return input.type.equals(type);
		}
		@Override public String toString() { return type.toString(); }
	}
	
	private static class SourceFileRegion implements Predicate<SourceFile> {
		SourceRegion region;
		SourceFileRegion(SourceRegion region) { this.region = region; }
		@Override public boolean apply(SourceFile input) {
			return input.region.equals(region);
		}
		@Override public String toString() { return region.toString(); }
	}

	private static class SourceFileName implements Predicate<SourceFile> {
		String name;
		SourceFileName(String name) { this.name = name; }
		@Override public boolean apply(SourceFile input) {
			return input.name.equals(name);
		}
		@Override public String toString() { return name; }
	}

	// @formatter:on

  SourceFile create(String resource, double weight) {
    List<String> parts = Lists.newArrayList(Parsing.split(resource, Delimiter.SLASH));
    SourceRegion region = SourceRegion.valueOf(parts.get(0));
    String typeFolder = parts.get(1);
    SourceType type = null;
    if (region == CASC) {
      type = INTERFACE;
    } else if (resource.contains("cluster") || resource.contains("_clu")) {
      type = CLUSTER;
    } else if (typeFolder.equals("gridded")) {
      type = resource.contains("deep") ? SLAB : GRID;
    } else {
      type = FAULT;
    }
    // move CA and CASC inputs to WUS, 2008 and 2014
    if (region == CASC) region = WUS;
    if (region == CA) region = WUS;

    URL url = null;
    try {
      url = new File(path() + resource).toURI().toURL();
    } catch (MalformedURLException mue) {
      mue.printStackTrace();
    }
    return new SourceFile(url, region, type, weight);

  }

  /* Return the path to the root of the source file directory structure */
  abstract String path();

  /* Implementations should poluate file list here */
  abstract void init();

  /* implementations return weights used to combine cluster sources */
  abstract double getClusterWeight(String name, int group);

}
