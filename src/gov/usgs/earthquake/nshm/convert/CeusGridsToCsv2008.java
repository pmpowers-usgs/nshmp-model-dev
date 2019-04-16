package gov.usgs.earthquake.nshm.convert;

import static com.google.common.base.Preconditions.checkArgument;
import static gov.usgs.earthquake.nshm.util.SourceRegion.CEUS;
import static gov.usgs.earthquake.nshmp.eq.model.SourceType.GRID;
import static gov.usgs.earthquake.nshmp.eq.model.SourceType.SLAB;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.StandardSystemProperty;

import gov.usgs.earthquake.nshm.util.Utils;

/*
 * Developer notes:
 * 
 * This class leverages past fortran input --> XML conversion classes to
 * generate csv source data for nshmp-haz-2 (json model format)
 * 
 * Prior representations, derived from fortran approach, created an MFD at each
 * grid node and then applied weights (scaling) to reflect the mMax logic tree.
 * This necessitated storing the incremental mfds as explicit M-rate pairs.
 * 
 * 
 *
 */
public class CeusGridsToCsv2008 {

  private static final SimpleDateFormat sdf = new SimpleDateFormat("[yy-MM-dd-HH-mm]");
  private static final String S = StandardSystemProperty.FILE_SEPARATOR.value();
  private static final String FCAST_DIR = ".." + S + "hazard-models" + S + "US" + S;
  private static final String LOG_DIR = FCAST_DIR + "logs" + S;
  private static final Level LEVEL = Level.INFO;

  public static void main(String[] args) {
    convert2008grids();
  }

  static void convert2008grids() {
    String logID = Converter.class.getName() + "-2008-" + sdf.format(new Date());
    String logPath = LOG_DIR + logID + ".log";
    Logger log = Utils.logger(logID, logPath, LEVEL);
    List<SourceFile> files = SourceManager_2008.instance().get(CEUS, GRID);
    Converter.convertGrid(files, "2008", log);

  }

}
