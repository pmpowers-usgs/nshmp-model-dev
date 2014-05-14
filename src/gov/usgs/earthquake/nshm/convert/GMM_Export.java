package gov.usgs.earthquake.nshm.convert;

import static gov.usgs.earthquake.nshm.util.SourceRegion.*;
import static org.opensha.eq.forecast.SourceType.*;
import static org.opensha.gmm.GMM.*;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.opensha.eq.forecast.SourceType;
import org.opensha.gmm.GMM;

import gov.usgs.earthquake.nshm.util.SourceRegion;

import com.google.common.collect.ArrayTable;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public class GMM_Export {

	// distance beyond which gmmMapAlt will be used in claculations
	private static final double R_CUT = 500.0;
	
	// need to break out CEUS mb converted grids as their GMMs would effectively
	// override the CEUS grid GMM's in the t08 table below
	private Map<GMM, Double> ceusGridMap08_J;
	private Map<GMM, Double> ceusGridMap08_AB;
	private Table<SourceRegion, SourceType, Map<GMM, Double>> gmmTable08;
	private Table<SourceRegion, SourceType, Map<GMM, Double>> gmmTable14;
	private Map<GMM, Double> ceusGridMap14_rCut;
	
	public static void createFile() {
		
	}
	
	private void initMaps() {
		
		gmmTable08 = createTable();
		gmmTable14 = createTable();
		gmmTable14 = createTable();
		
		
		Map<GMM, Double> wusFaultMap08 = Maps.newEnumMap(GMM.class);
		wusFaultMap08.put(BA_08,     0.3333);
		wusFaultMap08.put(CB_08,     0.3333);
		wusFaultMap08.put(CY_08,     0.3334);
		gmmTable08.put(WUS, FAULT, wusFaultMap08);

		Map<GMM, Double> wusFaultMap14 = Maps.newEnumMap(GMM.class);
		wusFaultMap14.put(ASK_14,    0.22);
		wusFaultMap14.put(BSSA_14,   0.22);
		wusFaultMap14.put(CB_14,     0.22);
		wusFaultMap14.put(CY_14,     0.22);
		wusFaultMap14.put(IDRISS_14, 0.12);
		gmmTable14.put(WUS, FAULT, wusFaultMap14);
		
		
		
		Map<GMM, Double> wusInterfaceMap08 = Maps.newEnumMap(GMM.class);
		wusInterfaceMap08.put(AB_03_GLOB_INTER,  0.25);
		wusInterfaceMap08.put(YOUNGS_97_INTER,   0.25);
		wusInterfaceMap08.put(ZHAO_06_INTER,     0.50);
		gmmTable08.put(WUS, INTERFACE, wusInterfaceMap08);

		Map<GMM, Double> wusInterfaceMap14 = Maps.newEnumMap(GMM.class);
		wusInterfaceMap14.put(AB_03_GLOB_INTER,  0.10);
		wusInterfaceMap14.put(AM_09_INTER,       0.30);
		wusInterfaceMap14.put(BCHYDRO_12_INTER,  0.30);
		wusInterfaceMap14.put(ZHAO_06_INTER,     0.30);
		gmmTable14.put(WUS, INTERFACE, wusInterfaceMap14);
		
		

		Map<GMM, Double> wusSlabMap08 = Maps.newEnumMap(GMM.class);
		wusSlabMap08.put(AB_03_CASC_SLAB,  0.25);
		wusSlabMap08.put(AB_03_GLOB_SLAB,  0.25);
		wusSlabMap08.put(YOUNGS_97_SLAB,   0.50);
		gmmTable08.put(WUS, SLAB, wusSlabMap08);

		Map<GMM, Double> wusSlabMap14 = Maps.newEnumMap(GMM.class);
		wusSlabMap14.put(AB_03_CASC_SLAB,  0.1665);
		wusSlabMap14.put(AB_03_GLOB_SLAB,  0.1665);
		wusSlabMap14.put(BCHYDRO_12_SLAB,  0.3330);
		wusSlabMap14.put(ZHAO_06_SLAB,     0.3340);
		gmmTable14.put(WUS, SLAB, wusSlabMap14);
		
		
		
		Map<GMM, Double> ceusFaultMap08 = Maps.newEnumMap(GMM.class);
		ceusFaultMap08.put(AB_06_140BAR,      0.1);
		ceusFaultMap08.put(AB_06_200BAR,      0.1);
		ceusFaultMap08.put(CAMPBELL_03,       0.1);
		ceusFaultMap08.put(FRANKEL_96,        0.1);
		ceusFaultMap08.put(SILVA_02,          0.1);
		ceusFaultMap08.put(SOMERVILLE_01,     0.2);
		ceusFaultMap08.put(TORO_97_MW,        0.2);
		ceusFaultMap08.put(TP_05,             0.1);
		gmmTable08.put(CEUS, FAULT, ceusFaultMap08);
		

		Map<GMM, Double> ceusFaultMap14 = Maps.newEnumMap(GMM.class);
		ceusFaultMap14.put(AB_06_PRIME,       0.22);
		ceusFaultMap14.put(ATKINSON_08_PRIME, 0.08);
		ceusFaultMap14.put(CAMPBELL_03,       0.11);
		ceusFaultMap14.put(FRANKEL_96,        0.06);
		ceusFaultMap14.put(SILVA_02,          0.06);
		ceusFaultMap14.put(SOMERVILLE_01,     0.10);
		ceusFaultMap14.put(PEZESHK_11,        0.15);
		ceusFaultMap14.put(TORO_97_MW,        0.11);
		ceusFaultMap14.put(TP_05,             0.11);
		gmmTable14.put(CEUS, FAULT, ceusFaultMap14);
		
		
		// for non-mb, fixed strike sources and RLMEs
		Map<GMM, Double> ceusGridMap08 = ceusFaultMap08;
		gmmTable08.put(CEUS, GRID, ceusGridMap08);

		ceusGridMap08_J = Maps.newEnumMap(GMM.class);
		ceusGridMap08_J.put(AB_06_140BAR_J,       0.125);
		ceusGridMap08_J.put(AB_06_200BAR_J,       0.125);
		ceusGridMap08_J.put(CAMPBELL_03_J,        0.125);
		ceusGridMap08_J.put(FRANKEL_96_J,         0.125);
		ceusGridMap08_J.put(SILVA_02_J,           0.125);
		ceusGridMap08_J.put(TORO_97_MB,           0.250);
		ceusGridMap08_J.put(TP_05_J,              0.125);

		ceusGridMap08_AB = Maps.newEnumMap(GMM.class);
		ceusGridMap08_AB.put(AB_06_140BAR_AB,     0.125);
		ceusGridMap08_AB.put(AB_06_200BAR_AB,     0.125);
		ceusGridMap08_AB.put(CAMPBELL_03_AB,      0.125);
		ceusGridMap08_AB.put(FRANKEL_96_AB,       0.125);
		ceusGridMap08_AB.put(SILVA_02_AB,         0.125);
		ceusGridMap08_AB.put(TORO_97_MB,          0.250);
		ceusGridMap08_AB.put(TP_05_AB,            0.125);

		Map<GMM, Double> ceusGridMap14 = Maps.newEnumMap(GMM.class);
		ceusGridMap14.put(AB_06_PRIME,        0.25);
		ceusGridMap14.put(ATKINSON_08_PRIME,  0.08);
		ceusGridMap14.put(CAMPBELL_03,        0.13);
		ceusGridMap14.put(FRANKEL_96,         0.06);
		ceusGridMap14.put(SILVA_02,           0.06);
		ceusGridMap14.put(PEZESHK_11,         0.16);
		ceusGridMap14.put(TORO_97_MW,         0.13);
		ceusGridMap14.put(TP_05,              0.13);
		gmmTable14.put(CEUS, GRID, ceusGridMap14);
		
		
		// map for fault and grid is the same beyond 500km
		ceusGridMap14_rCut = Maps.newEnumMap(GMM.class);
		ceusGridMap14_rCut.put(AB_06_PRIME,       0.30);
		ceusGridMap14_rCut.put(CAMPBELL_03,       0.17);
		ceusGridMap14_rCut.put(FRANKEL_96,        0.16);
		ceusGridMap14_rCut.put(PEZESHK_11,        0.20);
		ceusGridMap14_rCut.put(TP_05,             0.17);

	}
	
	private static Table<SourceRegion, SourceType, Map<GMM, Double>> createTable() {
		return ArrayTable.create(
			EnumSet.allOf(SourceRegion.class),
			EnumSet.allOf(SourceType.class));
	}
}
