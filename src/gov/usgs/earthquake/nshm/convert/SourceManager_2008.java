package gov.usgs.earthquake.nshm.convert;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

/*
 * Container class that manages access to 2008 NSHMP source files. Source
 * weights are hard coded in this class and supplied to each SourceFile
 * generated. This class also provides the weights used for NMSZ fault models,
 * which can be retreived by group index [1,2,3,4,5] and recurrence
 * [500,750,1000,1500].
 * 
 * @author Peter Powers
 */
class SourceManager_2008 extends SourceManager {
	
	private static final String PATH = "/Users/pmpowers/projects/svn/NSHMP08/conf/";
	private static SourceManager_2008 mgr;
	
	static SourceManager_2008 instance() {
		if (mgr == null) {
			mgr = new SourceManager_2008();
		}
		return mgr;
	}

	@Override
	String path() {
		return PATH;
	}
	
	@Override
	void init() {
		
		// CA faults
		files.add(create("CA/faults/bFault.ch.in", 0.6667));
		files.add(create("CA/faults/bFault.gr.in", 0.1667));
		files.add(create("CA/faults/aFault_aPriori_D2.1.in", 0.4500));
		files.add(create("CA/faults/aFault_unseg.in", 0.0500));
		files.add(create("CA/faults/aFault_MoBal.in", 0.2250));

		// CA grid and fixed strike
		files.add(create("CA/gridded/CAdeep.in", 1.0));
		files.add(create("CA/gridded/CAmap.24.ch.in", 0.3333));
		files.add(create("CA/gridded/CAmap.21.ch.in", 0.3334));
		files.add(create("CA/gridded/CAmap.21.gr.in", 0.1666));
		files.add(create("CA/gridded/CAmap.24.gr.in", 0.1667));
		files.add(create("CA/gridded/brawmap.in", 1.0));
		files.add(create("CA/gridded/creepmap.in", 1.0));
		files.add(create("CA/gridded/sangorg.in", 1.0));
		files.add(create("CA/gridded/mendo.in", 1.0));
		files.add(create("CA/gridded/mojave.in", 1.0));
		files.add(create("CA/gridded/impext.ch.in", 0.6667));
		files.add(create("CA/gridded/impext.gr.in", 0.3333));
		files.add(create("CA/gridded/shear1.in", 1.0));
		files.add(create("CA/gridded/shear2.in", 1.0));
		files.add(create("CA/gridded/shear3.in", 1.0));
		files.add(create("CA/gridded/shear4.in", 1.0));
		
		// WUS faults
		files.add(create("WUS/faults/brange.3dip.ch.in", 0.6666));
		files.add(create("WUS/faults/brange.3dip.gr.in", 0.3334));
		files.add(create("WUS/faults/brange.3dip.65.in", 1.0000));
		files.add(create("WUS/faults/nv.3dip.ch.in", 0.6666));
		files.add(create("WUS/faults/nv.3dip.gr.in", 0.3334));
		files.add(create("WUS/faults/nvut.3dip.65.in", 1.0000));
		files.add(create("WUS/faults/ut.3dip.ch.in", 0.6666));
		files.add(create("WUS/faults/ut.3dip.gr.in", 0.3334));
		files.add(create("WUS/faults/orwa_c.in", 0.5000));
		files.add(create("WUS/faults/orwa_n.3dip.ch.in", 0.6667));
		files.add(create("WUS/faults/orwa_n.3dip.gr.in", 0.3333));
		files.add(create("WUS/faults/wasatch.3dip.ch.in", 1.0000));
		files.add(create("WUS/faults/wasatch.3dip.gr.in", 1.0000));
		files.add(create("WUS/faults/wasatch.3dip.74.in", 1.0000));

		// WUS grid and fixed strike
		files.add(create("WUS/gridded/WUSmap.ch.in", 0.25));
		files.add(create("WUS/gridded/WUSmap.gr.in", 0.25));
		files.add(create("WUS/gridded/EXTmap.ch.in", 0.6667));
		files.add(create("WUS/gridded/EXTmap.gr.in", 0.333));
		files.add(create("WUS/gridded/nopuget.ch.in", 0.25));
		files.add(create("WUS/gridded/nopuget.gr.in", 0.25));
		files.add(create("WUS/gridded/puget.ch.in", 0.25));
		files.add(create("WUS/gridded/puget.gr.in", 0.25));
		files.add(create("WUS/gridded/pnwdeep.in", 1.0));
		files.add(create("WUS/gridded/portdeep.in", 1.0));

		// CASC
		files.add(create("CASC/cascadia.top.8387.in", 0.0051282));
		files.add(create("CASC/cascadia.mid.8387.in", 0.0102564));
		files.add(create("CASC/cascadia.bot.8387.in", 0.0102564));
		files.add(create("CASC/cascadia.older2.8387.in", 0.025644));
		files.add(create("CASC/cascadia.top.8082.in", 0.0025641));
		files.add(create("CASC/cascadia.mid.8082.in", 0.0051282));
		files.add(create("CASC/cascadia.bot.8082.in", 0.0051282));
		files.add(create("CASC/cascadia.older2.8082.in", 0.012833));
		files.add(create("CASC/cascadia.top.9pm.in", 0.06666667));
		files.add(create("CASC/cascadia.mid.9pm.in", 0.13333));
		files.add(create("CASC/cascadia.bot.9pm.in", 0.13333));
		files.add(create("CASC/cascadia.older2.9pm.in", 0.3333));

		// CEUS faults
		files.add(create("CEUS/faults/NMSZnocl.500yr.5branch.in", 0.45));
		files.add(create("CEUS/faults/NMSZnocl.1000yr.5branch.in", 0.05));
		files.add(create("CEUS/faults/CEUScm.in", 1.0));

		// CEUS gridded
		files.add(create("CEUS/gridded/CEUS.2007all8.AB.in", 0.5));
		files.add(create("CEUS/gridded/CEUS.2007all8.J.in", 0.5));
		files.add(create("CEUS/gridded/CEUSchar.73.in", 0.45));
		files.add(create("CEUS/gridded/CEUSchar.71.in", 0.2));
		files.add(create("CEUS/gridded/CEUSchar.75.in", 0.15));
		files.add(create("CEUS/gridded/CEUSchar.68.in", 0.2));

		// CLUSTER NOTE: the NNMSZ cluster model is the one case in the 2008
		// NSHMP where there is not a 1:1 correspondence between input and
		// output files (there are actually 5 output files per input
		// representing the different fault geometry models). As such we supply
		// the weights to the ClusterSource and have the NSHMP hazard calcuator
		// apply them appropriately; see getClusterWeight(). The inputs are
		// therefore given full weight.
		files.add(create("CEUS/faults/newmad.500.cluster.in", 1.0));
		files.add(create("CEUS/faults/newmad.750.cluster.in", 1.0));
		files.add(create("CEUS/faults/newmad.1000.cluster.in", 1.0));
		files.add(create("CEUS/faults/newmad.1500.cluster.in", 1.0));
		
		// Original cluster weights from NSHMP combine phase
		// newmad-500-clu.pga.g1 0.01125
		// newmad-500-clu.pga.g2 0.0225
		// newmad-500-clu.pga.g3 0.1575
		// newmad-500-clu.pga.g4 0.0225
		// newmad-500-clu.pga.g5 0.01125
		
		// newmad-750-clu.pga.g1 0.01125
		// newmad-750-clu.pga.g2 0.0225
		// newmad-750-clu.pga.g3 0.1575
		// newmad-750-clu.pga.g4 0.0225
		// newmad-750-clu.pga.g5 0.01125
		
		// newmad-1000-clu.pga.g1 0.0025
		// newmad-1000-clu.pga.g2 0.005
		// newmad-1000-clu.pga.g3 0.035
		// newmad-1000-clu.pga.g4 0.005
		// newmad-1000-clu.pga.g5 0.0025
		
		// newmad-1500-clu.pga.g1 0.01125
		// newmad-1500-clu.pga.g2 0.0225
		// newmad-1500-clu.pga.g3 0.1575
		// newmad-1500-clu.pga.g4 0.0225
		// newmad-1500-clu.pga.g5 0.01125
		
	}
	
	// These are combined the weights of: (un)clustered * location * recurrence
	// e.g. 0.5 * 0.05 * 0.9 * 0.05 = 0.01125
	private static final double[] CL_WTS_BASE = { 0.01125, 0.0225, 0.1575, 0.0225, 0.01125 };
	private static final double[] CL_WTS_1000 = { 0.0025, 0.005, 0.035, 0.005, 0.0025 };

	/*
	 * Returns the weight for the supplied NMSZ cluster model file name and
	 * fault model group index (1-5, west to east)
	 */
	public double getClusterWeight(String name, int group) {
		double[] wts = (name.contains(".1000.")) ? CL_WTS_1000 : CL_WTS_BASE;
		return wts[group-1];
	}

	
	public static URL getCEUSmask(String name) {
		checkArgument(name.equals("craton") || name.equals("margin"));
		URL url = null;
		try {
			url = new File(PATH + "../scripts/GR/" + name).toURI().toURL();
		} catch (MalformedURLException mue) {
			mue.printStackTrace();
		}
		return url;
	}

}
