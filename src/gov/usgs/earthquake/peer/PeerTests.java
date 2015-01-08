package gov.usgs.earthquake.peer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import gov.usgs.earthquake.nshm.convert.CH_Data;
import gov.usgs.earthquake.nshm.convert.GR_Data;

import org.opensha.data.XY_Sequence;
import org.opensha.eq.Magnitudes;

import static org.opensha.geo.GeoTools.TO_RAD;

import org.opensha.geo.Location;
import org.opensha.geo.LocationList;
import org.opensha.gmm.Gmm;
import org.opensha.mfd.GaussianMfd;
import org.opensha.mfd.GutenbergRichterMfd;
import org.opensha.mfd.IncrementalMfd;
import org.opensha.mfd.Mfds;
import org.opensha.mfd.YC_1985_CharMfd;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;

/**
 * Add comments here
 * 
 * @author Peter Powers
 */
public class PeerTests {

	static final double[] IMLS;
	
	static final LocationList FAULT_SITES;
	static final LocationList FAULT_SOURCE_TRACE;
	static final double FAULT1_ZTOP = 0.0; // km
	static final double FAULT1_ZBOT = 12.0; // km
	static final double FAULT1_WIDTH = FAULT1_ZBOT - FAULT1_ZTOP; // km
	static final double FAULT1_LENGTH = 25.0; // km
	static final double FAULT1_DIP = 90.0;
	static final double FAULT1_RAKE = 0.0;
	
	static final double FAULT2_ZTOP = 1.0; // km
	static final double FAULT2_ZBOT = 12.0; // km
	static final double FAULT2_DIP = 60.0; // degrees
	static final double FAULT2_WIDTH = (FAULT2_ZBOT - FAULT2_ZTOP) / Math.sin(FAULT2_DIP * TO_RAD);
	static final double FAULT2_LENGTH = FAULT1_LENGTH;
	static final double FAULT2_RAKE = 90.0;

	static final String AREA_DEPTH_STR;
	static final String AREA_DEPTH_VAR_STR;
	static final LocationList AREA_SITES;
	static final LocationList AREA_SOURCE_BORDER;
	
	static final double B_VAL = 0.9;
	static final double SINGLE_M1 = 6.5;
	static final double SINGLE_M2 = 6.0;
	static final double GR_MIN = 5.0;
	static final double GR_MAX = 6.5;
	static final double YC_MAX = 6.45;
	static final double YC_MAX_BIN = 6.445;
	static final double GAUSS_MEAN = 6.2;
	static final double GAUSS_SIGMA = 0.25;
	static final double SLIP_RATE = 2.0; // mm/yr
	static final double AREA_EVENT_RATE = 0.0395;
	
//	static final CH_Data F1_SINGLE_6P5_MFD;
//	static final CH_Data F1_SINGLE_6P0_FLOAT_MFD;
//	static final CH_Data F2_SINGLE_6P0_FLOAT_MFD;
//	static final GR_Data F1_GR_FLOAT_MFD;
	
	static final IncrementalMfd F1_SINGLE_6P5_MFD;			// case 1
	static final IncrementalMfd F1_SINGLE_6P0_FLOAT_MFD;	// case 2,3,8a,8b,8c
	static final IncrementalMfd F2_SINGLE_6P0_FLOAT_MFD;	// case 4
	static final IncrementalMfd F1_GR_FLOAT_MFD;			// case 5
	static final IncrementalMfd F1_GAUSS_FLOAT_MFD;			// case 6
	static final IncrementalMfd F1_YC_CHAR_FLOAT_MFD;		// case 7
	static final IncrementalMfd A1_GR_MFD;					// case 10,11
	
	static final List<Double> GMM_CUTOFFS = Lists.newArrayList(200.0);
	static final Map<Gmm, Double> GMM_WT_MAP = ImmutableMap.of(Gmm.SADIGH_97, 1.0);
	static final List<Map<Gmm, Double>> GMM_MAP_LIST = Lists.newArrayList(GMM_WT_MAP);
	
	public static void main(String[] args) {
//		System.out.println(F1_SINGLE_6P5_MFD);
//		System.out.println(F1_SINGLE_6P0_FLOAT_MFD);
//		System.out.println(F2_SINGLE_6P0_FLOAT_MFD);
//		System.out.println(F1_GR_FLOAT_MFD);
		System.out.println(F1_GAUSS_FLOAT_MFD);
//		System.out.println(F1_YC_CHAR_FLOAT_MFD);
//		System.out.println(A1_GR_MFD);
		
//		System.out.println(F1_TMR);
//		System.out.println(F2_TMR);
	}

	static {
		double f1area = FAULT1_LENGTH * 1000.0 * FAULT1_WIDTH * 1000.0;
		double f2area = FAULT2_LENGTH * 1000.0 * FAULT2_WIDTH * 1000.0;
		
		double f1tmr = Magnitudes.moment(f1area, SLIP_RATE / 1000.0);
		double f2tmr = Magnitudes.moment(f2area, SLIP_RATE / 1000.0);
		
		F1_SINGLE_6P5_MFD = Mfds.newSingleMoBalancedMFD(SINGLE_M1, f1tmr, false);
		F1_SINGLE_6P0_FLOAT_MFD = Mfds.newSingleMoBalancedMFD(SINGLE_M2, f1tmr, true);
		F2_SINGLE_6P0_FLOAT_MFD = Mfds.newSingleMoBalancedMFD(SINGLE_M2, f2tmr, true);

		IncrementalMfd gmMfd = Mfds.newGutenbergRichterMoBalancedMFD(0.005, 0.01, 650, B_VAL, f1tmr);
		F1_GR_FLOAT_MFD = trimToRange(gmMfd, GR_MIN, GR_MAX);
		
		GaussianMfd gaussMfd = new GaussianMfd(0.005, 9.995, 1000);
		gaussMfd.setAllButCumRate(GAUSS_MEAN, GAUSS_SIGMA, f1tmr, 1.19, 1);
		F1_GAUSS_FLOAT_MFD = trimToRange(gaussMfd, GR_MIN, GR_MAX);
		
		YC_1985_CharMfd ycMfd = new YC_1985_CharMfd(0.005, 1001, 0.01, 0.005, YC_MAX_BIN, 0.49, 5.945, 1.0, B_VAL, f1tmr);
		F1_YC_CHAR_FLOAT_MFD = trimToRange(ycMfd, GR_MIN, YC_MAX);
		
		A1_GR_MFD = Mfds.newGutenbergRichterMFD(5.05, 0.1, 15, B_VAL, AREA_EVENT_RATE);
		
	}
	
	private static IncrementalMfd trimToRange(IncrementalMfd mfd, double mMin, double mMax) {
		List<Double> mags = new ArrayList<>();
		List<Double> rates = new ArrayList<>();
		for (int i=0; i<mfd.getNum(); i++) {
			double mag = mfd.getX(i);
			double rate = mfd.getY(i);
			if (mag > mMin && mag < mMax && rate > 0.0) {
				mags.add(mag);
				rates.add(rate);
			}
		}
		return Mfds.newIncrementalMFD(Doubles.toArray(mags), Doubles.toArray(rates));
	}
	
	static {
		// @formatter:off
		
		IMLS = new double[] { 0.001, 0.01, 0.05, 0.1, 0.15, 0.2, 0.25, 0.3, 0.35, 0.4, 0.45, 0.5, 0.55, 0.6, 0.7, 0.8, 0.9, 1.0 };
		
		FAULT_SITES = LocationList.create(
			Location.create(38.113, -122.000),
			Location.create(38.113, -122.114),
			Location.create(38.111, -122.570),
			Location.create(38.000, -122.000),
			Location.create(37.910, -122.000),
			Location.create(38.225, -122.000),
			Location.create(38.113, -121.886));

		// defined from north to south so that reverse representation
		// is east-vergent
		FAULT_SOURCE_TRACE = LocationList.create(
			Location.create(38.2248, -122.0000),
			Location.create(38.000, -122.000));
		
		AREA_DEPTH_STR = "[10.0::[5.0:1.0]]";
		
		AREA_DEPTH_VAR_STR = "[10.0::[5.0:0.1667, 6.0:0.1666, 7.0:0.1667, 8.0:0.1667, 9.0:0.1666, 10.0:0.1667]";
		
		AREA_SITES = LocationList.create(
			Location.create(38.000, -122.000),
			Location.create(37.550, -122.000),
			Location.create(37.099, -122.000),
			Location.create(36.874, -122.000));
		
		AREA_SOURCE_BORDER = LocationList.create(
			Location.create(38.901, -122.000),
			Location.create(38.899, -121.920),
			Location.create(38.892, -121.840),
			Location.create(38.881, -121.760),
			Location.create(38.866, -121.682),
			Location.create(38.846, -121.606),
			Location.create(38.822, -121.532),
			Location.create(38.794, -121.460),
			Location.create(38.762, -121.390),
			Location.create(38.727, -121.324),
			Location.create(38.688, -121.261),
			Location.create(38.645, -121.202),
			Location.create(38.600, -121.147),
			Location.create(38.551, -121.096),
			Location.create(38.500, -121.050),
			Location.create(38.446, -121.008),
			Location.create(38.390, -120.971),
			Location.create(38.333, -120.940),
			Location.create(38.273, -120.913),
			Location.create(38.213, -120.892),
			Location.create(38.151, -120.876),
			Location.create(38.089, -120.866),
			Location.create(38.026, -120.862),
			Location.create(37.963, -120.863),
			Location.create(37.900, -120.869),
			Location.create(37.838, -120.881),
			Location.create(37.777, -120.899),
			Location.create(37.717, -120.921),
			Location.create(37.658, -120.949),
			Location.create(37.601, -120.982),
			Location.create(37.545, -121.020),
			Location.create(37.492, -121.063),
			Location.create(37.442, -121.110),
			Location.create(37.394, -121.161),
			Location.create(37.349, -121.216),
			Location.create(37.308, -121.275),
			Location.create(37.269, -121.337),
			Location.create(37.234, -121.403),
			Location.create(37.203, -121.471),
			Location.create(37.176, -121.542),
			Location.create(37.153, -121.615),
			Location.create(37.133, -121.690),
			Location.create(37.118, -121.766),
			Location.create(37.108, -121.843),
			Location.create(37.101, -121.922),
			Location.create(37.099, -122.000),
			Location.create(37.101, -122.078),
			Location.create(37.108, -122.157),
			Location.create(37.118, -122.234),
			Location.create(37.133, -122.310),
			Location.create(37.153, -122.385),
			Location.create(37.176, -122.458),
			Location.create(37.203, -122.529),
			Location.create(37.234, -122.597),
			Location.create(37.269, -122.663),
			Location.create(37.308, -122.725),
			Location.create(37.349, -122.784),
			Location.create(37.394, -122.839),
			Location.create(37.442, -122.890),
			Location.create(37.492, -122.937),
			Location.create(37.545, -122.980),
			Location.create(37.601, -123.018),
			Location.create(37.658, -123.051),
			Location.create(37.717, -123.079),
			Location.create(37.777, -123.101),
			Location.create(37.838, -123.119),
			Location.create(37.900, -123.131),
			Location.create(37.963, -123.137),
			Location.create(38.026, -123.138),
			Location.create(38.089, -123.134),
			Location.create(38.151, -123.124),
			Location.create(38.213, -123.108),
			Location.create(38.273, -123.087),
			Location.create(38.333, -123.060),
			Location.create(38.390, -123.029),
			Location.create(38.446, -122.992),
			Location.create(38.500, -122.950),
			Location.create(38.551, -122.904),
			Location.create(38.600, -122.853),
			Location.create(38.645, -122.798),
			Location.create(38.688, -122.739),
			Location.create(38.727, -122.676),
			Location.create(38.762, -122.610),
			Location.create(38.794, -122.540),
			Location.create(38.822, -122.468),
			Location.create(38.846, -122.394),
			Location.create(38.866, -122.318),
			Location.create(38.881, -122.240),
			Location.create(38.892, -122.160),
			Location.create(38.899, -122.080));
	}

}
