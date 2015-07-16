package gov.usgs.earthquake.nshm.convert;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

/*
 * Container class that manages access to 2008 NSHMP source files. Source
 * weights are hard coded in this class and supplied to each SourceFile
 * generated.
 * 
 * NOTE: Due to complexities in both the fortran code and newer ground motion
 * models that further parameterize hanging wall effects, additional focal-mech
 * dependent input files were required in 2014. These are not required by the
 * updated NSHM codes and so a git branch was created to consolidate a number of
 * WUS grid files. Converseley, the 'deep' intraslab sources abuse the standard
 * depth distribution line in the fortran inputs by including longitudinal
 * depth-step indicators. These have been broken into independent depth-specific
 * sources.
 * 
 * @author Peter Powers
 */
class SourceManager_2014 extends SourceManager {

	private static final String PATH = "/Users/pmpowers/projects/git/nshmp-haz-fortran/conf/";
	private static SourceManager_2014 mgr;

	static SourceManager_2014 instance() {
		if (mgr == null) {
			mgr = new SourceManager_2014();
		}
		return mgr;
	}

	@Override String path() {
		return PATH;
	}

	@Override void init() {

		// @formatter:off
				
		// *******  WUS fault  *******

		// Bird gets 0.1 wt after combining
		files.add(create("WUS/faults/2014WUSbird.65.in",   0.1));     // 1.0
		files.add(create("WUS/faults/2014WUSbird.char.in", 0.0667));  // 0.667
		files.add(create("WUS/faults/2014WUSbird.gr.in",   0.0333));  // 0.333
				
		// Geo get 0.8 wt after combining
		files.add(create("WUS/faults/2014WUSgeo.65.in",    0.8));     // 1.0
		files.add(create("WUS/faults/2014WUSgeo.char.in",  0.5336));  // 0.667
		files.add(create("WUS/faults/2014WUSgeo.gr.in",    0.2664));  // 0.333
		// Zeng gets 0.1 wt after combining
		files.add(create("WUS/faults/2014WUSzeng.65.in",   0.1));     // 1.0
		files.add(create("WUS/faults/2014WUSzeng.char.in", 0.0667));  // 0.667
		files.add(create("WUS/faults/2014WUSzeng.gr.in",   0.0333));  // 0.333
		
		// Wasatch unclustered
		files.add(create("WUS/faults/wasatch_slc.noclu.in", 0.24));
		
		
		// *******  WUS cluster  *******

		// this picks up the 0.8 weight of the WUS geologic branch - Wasatch includes 3 models
		// that vary on dip; each gets 0.24 weight (another 0.24 comes from the unclustered model,
		// which varies slightly from the Wasatch SLC model in 2014WUSgeo.char.in)
		files.add(create("WUS/faults/wasatch_slc.cluster.in", 0.8));

		
		// *******  WUS grid  *******
		
		// For WUS grids:
		// 		60/40 fixed/adaptive smoothing kernels
		// 		90/10 standardMFD/truncatedM8
		// Therefore use:
		//		0.54 fixed standardMFD
		//		0.36 adapt standardMFD
		//		0.06 fixed truncatedM8
		//		0.04 adapt truncatedM8
		//
		// ss:n and ss:r is 1/3:2/3 of 0.333:0.667 due to implementation difficulties
		// wrt hangin wall terms, input files were further decomposed on the basis of
		// focal mechanism.
		//
		// EXTensional WUS is 1/3:2/3 gr:ch; other WUS grids are 50/50 gr:ch
		
		String path = "WUS/gridded/convert/";
//		// EXT - original files and weights
//		// 0.54 weight after combining
//		files.add(create("WUS/gridded/EXTmap_2014_fixSm_n.ch.in",   0.2403));   // 0.445
//		files.add(create("WUS/gridded/EXTmap_2014_fixSm_n.gr.in",   0.11988));  // 0.222
//		files.add(create("WUS/gridded/EXTmap_2014_fixSm_ss.ch.in",  0.11988));  // 0.222
//		files.add(create("WUS/gridded/EXTmap_2014_fixSm_ss.gr.in",  0.05994));  // 0.111
//		// 0.36 weight after combining
//		files.add(create("WUS/gridded/EXTmap_2014_adSm_n.ch.in",    0.1602));   // 0.445
//		files.add(create("WUS/gridded/EXTmap_2014_adSm_n.gr.in",    0.07992));  // 0.222
//		files.add(create("WUS/gridded/EXTmap_2014_adSm_ss.ch.in",   0.07992));  // 0.222
//		files.add(create("WUS/gridded/EXTmap_2014_adSm_ss.gr.in",   0.03996));  // 0.111
//		// 0.06 weight after combining
//		files.add(create("WUS/gridded/EXTmap_2014_fixSm_n_M8.in",   0.04002));  // 0.667
//		files.add(create("WUS/gridded/EXTmap_2014_fixSm_ss_M8.in",  0.01998));  // 0.333
//		// 0.04 weight after combining
//		files.add(create("WUS/gridded/EXTmap_2014_adSm_n_M8.in",    0.02668));  // 0.667
//		files.add(create("WUS/gridded/EXTmap_2014_adSm_ss_M8.in",   0.01332));  // 0.333

		// EXT - mech consolidation for conversion - needs 0.333:0.667 strike-slip:normal
		// see config-restructure git branch
		// 0.54 weight after combining
		files.add(create(path + "EXTmap_2014_fixSm.ch.in",     0.36018));  // 0.667
		files.add(create(path + "EXTmap_2014_fixSm.gr.in",     0.17982));  // 0.333
		// 0.36 weight after combining
		files.add(create(path + "EXTmap_2014_adSm.ch.in",      0.24012));  // 0.667
		files.add(create(path + "EXTmap_2014_adSm.gr.in",      0.11988));  // 0.333
		// 0.1 weight after combining
		files.add(create(path + "EXTmap_2014_fixSm_M8.in",     0.06));
		files.add(create(path + "EXTmap_2014_adSm_M8.in",      0.04));
		
//		// MAP - original files and weights
//		// 0.27 weight after combining
//		files.add(create("WUS/gridded/WUSmap_2014_fixSm_r.ch.in",   0.08991));  // 0.333
//		files.add(create("WUS/gridded/WUSmap_2014_fixSm_r.gr.in",   0.08991));  // 0.333
//		files.add(create("WUS/gridded/WUSmap_2014_fixSm_ss.ch.in",  0.04509));  // 0.167
//		files.add(create("WUS/gridded/WUSmap_2014_fixSm_ss.gr.in",  0.04509));  // 0.167
//		// 0.18 weight after combining
//		files.add(create("WUS/gridded/WUSmap_2014_adSm_r.ch.in",    0.05994));  // 0.333
//		files.add(create("WUS/gridded/WUSmap_2014_adSm_r.gr.in",    0.05994));  // 0.333
//		files.add(create("WUS/gridded/WUSmap_2014_adSm_ss.ch.in",   0.03006));  // 0.167
//		files.add(create("WUS/gridded/WUSmap_2014_adSm_ss.gr.in",   0.03006));  // 0.167
//		// 0.03 weight after combining
//		files.add(create("WUS/gridded/WUSmap_2014_fixSm_r_M8.in",   0.02001));  // 0.667
//		files.add(create("WUS/gridded/WUSmap_2014_fixSm_ss_M8.in",  0.00999));  // 0.333
//		// 0.02 weight after combining
//		files.add(create("WUS/gridded/WUSmap_2014_adSm_r_M8.in",    0.01334));  // 0.667
//		files.add(create("WUS/gridded/WUSmap_2014_adSm_ss_M8.in",   0.00666));  // 0.333

		// MAP - mech consolidation for conversion - needs 0.333:0.667 strike-slip:normal
		// 0.27 weight after combining
		files.add(create(path + "WUSmap_2014_fixSm.ch.in",     0.135));    // 0.5
		files.add(create(path + "WUSmap_2014_fixSm.gr.in",     0.135));    // 0.5
		// 0.18 weight after combining
		files.add(create(path + "WUSmap_2014_adSm.ch.in",      0.09));     // 0.5
		files.add(create(path + "WUSmap_2014_adSm.gr.in",      0.09));     // 0.5
		// 0.05 weight after combining
		files.add(create(path + "WUSmap_2014_fixSm_M8.in",     0.03));
		files.add(create(path + "WUSmap_2014_adSm_M8.in",      0.02));

//		// No Puget - original files and weights
//		// 0.27 weight after combining
//		files.add(create("WUS/gridded/noPuget_2014_fixSm_r.ch.in",  0.08991));  // 0.333
//		files.add(create("WUS/gridded/noPuget_2014_fixSm_r.gr.in",  0.08991));  // 0.333
//		files.add(create("WUS/gridded/noPuget_2014_fixSm_ss.ch.in", 0.04509));  // 0.167
//		files.add(create("WUS/gridded/noPuget_2014_fixSm_ss.gr.in", 0.04509));  // 0.167
//		// 0.18 weight after combining
//		files.add(create("WUS/gridded/noPuget_2014_adSm_r.ch.in",   0.05994));  // 0.333
//		files.add(create("WUS/gridded/noPuget_2014_adSm_r.gr.in",   0.05994));  // 0.333
//		files.add(create("WUS/gridded/noPuget_2014_adSm_ss.ch.in",  0.03006));  // 0.167
//		files.add(create("WUS/gridded/noPuget_2014_adSm_ss.gr.in",  0.03006));  // 0.167
//		// 0.03 weight after combining
//		files.add(create("WUS/gridded/noPuget_2014_fixSm_r_M8.in",  0.02001));  // 0.667
//		files.add(create("WUS/gridded/noPuget_2014_fixSm_ss_M8.in", 0.00999));  // 0.333
//		// 0.02 weight after combining
//		files.add(create("WUS/gridded/noPuget_2014_adSm_r_M8.in",   0.01334));  // 0.667
//		files.add(create("WUS/gridded/noPuget_2014_adSm_ss_M8.in",  0.00666));  // 0.333

		// No Puget - mech consolidation for conversion - needs 0.667:0.333 mechmap
		// 0.27 weight after combining
		files.add(create(path + "noPuget_2014_fixSm.ch.in",    0.135));    // 0.5
		files.add(create(path + "noPuget_2014_fixSm.gr.in",    0.135));    // 0.5
		// 0.18 weight after combining
		files.add(create(path + "noPuget_2014_adSm.ch.in",     0.09));     // 0.5
		files.add(create(path + "noPuget_2014_adSm.gr.in",     0.09));     // 0.5
		// 0.05 weight after combining
		files.add(create(path + "noPuget_2014_fixSm_M8.in",    0.03));
		files.add(create(path + "noPuget_2014_adSm_M8.in",     0.02));

//		// Puget - original files and weights
//		// 0.45 weight after combining
//		files.add(create("WUS/gridded/puget_2014_r.ch.in",          0.14985));  // 0.333
//		files.add(create("WUS/gridded/puget_2014_r.gr.in",          0.14985));  // 0.333
//		files.add(create("WUS/gridded/puget_2014_ss.ch.in",         0.07515));  // 0.167
//		files.add(create("WUS/gridded/puget_2014_ss.gr.in",         0.07515));  // 0.167
//		// 0.05 weight after combining
//		files.add(create("WUS/gridded/puget_2014_r_M8.in",          0.03335));  // 0.667
//		files.add(create("WUS/gridded/puget_2014_ss_M8.in",         0.01665));  // 0.333

		// Puget - mech consolidation for conversion - needs 0.333:0.667 strike-slip:normal
		// 0.45 weight after combining
		files.add(create(path + "puget_2014.ch.in",            0.225));    // 0.5
		files.add(create(path + "puget_2014.gr.in",            0.225));    // 0.5
		// 0.05 weight after combining
		files.add(create(path + "puget_2014_M8.in",            0.05));

		// WUS fixed strike grid holdovers from 2008 CA model
		files.add(create("WUS/gridded/shear2_2014.in", 1.0));
		files.add(create("WUS/gridded/shear3_2014.in", 1.0));
		files.add(create("WUS/gridded/shear4_2014.in", 1.0));

		// grid representation of faults that go with Peter Bird's deformation model
		files.add(create("WUS/gridded/WUS_zones_PB.in",     0.1));
		
		
		// *******  WUS intraslab  *******
		
		files.add(create("WUS/gridded/CAdeep.2014.in",           1.0));
		files.add(create("WUS/gridded/CAdeepMmax75.2014.in",     0.9));
		files.add(create("WUS/gridded/CAdeepMmax8.2014.in",      0.1));
		files.add(create("WUS/gridded/coastalOR_deep.in",        1.0));
		files.add(create("WUS/gridded/coastalOR_deep_Mmax75.in", 0.9));
		files.add(create("WUS/gridded/coastalOR_deep_Mmax8.in",  0.1));
		files.add(create("WUS/gridded/pacnwdeep.2014.in",        1.0));
		files.add(create("WUS/gridded/pacnwdeep_Mmax75.2014.in", 0.9));
		files.add(create("WUS/gridded/pacnwdeep_Mmax8.2014.in",  0.1));
		
		
		// *******  WUS interface  *******

		files.add(create("CASC/sub0_GRb0_bot.in", 0.0348));		// Mscaling GMPE in input, bot 0.3, b=0 0.5, float 0.5, full rup 0.2, scale to mean rate 1.2 & by length ratio 1.5445
		files.add(create("CASC/sub0_GRb0_mid.in", 0.0579));		// Mscaling GMPE in input, mid 0.5, b=0 0.5, float 0.5, full rup 0.2, scale to mean rate 1.2 & by length ratio 1.5445
		files.add(create("CASC/sub0_GRb0_top.in", 0.0232));		// Mscaling GMPE in input, top 0.2, b=0 0.5, float 0.5, full rup 0.2, scale to mean rate 1.2 & by length ratio 1.5445
		files.add(create("CASC/sub0_GRb1_bot.in", 0.0348));		// Mscaling GMPE in input, bot 0.3, b=1 0.5, float 0.5, full rup 0.2, scale to mean rate 1.2 & by length ratio 1.5445
		files.add(create("CASC/sub0_GRb1_mid.in", 0.0579));		// Mscaling GMPE in input, mid 0.5, b=1 0.5, float 0.5, full rup 0.2, scale to mean rate 1.2 & by length ratio 1.5445
		files.add(create("CASC/sub0_GRb1_top.in", 0.0232));		// Mscaling GMPE in input, top 0.2 ,b=1 0.5, float 0.5, full rup 0.2, scale to mean rate 1.2 & by length ratio 1.5445
		files.add(create("CASC/sub0_ch_bot.in",   0.3));		// bot 0.3, full CSZ characteristic, Mscaling GMPEs in input
		files.add(create("CASC/sub0_ch_mid.in",   0.5));		// mid 0.5, full CSZ characteristic, Mscaling GMPEs in input
		files.add(create("CASC/sub0_ch_top.in",   0.2));		// top 0.2, full CSZ characteristic, Mscaling GMPEs in input
		files.add(create("CASC/sub1_GRb0_bot.in", 0.0675));		// float 0.5, bot 0.3, b=0 0.5,southernsegs 0.8, Mscaling GMPE in input, scale to mean rate 1.2
		files.add(create("CASC/sub1_GRb0_mid.in", 0.1125));		// float 0.5, mid 0.5, b=0 0.5,southernsegs 0.8, Mscaling GMPE in input, scale to mean rate 1.2
		files.add(create("CASC/sub1_GRb0_top.in", 0.045));		// float 0.5, top 0.2, b=0 0.5,southernsegs 0.8, Mscaling GMPE in input, scale to mean rate 1.2
		files.add(create("CASC/sub1_GRb1_bot.in", 0.0675));		// float 0.5, bot 0.3, b=1 0.5,southernsegs 0.8, Mscaling GMPE in input, scale to mean rate 1.2
		files.add(create("CASC/sub1_GRb1_mid.in", 0.1125));		// float 0.5, mid 0.5, b=1 0.5,southernsegs 0.8, Mscaling GMPE in input, scale to mean rate 1.2
		files.add(create("CASC/sub1_GRb1_top.in", 0.045));		// float 0.5, top 0.2, b=1 0.5,southernsegs 0.8, Mscaling GMPE in input, scale to mean rate 1.2
		files.add(create("CASC/sub1_ch_bot.in",   0.18));		// ch 0.5, bot 0.3, scaling to mean rate 1.2
		files.add(create("CASC/sub1_ch_mid.in",   0.3));		// ch 0.5, mid 0.5, scaling to mean rate 1.2
		files.add(create("CASC/sub1_ch_top.in",   0.12));		// ch 0.5, top 0.2, scaling to mean rate 1.2
		files.add(create("CASC/sub2_ch_bot.in",   0.18));		// ch 0.5, bot 0.3, scaling to mean rate 1.2
		files.add(create("CASC/sub2_ch_mid.in",   0.3));		// ch 0.5, mid 0.5, scaling to mean rate 1.2
		files.add(create("CASC/sub2_ch_top.in",   0.12));		// ch 0.5, top 0.2, scaling to mean rate 1.2
		files.add(create("CASC/sub3_ch_bot.in",   0.18));		// ch 0.5, bot 0.3, scaling to mean rate 1.2
		files.add(create("CASC/sub3_ch_mid.in",   0.3));		// ch 0.5, mid 0.5, scaling to mean rate 1.2
		files.add(create("CASC/sub3_ch_top.in",   0.12));		// ch 0.5, top 0.2, scaling to mean rate 1.2
		files.add(create("CASC/sub4_ch_bot.in",   0.0375));		// sub4_ch_bot uses 1/1000 rate, ch 0.5, bot 0.3, add  northern (0.2)
		files.add(create("CASC/sub4_ch_mid.in",   0.0625));		// sub1_ch_bot uses 1/1000 rate, ch 0.5, mid 0.5, add  northern (0.2)
		files.add(create("CASC/sub4_ch_top.in",   0.025));		// sub1_ch_bot uses 1/1000 rate, ch 0.5, top 0.2, add  northern (0.2)

		
		// ****** CEUS faults *****
		
		files.add(create("CEUS/faults/NMSZnocl.500.2014.in",   0.09));     // 0.5 USGS * 0.2 nocl * 0.9 500yr
		files.add(create("CEUS/faults/NMSZnocl.1000.2014.in",  0.005));    // 0.5 USGS * 0.2 nocl * 0.05 1000yr
		files.add(create("CEUS/faults/NMSZnocl.50000.2014.in", 0.005));    // 0.5 USGS * 0.2 nocl * 0.05 50000yr
		files.add(create("CEUS/faults/CEUScm2014.in",          0.5));
		files.add(create("CEUS/faults/CEUScm-meers_2014.in",   0.5));
		files.add(create("CEUS/faults/CEUScm-recur_2014.in",   0.25));
		files.add(create("CEUS/faults/CEUScm-srchar_2014.in",  0.16675));
		files.add(create("CEUS/faults/CEUScm-srgr_2014.in",    0.08325));
		
		files.add(create("CEUS/faults/NMFS_RFT.RLME.in",       0.025));    

		
		// ******  CEUS cluster  ******
		files.add(create("CEUS/faults/NMFS_RLME_clu.in", 0.45));           // 0.5 SSC * 0.9 clust

		// collectively USGS NMSZ clustered model gets 0.4 weight (0.5 USGS * 0.8 clust)
		double nmClustWt = 0.4;
		files.add(create("CEUS/faults/newmad2014.500.cluster.in", nmClustWt));
		files.add(create("CEUS/faults/newmad2014.750.cluster.in", nmClustWt));
		files.add(create("CEUS/faults/newmad2014.1000.cluster.in", nmClustWt));
		files.add(create("CEUS/faults/newmad2014.1500.cluster.in", nmClustWt));
		files.add(create("CEUS/faults/newmad2014.50000.cluster.in", nmClustWt));
		
		
		// *******  CEUS grid  ******
		
		files.add(create("CEUS/gridded/CEUS_adaptGridded_2014_2zone.in", 0.2));
		files.add(create("CEUS/gridded/CEUS_adaptGridded_2014_4zone.in", 0.2));
		files.add(create("CEUS/gridded/CEUS_fixRGridded_2014_2zone.in", 0.3));
		files.add(create("CEUS/gridded/CEUS_fixRGridded_2014_4zone.in", 0.3));
		
		files.add(create("CEUS/gridded/CEUSchar_2014_l.ssc67.in", 0.05));
		files.add(create("CEUS/gridded/CEUSchar_2014_l.ssc69.in", 0.125));
		files.add(create("CEUS/gridded/CEUSchar_2014_l.ssc71.in", 0.15));
		files.add(create("CEUS/gridded/CEUSchar_2014_l.ssc73.in", 0.125));
		files.add(create("CEUS/gridded/CEUSchar_2014_l.ssc75.in", 0.05));
		files.add(create("CEUS/gridded/CEUSchar_2014_n.ssc67.in", 0.03));
		files.add(create("CEUS/gridded/CEUSchar_2014_n.ssc69.in", 0.075));
		files.add(create("CEUS/gridded/CEUSchar_2014_n.ssc71.in", 0.09));
		files.add(create("CEUS/gridded/CEUSchar_2014_n.ssc73.in", 0.075));
		files.add(create("CEUS/gridded/CEUSchar_2014_n.ssc75.in", 0.03));
		files.add(create("CEUS/gridded/CEUSchar_2014_r.ssc67.in", 0.02));
		files.add(create("CEUS/gridded/CEUSchar_2014_r.ssc69.in", 0.05));
		files.add(create("CEUS/gridded/CEUSchar_2014_r.ssc71.in", 0.06));
		files.add(create("CEUS/gridded/CEUSchar_2014_r.ssc73.in", 0.05));
		files.add(create("CEUS/gridded/CEUSchar_2014_r.ssc75.in", 0.02));

		files.add(create("CEUS/gridded/ERM-N_RLME_2014.670.in", 0.3));
		files.add(create("CEUS/gridded/ERM-N_RLME_2014.690.in", 0.3));
		files.add(create("CEUS/gridded/ERM-N_RLME_2014.710.in", 0.3));
		files.add(create("CEUS/gridded/ERM-N_RLME_2014.740.in", 0.1));

		files.add(create("CEUS/gridded/ERM-S1_RLME_2014.670.in", 0.09));
		files.add(create("CEUS/gridded/ERM-S1_RLME_2014.690.in", 0.12));
		files.add(create("CEUS/gridded/ERM-S1_RLME_2014.710.in", 0.12));
		files.add(create("CEUS/gridded/ERM-S1_RLME_2014.730.in", 0.12));
		files.add(create("CEUS/gridded/ERM-S1_RLME_2014.750.in", 0.12));
		files.add(create("CEUS/gridded/ERM-S1_RLME_2014.770.in", 0.03));
		
		files.add(create("CEUS/gridded/ERM-S2_RLME_2014.670.in", 0.06));
		files.add(create("CEUS/gridded/ERM-S2_RLME_2014.690.in", 0.08));
		files.add(create("CEUS/gridded/ERM-S2_RLME_2014.710.in", 0.08));
		files.add(create("CEUS/gridded/ERM-S2_RLME_2014.730.in", 0.08));
		files.add(create("CEUS/gridded/ERM-S2_RLME_2014.750.in", 0.08));
		files.add(create("CEUS/gridded/ERM-S2_RLME_2014.770.in", 0.02));

		files.add(create("CEUS/gridded/Chlvx_RLME_2014.675.in", 0.2));
		files.add(create("CEUS/gridded/Chlvx_RLME_2014.700.in", 0.5));
		files.add(create("CEUS/gridded/Chlvx_RLME_2014.725.in", 0.2));
		files.add(create("CEUS/gridded/Chlvx_RLME_2014.750.in", 0.1));

		// Marianna collectively get 0.5 weight once combined
		files.add(create("CEUS/gridded/Marianna_RLME_2014.670.in", 0.075)); // 0.15
		files.add(create("CEUS/gridded/Marianna_RLME_2014.690.in", 0.1));   // 0.2
		files.add(create("CEUS/gridded/Marianna_RLME_2014.710.in", 0.1));   // 0.2
		files.add(create("CEUS/gridded/Marianna_RLME_2014.730.in", 0.1));   // 0.2
		files.add(create("CEUS/gridded/Marianna_RLME_2014.750.in", 0.1));   // 0.2
		files.add(create("CEUS/gridded/Marianna_RLME_2014.770.in", 0.025)); // 0.05

		files.add(create("CEUS/gridded/Commerce_RLME_2014.670.in", 0.15));
		files.add(create("CEUS/gridded/Commerce_RLME_2014.690.in", 0.2));
		files.add(create("CEUS/gridded/Commerce_RLME_2014.710.in", 0.2));
		files.add(create("CEUS/gridded/Commerce_RLME_2014.730.in", 0.2));
		files.add(create("CEUS/gridded/Commerce_RLME_2014.750.in", 0.2));
		files.add(create("CEUS/gridded/Commerce_RLME_2014.770.in", 0.05));

		files.add(create("CEUS/gridded/Wabash_RLME_2014.675.in", 0.05));
		files.add(create("CEUS/gridded/Wabash_RLME_2014.700.in", 0.25));
		files.add(create("CEUS/gridded/Wabash_RLME_2014.725.in", 0.35));
		files.add(create("CEUS/gridded/Wabash_RLME_2014.750.in", 0.35));
		
	}
	
	// Cluster group weights:
	private static final double[] NMSZ_CL_WTS_BASE = { 0.0225, 0.045, 0.315 , 0.045, 0.0225 }; // 500, 750, 1500
	private static final double[] NMSZ_CL_WTS_1000 = { 0.0025, 0.005, 0.035, 0.005, 0.0025 }; // 1000, 50000
	private static final double[] NMFS_CL_WTS = { 0.294000, 0.126000, 0.054000, 0.126000, 0.196000, 0.084000, 0.084000, 0.036000 };
	private static final double[] WASATCH_WTS = { 0.24, 0.24, 0.24 };
	
	/*
	 * Returns the weight for the supplied NMSZ cluster model file name and
	 * fault model group index (1-5, west to east)
	 */
	@Override
	public double getClusterWeight(String name, int group) {
		double[] wts = null;
		if (name.contains(".1000.") || name.contains(".50000.")) {
			wts = NMSZ_CL_WTS_1000;
		} else if (name.equals("NMFS_RLME_clu.in")) {
			wts = NMFS_CL_WTS;
		} else if (name.equals("wasatch_slc.cluster.in")) {
			wts = WASATCH_WTS;
		} else {
			wts = NMSZ_CL_WTS_BASE;
		}
		return wts[group - 1];
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
