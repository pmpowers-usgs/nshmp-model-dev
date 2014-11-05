package gov.usgs.earthquake.nshm.convert;

import java.util.List;

import org.opensha.eq.Magnitudes;
import org.opensha.geo.GeoTools;
import org.opensha.geo.LocationList;
import org.opensha.mfd.Mfds;

import com.google.common.collect.Lists;

/*
 * Placeholder class for future model reduction (via source combination).
 * Contains some example calculations of how to derive eq rates from slip rates.
 *
 * @author Peter Powers
 */
@Deprecated
class ModelReducer {

	// GOBBLEDEEGOOK, but useful
	
	static void rateTest() {
		double vSlip = 0.3; // mm/yr
		double chM = 7.0; // Mw
		double chRate = 7.7311764E-4;
		double dip = 50.0; // deg
		double W = 19.58112; // km
		double L = 119.239044; // km
		double mu = 3e10; // shear modulus
		double b = 0.8;
		
		double grRate = 2.0502302;
		double minM = 6.5;
		double mMax = 7.0;
		double dMag = 0.125;
		int nMag = 4;
		
		double fSlip = 0.3 / Math.sin(dip * GeoTools.TO_RAD);
		
		double Mo = Magnitudes.magToMoment_N_m(chM); // ch M moment
		double moRate = mu * fSlip * W * L * 1000.0; // 1K scale from mm to m and km to m conversions
		
		double chRateCalc = moRate / Mo;
		System.out.println(chRateCalc);
		double vRateCalc = chRate * Magnitudes.magToMoment_N_m(chM) * Math.sin(dip * GeoTools.TO_RAD) / (mu * W * L) / 1000.0;
		System.out.println(vRateCalc);
		
		List<Double> mags = Lists.newArrayList();
		for (int i=0; i<nMag; i++) {
			mags.add(minM + dMag/2.0 + i * dMag);
		}
//		System.out.println(mags);

		double toMo = 0.0;
		for (double mag : mags) {
			toMo += Magnitudes.magToMoment_N_m(mag) * Mfds.grRate(0, b, mag);
		}
		double grRateCalc = Math.log10(moRate / toMo);
		System.out.println(grRateCalc);
		
		double aValGR = 2.0502302;
		
		toMo = 0.0;
		for (double mag : mags) {
			toMo += Magnitudes.magToMoment_N_m(mag) * Mfds.grRate(grRate, b, mag);
		}
		toMo = toMo / Magnitudes.magToMoment_N_m(mMax);
		System.out.println(toMo);
		
		System.out.println(slipFromCH(chM, chRate, 50.0, W, L));
		System.out.println(slipFromGR(minM, mMax, dMag, grRate, b, 50.0, W, L));
//		double W2 = 15.0 / Math.sin(dip * GeoTools.TO_RAD);
//		System.out.println(W2);
		
//		// Dixie Valley
//		LocationList dixie = dixie();
////		System.out.println(dixie);
//		
//		FaultTrace trace = FaultTrace.create("Dixie Valley", dixie);
//		double len = trace.length();
//		System.out.println(len);
	}
	
	/*
	 * NSHMP input files typically present all information  necessary to build a
	 * rupture (dip, width, trace, eq. rate...). This requires replication of data
	 * across multiple mag scaling relationships and dip variations.
	 * 
	 * The methods below derive the original fault slip rate from the information
	 * in the NSHMP inpout files for CH and GR style sources.
	 * 
	 * All data in the input files can be derived from the fault trace, dip, a
	 * mag-area or mag-length relation
	 * 
	 * Need to be aware of those faults that have fixed dips and no variants
	 * 		-- Borah Peak
	 * 		-- Others?
	 */
	
	static final double ELASTIC_MODULUS = 3e10; //in N-m
	
	static double slipFromCH(double M, double rate, double dip, double W, double L) {
		return rate * Magnitudes.magToMoment_N_m(M) * Math.sin(dip * GeoTools.TO_RAD) / (ELASTIC_MODULUS * W * L) / 1000.0;
	}
	
	// this was built using values from the NSHMP config files; vMFD values in XML files
	// have already been adjusted, e.g. bin centered 5.0 --> 5.05
	static double slipFromGR(double mMin, double mMax, double dMag, double a, double b, double dip, double W, double L) {
		double totMo = 0.0;
		for (double mag = mMin + dMag/2.0; mag <= mMax; mag+=dMag) {
			totMo += Magnitudes.magToMoment_N_m(mag) * Mfds.grRate(a, b, mag);
		}
		double chRate = totMo / Magnitudes.magToMoment_N_m(mMax);
		return slipFromCH(mMax, chRate, dip, W, L);
	}
	
	static LocationList dixie() {
		String locDat = " -118.27796,39.36089,0.0 -118.25793,39.37185,0.0 -118.23511,39.39781,0.0 -118.22711,39.42710,0.0 -118.20995,39.44749,0.0 -118.20780,39.47354,0.0 -118.20178,39.47983,0.0 -118.20124,39.53499,0.0 -118.20798,39.54326,0.0 -118.20267,39.54676,0.0 -118.20412,39.55485,0.0 -118.19765,39.56060,0.0 -118.19729,39.57398,0.0 -118.18354,39.59285,0.0 -118.18183,39.60803,0.0 -118.17195,39.61854,0.0 -118.17626,39.64001,0.0 -118.18552,39.65717,0.0 -118.18444,39.66517,0.0 -118.19998,39.68691,0.0 -118.20151,39.72078,0.0 -118.19046,39.73354,0.0 -118.16548,39.73129,0.0 -118.15479,39.73677,0.0 -118.14410,39.73632,0.0 -118.11419,39.75582,0.0 -118.09820,39.77837,0.0 -118.10017,39.78223,0.0 -118.08705,39.79184,0.0 -118.07277,39.81179,0.0 -118.04636,39.81987,0.0 -118.03036,39.82940,0.0 -118.02767,39.84009,0.0 -118.01500,39.85329,0.0 -118.01635,39.87387,0.0 -118.02363,39.88483,0.0 -118.01392,39.89669,0.0 -117.98706,39.91016,0.0 -117.97395,39.92885,0.0 -117.96029,39.93999,0.0 -117.94053,39.94861,0.0 -117.89139,39.96065,0.0 -117.83371,40.01141,0.0 -117.81942,40.04043,0.0 -117.80029,40.06594,0.0 -117.78133,40.14850,0.0 -117.77244,40.16836,0.0 -117.74055,40.20789,0.0 -117.73615,40.22684,0.0 ";
		return LocationList.fromString(locDat);
	}
	

}
